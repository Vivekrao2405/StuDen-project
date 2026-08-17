package com.studen.practical.judge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * The pre-Phase-7.5 fallback: honestly reports execution isn't available rather than fabricating a
 * result. Selected instead of {@link DockerCodeExecutionService} whenever {@code app.execution.
 * enabled=false} -- e.g. an environment with no Docker Engine reachable at all. Callers (
 * {@code com.studen.practical.execution.ExecutionOrchestrator}) must check {@link #isAvailable()}
 * first and route to manual review; {@link #compile}/{@link #run} on this implementation are only
 * ever reached by a genuine programming-error bypass of that check.
 */
@Service
@ConditionalOnProperty(prefix = "app.execution", name = "enabled", havingValue = "false")
public class UnavailableCodeExecutionService implements CodeExecutionService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public CompileOutcome compile(ExecutionRequest request) {
        throw new IllegalStateException("Code execution is not available in this environment");
    }

    @Override
    public RunOutcome run(ExecutionRequest request, String stdin) {
        throw new IllegalStateException("Code execution is not available in this environment");
    }
}
