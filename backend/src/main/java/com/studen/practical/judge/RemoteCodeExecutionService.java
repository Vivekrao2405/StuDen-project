package com.studen.practical.judge;

import com.studen.practical.execution.ExecutionProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Real {@link CodeExecutionService} for production (Render, no Docker Engine reachable): every
 * {@link #compile}/{@link #run} call is one authenticated HTTPS request to the separate,
 * self-hosted {@code execution-server} app — never a container this JVM manages itself, never
 * student code executed inside this process. Active only when {@code app.execution.provider=remote}
 * (see {@link RemoteProviderCondition}); {@link DockerCodeExecutionService} remains the local-dev
 * default (and can also be run in-process locally, or {@code remote} can point at a locally-running
 * execution-server to exercise the exact production code path before deploying).
 *
 * <p>{@code executionId} (stable across one submission's {@code compile()} call and every
 * subsequent {@code run()} call, per {@link ExecutionRequest}'s own contract) doubles as the
 * execution-server's workspace key — it compiles once and keeps that compiled workspace on disk
 * across calls, so this preserves the same compile-once-run-many-test-cases efficiency the local
 * Docker path already has, without any change to {@code ExecutionOrchestrator}.
 */
@Service
@Conditional(RemoteProviderCondition.class)
public class RemoteCodeExecutionService implements CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RemoteCodeExecutionService.class);

    private final RestClient restClient;
    private final ExecutionProperties properties;
    private final boolean configured;

    public RemoteCodeExecutionService(RestClient.Builder restClientBuilder, ExecutionProperties properties) {
        this.properties = properties;
        this.configured = !properties.getExecutionServerUrl().isBlank() && !properties.getExecutionServerApiKey().isBlank();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        // Generous ceiling above the longest single call this app makes (compile or one test-case
        // run, each capped at compile/run-timeout-seconds) -- the execution-server's own container
        // timeout is what actually bounds the student's program; this just bounds our HTTP wait for
        // its response on top of that.
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = restClientBuilder
                .baseUrl(configured ? properties.getExecutionServerUrl() : "http://unconfigured.invalid")
                .requestFactory(requestFactory)
                .defaultHeader("X-Execution-Api-Key", properties.getExecutionServerApiKey())
                .build();

        if (!configured) {
            log.warn("Remote execution server is not configured (EXECUTION_SERVER_URL/EXECUTION_SERVER_API_KEY "
                    + "missing) — code execution will report unavailable");
        }
    }

    @Override
    public boolean isAvailable() {
        // Unlike Judge0/a third-party API, this IS infrastructure we run and monitor ourselves, so
        // a live check (mirroring DockerCodeExecutionService.isAvailable()'s pingCmd() fail-fast
        // rationale) is the right default here, not just a cheap "configured" flag.
        if (!configured) {
            return false;
        }
        try {
            JsonNode health = restClient.get().uri("/health").retrieve().body(JsonNode.class);
            return health != null && "UP".equalsIgnoreCase(textOrNull(health, "status"));
        } catch (Exception e) {
            log.warn("Execution server unreachable: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public CompileOutcome compile(ExecutionRequest request) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("executionId", request.executionId().toString());
            body.put("language", request.language().name());
            body.put("sourceCode", request.sourceCode());
            body.put("timeoutSeconds", properties.getCompileTimeoutSeconds());
            body.put("memoryLimitMb", properties.getMemoryLimitMb());
            body.put("cpuLimit", properties.getCpuLimit());
            body.put("pidsLimit", properties.getPidsLimit());
            body.put("outputLimitKb", properties.getOutputLimitKb());

            JsonNode result = restClient.post()
                    .uri("/compile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            long durationMs = System.currentTimeMillis() - start;
            boolean success = result != null && result.get("success") != null && result.get("success").asBoolean();
            return success ? CompileOutcome.success(durationMs) : CompileOutcome.failure(textOrNull(result, "stderr"), durationMs);
        } catch (Exception e) {
            log.error("Remote compile call failed", e);
            return CompileOutcome.failure(null, System.currentTimeMillis() - start);
        }
    }

    @Override
    public RunOutcome run(ExecutionRequest request, String stdin) {
        long start = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("executionId", request.executionId().toString());
            body.put("stdin", stdin == null ? "" : stdin);
            body.put("timeoutSeconds", properties.getRunTimeoutSeconds());
            body.put("memoryLimitMb", properties.getMemoryLimitMb());
            body.put("cpuLimit", properties.getCpuLimit());
            body.put("pidsLimit", properties.getPidsLimit());
            body.put("outputLimitKb", properties.getOutputLimitKb());

            JsonNode result = restClient.post()
                    .uri("/run")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            long durationMs = System.currentTimeMillis() - start;
            if (result == null) {
                return RunOutcome.systemError();
            }
            RunStatus status = parseStatus(textOrNull(result, "status"));
            String stdout = textOrNull(result, "stdout");
            String stderr = textOrNull(result, "stderr");
            return new RunOutcome(status, status == RunStatus.SUCCESS || status == RunStatus.RUNTIME_ERROR ? stdout : null,
                    status == RunStatus.SUCCESS || status == RunStatus.RUNTIME_ERROR ? stderr : null, durationMs);
        } catch (Exception e) {
            log.error("Remote run call failed", e);
            return RunOutcome.systemError();
        }
    }

    private RunStatus parseStatus(String raw) {
        if (raw == null) {
            return RunStatus.SYSTEM_ERROR;
        }
        try {
            return RunStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            // The execution-server sent a status this app doesn't recognize (version skew) --
            // treat conservatively as an infrastructure issue rather than guessing.
            log.warn("Unexpected execution-server run status: {}", raw);
            return RunStatus.SYSTEM_ERROR;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
