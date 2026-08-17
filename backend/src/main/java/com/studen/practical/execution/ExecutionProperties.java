package com.studen.practical.execution;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Trusted, env-overridable configuration for the execution sandbox -- same {@code app.*} idiom as
 * {@code com.studen.assessment.AssessmentProperties}/{@code com.studen.assessment.ScoringProperties}.
 * {@code enabled=false} (or Docker being genuinely unreachable) cleanly falls back to the honest
 * {@code UnavailableCodeExecutionService}/{@code UnavailableSqlExecutionService} -- Phase 7.4
 * behavior is fully preserved, nothing regresses.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.execution")
public class ExecutionProperties {

    private boolean enabled = true;

    // Empty = docker-java's own default resolution (DOCKER_HOST env var, or the platform default
    // local socket/named pipe). Set to tcp://host:2376 for a remote prod Docker Engine.
    private String dockerHost = "";
    private boolean dockerTlsVerify = false;
    private String dockerCertPath = "";

    // Hard ceiling on concurrent containers regardless of how many HTTP requests arrive at once.
    private int maxConcurrency = 4;
    // How long a request will wait for a free execution slot before giving up as SYSTEM_ERROR
    // (busy, safe to retry) rather than queuing indefinitely.
    private int queueWaitSeconds = 10;

    private int compileTimeoutSeconds = 10;
    private int runTimeoutSeconds = 5;
    private int sqlTimeoutSeconds = 5;

    private int memoryLimitMb = 256;
    private double cpuLimit = 1.0;
    private int pidsLimit = 64;
    private int outputLimitKb = 64;

    private String runnerImage = "studen-code-runner:latest";
    // Stock upstream image -- no custom build needed, POSTGRES_USER/PASSWORD/DB are set per
    // container at create time by DockerSqlExecutionService.
    private String sqlRunnerImage = "postgres:16-alpine";

    public long memoryLimitBytes() {
        return memoryLimitMb * 1024L * 1024L;
    }

    public long outputLimitBytes() {
        return outputLimitKb * 1024L;
    }
}
