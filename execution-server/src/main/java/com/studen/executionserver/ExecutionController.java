package com.studen.executionserver;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The whole business-facing surface of this app: compile a submission once, then run it (against
 * one stdin) as many times as the caller needs, both keyed by {@code executionId}. Authenticated by
 * {@link ApiKeyAuthFilter} before either handler runs.
 */
@RestController
public class ExecutionController {

    private final DockerExecutionService executionService;

    public ExecutionController(DockerExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/compile")
    public ResponseEntity<CompileResponse> compile(@Valid @RequestBody CompileRequest request) {
        return ResponseEntity.ok(executionService.compile(request));
    }

    @PostMapping("/run")
    public ResponseEntity<RunResponse> run(@Valid @RequestBody RunRequest request) {
        return ResponseEntity.ok(executionService.run(request));
    }
}
