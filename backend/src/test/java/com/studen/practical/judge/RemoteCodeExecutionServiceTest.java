package com.studen.practical.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.studen.practical.CodingLanguage;
import com.studen.practical.execution.ExecutionProperties;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Replaces the never-committed Judge0CodeExecutionServiceTest -- same MockRestServiceServer
 * technique, but against our own simple JSON contract (see execution-server's Compile/RunRequest
 * and Compile/RunResponse records) instead of Judge0's status-ID table, so there's no translation
 * guesswork: {@link RunStatus} values pass straight through by name.
 */
class RemoteCodeExecutionServiceTest {

    private static final UUID EXECUTION_ID = UUID.randomUUID();

    private MockRestServiceServer mockServer;
    private RemoteCodeExecutionService service;
    private ExecutionRequest request;

    @BeforeEach
    void setUp() {
        ExecutionProperties properties = new ExecutionProperties();
        properties.setExecutionServerUrl("http://execution-server.test");
        properties.setExecutionServerApiKey("test-api-key");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://execution-server.test")
                .defaultHeader("X-Execution-Api-Key", "test-api-key");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new RemoteCodeExecutionService(builder.build(), properties, true);
        request = new ExecutionRequest(EXECUTION_ID, CodingLanguage.PYTHON, "print('hi')", Path.of("unused"));
    }

    @Test
    void isAvailable_healthReportsUp_returnsTrue() {
        mockServer.expect(requestTo("http://execution-server.test/health"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Execution-Api-Key", "test-api-key"))
                .andRespond(withSuccess("{\"status\":\"UP\",\"docker\":\"AVAILABLE\"}", MediaType.APPLICATION_JSON));

        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_healthReportsDown_returnsFalse() {
        mockServer.expect(requestTo("http://execution-server.test/health"))
                .andRespond(withSuccess("{\"status\":\"DOWN\",\"docker\":\"UNAVAILABLE\"}", MediaType.APPLICATION_JSON));

        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_serverUnreachable_returnsFalse() {
        mockServer.expect(requestTo("http://execution-server.test/health")).andRespond(withServerError());

        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_notConfigured_returnsFalseWithoutCallingServer() {
        ExecutionProperties unconfigured = new ExecutionProperties();
        RestClient.Builder builder = RestClient.builder();
        RemoteCodeExecutionService unconfiguredService = new RemoteCodeExecutionService(builder, unconfigured);

        assertThat(unconfiguredService.isAvailable()).isFalse();
    }

    @Test
    void compile_success_returnsSuccessOutcome() {
        mockServer.expect(requestTo("http://execution-server.test/compile"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Execution-Api-Key", "test-api-key"))
                .andExpect(jsonPath("$.executionId").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.language").value("PYTHON"))
                .andExpect(jsonPath("$.sourceCode").value("print('hi')"))
                .andRespond(withSuccess("{\"success\":true,\"stderr\":null,\"durationMs\":42}", MediaType.APPLICATION_JSON));

        CompileOutcome outcome = service.compile(request);

        assertThat(outcome.success()).isTrue();
        assertThat(outcome.stderr()).isNull();
    }

    @Test
    void compile_failure_returnsStderr() {
        mockServer.expect(requestTo("http://execution-server.test/compile"))
                .andRespond(withSuccess("{\"success\":false,\"stderr\":\"SyntaxError\",\"durationMs\":10}", MediaType.APPLICATION_JSON));

        CompileOutcome outcome = service.compile(request);

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.stderr()).isEqualTo("SyntaxError");
    }

    @Test
    void compile_executionServerUnreachable_returnsFailureNotException() {
        mockServer.expect(requestTo("http://execution-server.test/compile")).andRespond(withServerError());

        CompileOutcome outcome = service.compile(request);

        assertThat(outcome.success()).isFalse();
    }

    @Test
    void run_success_returnsStdoutAndStderr() {
        mockServer.expect(requestTo("http://execution-server.test/run"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.executionId").value(EXECUTION_ID.toString()))
                .andExpect(jsonPath("$.stdin").value("input"))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\",\"stdout\":\"hi\",\"stderr\":\"\",\"durationMs\":5}",
                        MediaType.APPLICATION_JSON));

        RunOutcome outcome = service.run(request, "input");

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCESS);
        assertThat(outcome.stdout()).isEqualTo("hi");
    }

    @Test
    void run_timeout_returnsTimeoutStatusWithoutOutput() {
        mockServer.expect(requestTo("http://execution-server.test/run"))
                .andRespond(withSuccess("{\"status\":\"TIMEOUT\",\"stdout\":null,\"stderr\":null,\"durationMs\":5000}",
                        MediaType.APPLICATION_JSON));

        RunOutcome outcome = service.run(request, "input");

        assertThat(outcome.status()).isEqualTo(RunStatus.TIMEOUT);
        assertThat(outcome.stdout()).isNull();
    }

    @Test
    void run_unrecognizedStatus_treatedAsSystemError() {
        mockServer.expect(requestTo("http://execution-server.test/run"))
                .andRespond(withSuccess("{\"status\":\"SOMETHING_NEW\",\"stdout\":null,\"stderr\":null,\"durationMs\":1}",
                        MediaType.APPLICATION_JSON));

        RunOutcome outcome = service.run(request, "input");

        assertThat(outcome.status()).isEqualTo(RunStatus.SYSTEM_ERROR);
    }

    @Test
    void run_executionServerUnreachable_returnsSystemError() {
        mockServer.expect(requestTo("http://execution-server.test/run")).andRespond(withServerError());

        RunOutcome outcome = service.run(request, "input");

        assertThat(outcome.status()).isEqualTo(RunStatus.SYSTEM_ERROR);
    }

    @Test
    void run_nullStdin_sentAsEmptyString() {
        mockServer.expect(requestTo("http://execution-server.test/run"))
                .andExpect(jsonPath("$.stdin").value(""))
                .andRespond(withSuccess("{\"status\":\"SUCCESS\",\"stdout\":\"\",\"stderr\":\"\",\"durationMs\":1}",
                        MediaType.APPLICATION_JSON));

        RunOutcome outcome = service.run(request, null);

        assertThat(outcome.status()).isEqualTo(RunStatus.SUCCESS);
    }
}
