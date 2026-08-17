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
import com.studen.practical.execution.ExecutionProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Real {@link SqlExecutionService}. Bind-mounts the packaged {@code sql-entrypoint.sh} (extracted
 * once to a stable temp path -- Docker bind mounts need a real host file, not a classpath
 * resource) as the container's override entrypoint over the stock {@code postgres:16-alpine}
 * image, so no custom image build is required. See that script for the full in-container flow.
 */
@Service
@ConditionalOnProperty(prefix = "app.execution", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DockerSqlExecutionService implements SqlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DockerSqlExecutionService.class);
    // initdb + pg_ctl start + seed script apply, before either query even begins -- generous but
    // not open-ended (still bounded by the outer awaitCompletion call).
    private static final int STARTUP_ALLOWANCE_SECONDS = 15;

    private final DockerClient dockerClient;
    private final ExecutionProperties properties;
    private Path entrypointScript;

    public DockerSqlExecutionService(DockerClient dockerClient, ExecutionProperties properties) {
        this.dockerClient = dockerClient;
        this.properties = properties;
    }

    @PostConstruct
    void extractEntrypointScript() throws IOException {
        Path dir = Files.createTempDirectory("studen-sql-entrypoint");
        entrypointScript = dir.resolve("sql-entrypoint.sh");
        try (InputStream in = getClass().getResourceAsStream("/sandbox/sql-entrypoint.sh")) {
            if (in == null) {
                throw new IllegalStateException("sql-entrypoint.sh not found on classpath");
            }
            Files.copy(in, entrypointScript, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.setPosixFilePermissions(entrypointScript,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
                            PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                            PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException e) {
            // Windows dev host -- Docker Desktop's WSL2 file sharing doesn't need POSIX bits set here.
        }
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
    public SqlRunOutcome run(String seedScript, String studentQuery, String referenceQuery, int timeoutSeconds) {
        String rejection = SqlStatementGuard.reject(studentQuery);
        if (rejection != null) {
            return SqlRunOutcome.rejected(rejection);
        }

        Path workDir = null;
        String containerId = null;
        try {
            workDir = Files.createTempDirectory("studen-sql-" + UUID.randomUUID());
            Path inputDir = workDir.resolve("input");
            Path outputDir = workDir.resolve("output");
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);

            writeIfPresent(inputDir.resolve("seed.sql"), seedScript);
            writeIfPresent(inputDir.resolve("student.sql"), studentQuery);
            writeIfPresent(inputDir.resolve("reference.sql"), referenceQuery);

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withBinds(
                            new Bind(entrypointScript.toAbsolutePath().toString(),
                                    new Volume("/sandbox/entrypoint.sh"), AccessMode.ro),
                            new Bind(inputDir.toAbsolutePath().toString(), new Volume("/sandbox/input"), AccessMode.ro),
                            new Bind(outputDir.toAbsolutePath().toString(), new Volume("/sandbox/output"), AccessMode.rw))
                    .withNetworkMode("none")
                    .withMemory(properties.memoryLimitBytes())
                    .withMemorySwap(properties.memoryLimitBytes())
                    .withNanoCPUs((long) (properties.getCpuLimit() * 1_000_000_000L))
                    .withPidsLimit((long) properties.getPidsLimit())
                    .withCapDrop(Capability.ALL)
                    .withCapAdd(Capability.CHOWN, Capability.SETUID, Capability.SETGID, Capability.DAC_OVERRIDE)
                    .withSecurityOpts(List.of("no-new-privileges"))
                    .withTmpFs(Map.of("/tmp", "size=128m,mode=1777"));

            CreateContainerResponse response = dockerClient.createContainerCmd(properties.getSqlRunnerImage())
                    .withEntrypoint("/bin/sh", "/sandbox/entrypoint.sh")
                    .withEnv("STATEMENT_TIMEOUT_MS=" + (timeoutSeconds * 1000))
                    .withHostConfig(hostConfig)
                    .exec();
            containerId = response.getId();

            long start = System.currentTimeMillis();
            dockerClient.startContainerCmd(containerId).exec();
            // The container's own wall-clock budget must cover initdb/pg_ctl-start/seed overhead
            // on top of the per-statement timeout -- otherwise even a fast, correct query would
            // race the outer kill before Postgres's own statement_timeout ever gets to fire.
            boolean finished = awaitCompletion(containerId, timeoutSeconds + STARTUP_ALLOWANCE_SECONDS);
            long durationMs = System.currentTimeMillis() - start;

            if (!finished) {
                dockerClient.killContainerCmd(containerId).exec();
                return new SqlRunOutcome(SqlRunStatus.TIMEOUT, null, null, "Query exceeded the allowed time.", durationMs);
            }

            String status = readTrimmed(outputDir.resolve("status"));
            if (!"DONE".equals(status)) {
                String error = readCapped(outputDir.resolve("error"), properties.outputLimitBytes());
                return new SqlRunOutcome(SqlRunStatus.SYSTEM_ERROR, null, null, error, durationMs);
            }

            int studentExit = readExitCode(outputDir.resolve("student_exit"));
            if (studentExit != 0) {
                String error = sanitizeQueryError(readCapped(outputDir.resolve("student_exit.log"), properties.outputLimitBytes()));
                return new SqlRunOutcome(SqlRunStatus.QUERY_ERROR, null, null, error, durationMs);
            }

            int referenceExit = readExitCode(outputDir.resolve("reference_exit"));
            if (referenceExit != 0) {
                // The reference query is admin-authored -- a failure here is an infra/content
                // problem, never the student's fault.
                log.error("SQL reference query failed for a sandbox run: {}",
                        readCapped(outputDir.resolve("reference_exit.log"), properties.outputLimitBytes()));
                return SqlRunOutcome.systemError();
            }

            String studentCsv = readCapped(outputDir.resolve("student_result.csv"), properties.outputLimitBytes());
            String referenceCsv = readCapped(outputDir.resolve("reference_result.csv"), properties.outputLimitBytes());
            return new SqlRunOutcome(SqlRunStatus.SUCCESS, studentCsv, referenceCsv, null, durationMs);
        } catch (Exception e) {
            log.error("Docker SQL execution failed", e);
            return SqlRunOutcome.systemError();
        } finally {
            if (containerId != null) {
                removeContainer(containerId);
            }
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
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

    private void removeContainer(String containerId) {
        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
        } catch (NotFoundException ignored) {
            // Already gone.
        } catch (Exception e) {
            log.warn("Failed to remove SQL sandbox container {}: {}", containerId, e.getMessage());
        }
    }

    private void writeIfPresent(Path file, String content) throws IOException {
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String readTrimmed(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8).trim() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private int readExitCode(Path file) {
        String raw = readTrimmed(file);
        try {
            return raw == null || raw.isEmpty() ? -1 : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
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

    // psql error output can echo back the failing SQL text (safe -- it's the student's own query,
    // never server-side content) but never a file path/connection string; strips a leading
    // "psql:/tmp/wrapper.sql:N:" prefix so no in-container path leaks into student-facing text.
    private String sanitizeQueryError(String rawLog) {
        if (rawLog == null) {
            return "Your query failed to run.";
        }
        return rawLog.replaceAll("(?m)^psql:[^:]*:\\d+:\\s*", "").strip();
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a throwaway temp dir.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup of a throwaway temp dir.
        }
    }
}
