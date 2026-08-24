package com.studen.practical.execution;

// Thrown by PracticalAttemptService.run()/AdminExecutionService.testRun() when the active
// CodeExecutionService/SqlExecutionService reports an infrastructure failure
// (ExecutionJobStatus.isInfrastructureFailure()) -- mapped to HTTP 503 by GlobalExceptionHandler
// rather than the endpoint returning a 200 with a fake result baked into the body.
public class ExecutionServiceUnavailableException extends RuntimeException {

    public ExecutionServiceUnavailableException(String message) {
        super(message);
    }
}
