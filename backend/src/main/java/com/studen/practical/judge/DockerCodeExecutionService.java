package com.studen.practical.judge;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.studen.practical.CodingLanguage;
import com.studen.practical.execution.ExecutionProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Real {@link CodeExecutionService}: every {@link #compile}/{@link #run} call creates one
 * ephemeral, network-disabled, resource-limited, non-root container from the
 * {@code studen-code-runner} image (see {@code backend/docker/runner/}), waits for it with a hard
 * wall-clock deadline, reads results from the bind-mounted workspace, and always removes the
 * container afterward. Never executes student code inside this JVM process.
 *
 * <p>All in-container paths are fixed ({@code /workspace}, {@code /run}) -- a host path can never
 * appear in a compiler/runtime error message, satisfying the "never expose server paths" spec
 * requirement by construction rather than by scrubbing.
 *
 * <p>Active only when {@code app.execution.provider=docker} (local dev default) -- production
 * (Render, no Docker Engine reachable) sets {@code app.execution.provider=judge0} instead, which
 * selects {@link Judge0CodeExecutionService} here. The shared {@link com.github.dockerjava.api.
 * DockerClient} bean itself ({@link DockerClientFactory}) stays gated on {@code enabled} alone
 * (not provider) since {@link DockerSqlExecutionService} still needs it regardless of which CODING
 * provider is active -- SQL intentionally stays docker-only for now.
 */
@Service
@Conditional(DockerProviderCondition.class)
public class DockerCodeExecutionService implements CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DockerCodeExecutionService.class);

    private final DockerClient dockerClient;
    private final ExecutionProperties properties;

    public DockerCodeExecutionService(DockerClient dockerClient, ExecutionProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    @Override
    public boolean isAvailable() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.warn("Docker Engine unreachable: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public CompileOutcome compile(ExecutionRequest request) {
        long start = System.currentTimeMillis();
        try {
            writeSource(request);
            Files.createDirectories(request.workspaceDir().resolve("bin"));
            Files.createDirectories(request.workspaceDir().resolve("output"));

            // Same resource/security hardening as the run step -- a malicious source file can
            // attack the compiler itself (e.g. runaway template/macro expansion), not just the
            // compiled program, so the compile container gets no exemption.
            String containerId = createContainer(
                    List.of("compile", languageToken(request.language())),
                    List.of(new Bind(request.workspaceDir().toAbsolutePath().toString(),
                            new Volume("/workspace"), AccessMode.rw)),
                    this::hardenedHostConfig);
            try {
                dockerClient.startContainerCmd(containerId).exec();
                boolean finished = awaitCompletion(containerId, properties.getCompileTimeoutSeconds());
                long durationMs = System.currentTimeMillis() - start;
                if (!finished) {
                    return CompileOutcome.failure("Compilation timed out.", durationMs);
                }

                Path exitFile = request.workspaceDir().resolve("output/compile_exit");
                Path stderrFile = request.workspaceDir().resolve("output/compile_stderr");
                int exitCode = readExitCode(exitFile);
                String stderr = readCapped(stderrFile, properties.outputLimitBytes());
                return exitCode == 0 ? CompileOutcome.success(durationMs) : CompileOutcome.failure(stderr, durationMs);
            } finally {
                removeContainer(containerId);
            }
        } catch (Exception e) {
            log.error("Docker compile failed", e);
            return CompileOutcome.failure(null, System.currentTimeMillis() - start);
        }
    }

    @Override
    public RunOutcome run(ExecutionRequest request, String stdin) {
        try {
            Path runDir = request.workspaceDir().resolve("run");
            deleteRecursively(runDir);
            Files.createDirectories(runDir.resolve("input"));
            Files.createDirectories(runDir.resolve("output"));
            Files.writeString(runDir.resolve("input/stdin.txt"), stdin == null ? "" : stdin, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            List<Bind> binds = List.of(
                    new Bind(request.workspaceDir().toAbsolutePath().toString(), new Volume("/workspace"), AccessMode.ro),
                    new Bind(runDir.toAbsolutePath().toString(), new Volume("/run"), AccessMode.rw));

            String containerId = createContainer(List.of("run", languageToken(request.language())), binds,
                    this::hardenedHostConfig);

            long start = System.currentTimeMillis();
            try {
                dockerClient.startContainerCmd(containerId).exec();
                boolean finished = awaitCompletion(containerId, properties.getRunTimeoutSeconds());
                long durationMs = System.currentTimeMillis() - start;

                if (!finished) {
                    dockerClient.killContainerCmd(containerId).exec();
                    return new RunOutcome(RunStatus.TIMEOUT, null, null, durationMs);
                }

                boolean oomKilled = wasOomKilled(containerId);
                if (oomKilled) {
                    return new RunOutcome(RunStatus.MEMORY_LIMIT, null, null, durationMs);
                }

                Path stdoutFile = runDir.resolve("output/stdout");
                Path stderrFile = runDir.resolve("output/stderr");
                Path exitFile = runDir.resolve("output/exit_code");

                if (exceedsLimit(stdoutFile) || exceedsLimit(stderrFile)) {
                    return new RunOutcome(RunStatus.OUTPUT_LIMIT, null, null, durationMs);
                }

                int exitCode = readExitCode(exitFile);
                String stdout = readCapped(stdoutFile, properties.outputLimitBytes());
                String stderr = readCapped(stderrFile, properties.outputLimitBytes());
                RunStatus status = exitCode == 0 ? RunStatus.SUCCESS : RunStatus.RUNTIME_ERROR;
                return new RunOutcome(status, stdout, stderr, durationMs);
            } finally {
                removeContainer(containerId);
            }
        } catch (Exception e) {
            log.error("Docker run failed", e);
            return RunOutcome.systemError();
        }
    }

    // Applied to every container this service creates -- compile and run alike. A malicious
    // source file can attack the compiler itself (runaway template/macro expansion, a compiler
    // plugin trick) just as easily as the compiled program, so neither step gets an exemption.
    private HostConfig hardenedHostConfig(HostConfig hostConfig) {
        return hostConfig
                .withNetworkMode("none")
                .withMemory(properties.memoryLimitBytes())
                .withMemorySwap(properties.memoryLimitBytes())
                .withNanoCPUs((long) (properties.getCpuLimit() * 1_000_000_000L))
                .withPidsLimit((long) properties.getPidsLimit())
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/tmp", "size=64m,mode=1777"))
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"));
    }

    private String createContainer(List<String> cmd, List<Bind> binds, java.util.function.UnaryOperator<HostConfig> extraConfig) {
        HostConfig hostConfig = HostConfig.newHostConfig().withBinds(binds.toArray(new Bind[0]));
        if (extraConfig != null) {
            hostConfig = extraConfig.apply(hostConfig);
        }
        CreateContainerResponse response = dockerClient.createContainerCmd(properties.getRunnerImage())
                .withCmd(cmd)
                .withHostConfig(hostConfig)
                .withUser("10001:10001")
                .withNetworkDisabled(true)
                .exec();
        return response.getId();
    }

    private boolean awaitCompletion(String containerId, int timeoutSeconds) {
        try {
            dockerClient.waitContainerCmd(containerId)
                    .exec(new WaitContainerResultCallback())
                    .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean wasOomKilled(String containerId) {
        try {
            var state = dockerClient.inspectContainerCmd(containerId).exec().getState();
            return state != null && Boolean.TRUE.equals(state.getOOMKilled());
        } catch (Exception e) {
            return false;
        }
    }

    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException ignored) {
            // Already gone -- nothing to clean up.
        } catch (Exception e) {
            log.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    private void writeSource(ExecutionRequest request) throws IOException {
        Path srcDir = request.workspaceDir().resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve(sourceFileName(request.language())), request.sourceCode(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort -- the whole workspace dir is destroyed by the orchestrator anyway.
                }
            });
        }
    }

    private int readExitCode(Path file) {
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            return raw.isEmpty() ? -1 : Integer.parseInt(raw);
        } catch (Exception e) {
            return -1;
        }
    }

    private boolean exceedsLimit(Path file) throws IOException {
        return Files.exists(file) && Files.size(file) > properties.outputLimitBytes();
    }

    private String readCapped(Path file, long maxBytes) {
        try {
            if (!Files.exists(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            byte[] capped = bytes.length > maxBytes ? java.util.Arrays.copyOf(bytes, (int) maxBytes) : bytes;
            return new String(capped, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String sourceFileName(CodingLanguage language) {
        return switch (language) {
            case JAVA -> "Main.java";
            case PYTHON -> "main.py";
            case C -> "main.c";
            case CPP -> "main.cpp";
        };
    }

    private static String languageToken(CodingLanguage language) {
        return switch (language) {
            case JAVA -> "java";
            case PYTHON -> "python";
            case C -> "c";
            case CPP -> "cpp";
        };
    }
}
