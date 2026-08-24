package com.studen.practical.execution;

import com.studen.practical.judge.CodeExecutionService;
import com.studen.practical.judge.SqlExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Read-only, authenticated-by-default (no @PreAuthorize -- any signed-in student/admin may check
// this before attempting a Run/Check). See ExecutionStatusResponse's javadoc for why this is a
// dedicated endpoint rather than folded into Actuator's aggregate /actuator/health.
@RestController
@RequestMapping("/api/v1/execution-status")
public class ExecutionStatusController {

    private final CodeExecutionService codeExecutionService;
    private final SqlExecutionService sqlExecutionService;

    public ExecutionStatusController(CodeExecutionService codeExecutionService, SqlExecutionService sqlExecutionService) {
        this.codeExecutionService = codeExecutionService;
        this.sqlExecutionService = sqlExecutionService;
    }

    @GetMapping
    public ExecutionStatusResponse status() {
        return new ExecutionStatusResponse(codeExecutionService.isAvailable(), sqlExecutionService.isAvailable());
    }
}
