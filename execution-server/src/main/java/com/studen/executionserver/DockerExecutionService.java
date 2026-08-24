package com.studen.executionserver;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The actual container orchestration -- ported from the main backend's
 * {@code DockerCodeExecutionService}, same hardening, same {@code studen-code-runner} image and
 * {@code compile <lang>}/{@code run <lang>} Cmd contract (see {@code backend/docker/runner/run.sh},
 * not duplicated here, just built once on whichever Docker host this app talks to). The one
 * structural difference: a workspace persists on disk across the {@link #compile} call and every
 * subsequent {@link #run} call for the same {@code executionId} (instead of one in-process method
 * owning it start-to-finish), so compile-once-run-many-test-cases stays efficient across separate
 * HTTP calls. {@link WorkspaceCleanupTask} reaps anything left idle too long.
 */
@Service
public class DockerExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DockerExecutionService.class);
    private static final String LANGUAGE_MARKER = ".language";
    private static final String ACTIVITY_MARKER = ".last-activity";

    private final DockerClient dockerClient;
    private final ExecutionServerProperties properties;

    public DockerExecutionService(DockerClient dockerClient, ExecutionServerProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    public boolean isAvailable() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.warn("Docker Engine unreachable: {}", e.getMessage());
            return false;
        }
    }

    public CompileResponse compile(CompileRequest request) {
        long start = System.currentTimeMillis();
        Path workspaceDir = containerWorkspaceDir(request.executionId());
        try {
            Files.createDirectories(workspaceDir.resolve("src"));
            Files.createDirectories(workspaceDir.resolve("bin"));
            Files.createDirectories(workspaceDir.resolve("output"));
            Files.writeString(workspaceDir.resolve("src").resolve(sourceFileName(request.language())), request.sourceCode(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.writeString(workspaceDir.resolve(LANGUAGE_MARKER), request.language().name(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            touchActivity(workspaceDir);

            UnaryOperator<HostConfig> hardening = hc -> hardenedHostConfig(hc, request.memoryLimitMb(), request.cpuLimit(),
                    request.pidsLimit());
            String containerId = createContainer(List.of("compile", languageToken(request.language())),
                    List.of(new Bind(hostWorkspacePath(request.executionId()), new Volume("/workspace"), AccessMode.rw)),
                    hardening);
            try {
                dockerClient.startContainerCmd(containerId).exec();
                boolean finished = awaitCompletion(containerId, request.timeoutSeconds());
                long durationMs = System.currentTimeMillis() - start;
                if (!finished) {
                    return new CompileResponse(false, "Compilation timed out.", durationMs);
                }
                int exitCode = readExitCode(workspaceDir.resolve("output/compile_exit"));
                long limitBytes = request.outputLimitKb() * 1024L;
                String stderr = readCapped(workspaceDir.resolve("output/compile_stderr"), limitBytes);
                return exitCode == 0 ? new CompileResponse(true, null, durationMs) : new CompileResponse(false, stderr, durationMs);
            } finally {
                removeContainer(containerId);
            }
        } catch (Exception e) {
            log.error("Compile failed for execution {}", request.executionId(), e);
            return new CompileResponse(false, null, System.currentTimeMillis() - start);
        }
    }

    public RunResponse run(RunRequest request) {
        long start = System.currentTimeMillis();
        Path workspaceDir = containerWorkspaceDir(request.executionId());
        Path languageMarker = workspaceDir.resolve(LANGUAGE_MARKER);
        if (!Files.exists(languageMarker)) {
            // No prior compile() for this executionId (never happened, or its workspace already
            // expired) -- can't run against nothing. Never the student's fault; the caller retries.
            log.warn("No workspace found for execution {} (expired or never compiled)", request.executionId());
            return RunResponse.systemError(System.currentTimeMillis() - start);
        }
        try {
            CodingLanguage language = CodingLanguage.valueOf(Files.readString(languageMarker, StandardCharsets.UTF_8).trim());
            touchActivity(workspaceDir);

            Path runDir = workspaceDir.resolve("run");
            deleteRecursively(runDir);
            Files.createDirectories(runDir.resolve("input"));
            Files.createDirectories(runDir.resolve("output"));
            Files.writeString(runDir.resolve("input/stdin.txt"), request.stdin(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            List<Bind> binds = List.of(
                    new Bind(hostWorkspacePath(request.executionId()), new Volume("/workspace"), AccessMode.ro),
                    new Bind(hostWorkspacePath(request.executionId()) + "/run", new Volume("/run"), AccessMode.rw));

            UnaryOperator<HostConfig> hardening = hc -> hardenedHostConfig(hc, request.memoryLimitMb(), request.cpuLimit(),
                    request.pidsLimit());
            String containerId = createContainer(List.of("run", languageToken(language)), binds, hardening);

            long runStart = System.currentTimeMillis();
            try {
                dockerClient.startContainerCmd(containerId).exec();
                boolean finished = awaitCompletion(containerId, request.timeoutSeconds());
                long durationMs = System.currentTimeMillis() - runStart;

                if (!finished) {
                    dockerClient.killContainerCmd(containerId).exec();
                    return new RunResponse(RunStatus.TIMEOUT, null, null, durationMs);
                }
                if (wasOomKilled(containerId)) {
                    return new RunResponse(RunStatus.MEMORY_LIMIT, null, null, durationMs);
                }

                Path stdoutFile = runDir.resolve("output/stdout");
                Path stderrFile = runDir.resolve("output/stderr");
                Path exitFile = runDir.resolve("output/exit_code");
                long limitBytes = request.outputLimitKb() * 1024L;

                if (exceedsLimit(stdoutFile, limitBytes) || exceedsLimit(stderrFile, limitBytes)) {
                    return new RunResponse(RunStatus.OUTPUT_LIMIT, null, null, durationMs);
                }

                int exitCode = readExitCode(exitFile);
                String stdout = readCapped(stdoutFile, limitBytes);
                String stderr = readCapped(stderrFile, limitBytes);
                RunStatus status = exitCode == 0 ? RunStatus.SUCCESS : RunStatus.RUNTIME_ERROR;
                return new RunResponse(status, stdout, stderr, durationMs);
            } finally {
                removeContainer(containerId);
            }
        } catch (Exception e) {
            log.error("Run failed for execution {}", request.executionId(), e);
            return RunResponse.systemError(System.currentTimeMillis() - start);
        }
    }

    // Same hardening applied to compile and run alike -- a malicious source file can attack the
    // compiler itself (runaway template/macro expansion), not just the compiled program.
    private HostConfig hardenedHostConfig(HostConfig hostConfig, int memoryLimitMb, double cpuLimit, int pidsLimit) {
        long memoryLimitBytes = memoryLimitMb * 1024L * 1024L;
        return hostConfig
                .withNetworkMode("none")
                .withMemory(memoryLimitBytes)
                .withMemorySwap(memoryLimitBytes)
                .withNanoCPUs((long) (cpuLimit * 1_000_000_000L))
                .withPidsLimit((long) pidsLimit)
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of("/tmp", "size=64m,mode=1777"))
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"));
    }

    private String createContainer(List<String> cmd, List<Bind> binds, UnaryOperator<HostConfig> extraConfig) {
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

    // This app's own view of the workspace (for reading/writing files locally) -- see
    // ExecutionServerProperties' javadoc for why this differs from hostWorkspacePath().
    private Path containerWorkspaceDir(String executionId) {
        return Path.of(properties.getWorkspaceContainerPath(), sanitize(executionId));
    }

    // The path the HOST Docker daemon can resolve for a bind mount -- required whenever this app
    // itself runs inside a container with the host socket mounted in (Docker-outside-of-Docker).
    // Falls back to the same value as containerWorkspaceDir when this app runs as a plain,
    // non-containerized process (workspaceHostPath left unset), where there's no path-mapping gap.
    private String hostWorkspacePath(String executionId) {
        String base = properties.getWorkspaceHostPath().isBlank() ? properties.getWorkspaceContainerPath()
                : properties.getWorkspaceHostPath();
        return base + "/" + sanitize(executionId);
    }

    // executionId always originates as a UUID.toString() from the trusted main backend, but never
    // trust a caller-supplied string as a filesystem path component without stripping path
    // separators first -- defense in depth against a malformed/malicious executionId.
    private String sanitize(String executionId) {
        return executionId.replaceAll("[^a-zA-Z0-9-]", "");
    }

    private void touchActivity(Path workspaceDir) throws IOException {
        Files.writeString(workspaceDir.resolve(ACTIVITY_MARKER), Long.toString(System.currentTimeMillis()),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort -- WorkspaceCleanupTask will reap anything left behind anyway.
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

    private boolean exceedsLimit(Path file, long maxBytes) throws IOException {
        return Files.exists(file) && Files.size(file) > maxBytes;
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
