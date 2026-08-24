package com.studen.executionserver;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Trusted, env-var-backed configuration -- same {@code app.*} idiom the main backend uses.
 * Deliberately carries NO memory/cpu/pids/output-limit fields: those stay owned by the main
 * backend's own {@code ExecutionProperties} (admin-configurable there) and are passed in on every
 * {@code /compile}/{@code /run} request instead, so there's exactly one source of truth for
 * sandbox resource limits, not two configs that can drift out of sync.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class ExecutionServerProperties {

    // Must exactly match EXECUTION_SERVER_API_KEY on the main backend. No default -- see
    // ApiKeyAuthFilter, which rejects every /compile and /run request when this is blank (fails
    // closed, never silently accepts unauthenticated requests).
    private String apiKey = "";

    // Never allow the caller to name an arbitrary image -- CodingLanguage always maps to this one
    // fixed, pre-built image (see backend/docker/runner/), exactly like DockerCodeExecutionService.
    private String runnerImage = "studen-code-runner:latest";

    // Empty = docker-java's own default resolution (DOCKER_HOST env var, or the platform default
    // local socket/named pipe) -- the normal case when this app itself has direct Docker access
    // (Docker-outside-of-Docker via a mounted socket, or a native install on the same host).
    private String dockerHost = "";
    private boolean dockerTlsVerify = false;
    private String dockerCertPath = "";

    // See the plan's "DooD path-mapping" note: workspaceHostPath is the path the HOST Docker
    // daemon can resolve (used when bind-mounting a workspace into a sandbox container);
    // workspaceContainerPath is where that same directory is mounted inside this app's own
    // container (used when this app writes files locally). They must point at the same physical
    // directory. When this app runs as a plain, non-containerized process, both are simply equal.
    private String workspaceHostPath = "";
    private String workspaceContainerPath = "/workspaces";

    // How long a workspace (source + compiled artifact) is kept on disk after the last activity
    // before WorkspaceCleanupTask deletes it -- generously above the worst-case compile-timeout +
    // (test-case-count * run-timeout) for any realistic assessment.
    private int workspaceTtlSeconds = 180;
}
