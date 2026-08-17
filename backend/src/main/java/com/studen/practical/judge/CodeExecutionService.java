package com.studen.practical.judge;

/**
 * Low-level abstraction over "compile/run one program inside an isolated sandbox." Knows nothing
 * about test cases, expected output, or scoring -- that orchestration lives one layer up in
 * {@code com.studen.practical.execution.ExecutionOrchestrator}, which calls {@link #compile} once
 * per submission and {@link #run} once per test case against the same compiled workspace.
 *
 * <p>{@link DockerCodeExecutionService} is the real implementation: every call creates an
 * ephemeral, network-disabled, resource-limited, non-root container and destroys it afterward.
 * {@link UnavailableCodeExecutionService} is the honest fallback when execution infrastructure
 * isn't configured/reachable -- callers must check {@link #isAvailable()} before calling
 * {@link #compile}/{@link #run} and never attempt to invoke them regardless.
 */
public interface CodeExecutionService {

    boolean isAvailable();

    CompileOutcome compile(ExecutionRequest request);

    RunOutcome run(ExecutionRequest request, String stdin);
}
