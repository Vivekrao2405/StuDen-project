package com.studen.practical.execution;

import com.studen.practical.CodingLanguage;
import com.studen.practical.PracticalTestCase;
import com.studen.practical.judge.CodeExecutionService;
import com.studen.practical.judge.CompileOutcome;
import com.studen.practical.judge.ExecutionRequest;
import com.studen.practical.judge.RunOutcome;
import com.studen.practical.judge.SqlExecutionService;
import com.studen.practical.judge.SqlRunOutcome;
import com.studen.practical.judge.SqlRunStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Owns the actual grading run: acquires a bounded concurrency slot, compiles once (CODING) or
 * validates once (SQL), runs every supplied test case, and returns a factual, unpersisted
 * {@link PracticalExecutionResult}. Never decides what a result means for scoring/evaluation --
 * that's {@code com.studen.practical.PracticalAttemptService}'s job, exactly per this phase's
 * "infrastructure layer only" rule. Callers (student run/submit, admin test-question) both go
 * through here so there is exactly one place that talks to the judge layer.
 */
@Service
public class ExecutionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ExecutionOrchestrator.class);
    private static final int DISPLAY_OUTPUT_CAP = 4000;

    private final CodeExecutionService codeExecutionService;
    private final SqlExecutionService sqlExecutionService;
    private final ExecutionProperties properties;
    private final Semaphore semaphore;

    public ExecutionOrchestrator(CodeExecutionService codeExecutionService, SqlExecutionService sqlExecutionService,
            ExecutionProperties properties, Semaphore executionSemaphore) {
        this.codeExecutionService = codeExecutionService;
        this.sqlExecutionService = sqlExecutionService;
        this.properties = properties;
        this.semaphore = executionSemaphore;
    }

    public PracticalExecutionResult runCoding(CodingLanguage language, String sourceCode, List<PracticalTestCase> testCases) {
        long start = System.currentTimeMillis();
        boolean acquired = tryAcquire();
        if (!acquired) {
            log.warn("Execution semaphore exhausted -- rejecting a CODING run");
            return PracticalExecutionResult.systemError(0);
        }
        try {
            if (!codeExecutionService.isAvailable()) {
                return PracticalExecutionResult.systemError(System.currentTimeMillis() - start);
            }

            Path workspaceDir = Files.createTempDirectory("studen-exec-");
            try {
                ExecutionRequest request = new ExecutionRequest(UUID.randomUUID(), language, sourceCode, workspaceDir);
                CompileOutcome compile = codeExecutionService.compile(request);
                if (!compile.success()) {
                    // null/null, not 0/testCases.size() -- a compile failure means no test genuinely
                    // ran, same contract as systemError(); testsPassed must never read as "0 passed"
                    // when in fact nothing was ever executed.
                    return new PracticalExecutionResult(ExecutionJobStatus.COMPILATION_ERROR, cap(compile.stderr()),
                            List.of(), null, null, System.currentTimeMillis() - start);
                }

                List<TestCaseOutcome> outcomes = new ArrayList<>();
                for (PracticalTestCase testCase : testCases) {
                    RunOutcome run = codeExecutionService.run(request, testCase.getInput());
                    if (run.status() == com.studen.practical.judge.RunStatus.SYSTEM_ERROR) {
                        return PracticalExecutionResult.systemError(System.currentTimeMillis() - start);
                    }
                    outcomes.add(toCodingOutcome(testCase, run));
                }
                return complete(outcomes, System.currentTimeMillis() - start);
            } finally {
                deleteRecursively(workspaceDir);
            }
        } catch (IOException e) {
            log.error("Execution workspace setup failed", e);
            return PracticalExecutionResult.systemError(System.currentTimeMillis() - start);
        } finally {
            semaphore.release();
        }
    }

    public PracticalExecutionResult runSql(String studentQuery, List<PracticalTestCase> testCases, boolean orderedComparison) {
        long start = System.currentTimeMillis();
        boolean acquired = tryAcquire();
        if (!acquired) {
            log.warn("Execution semaphore exhausted -- rejecting a SQL run");
            return PracticalExecutionResult.systemError(0);
        }
        try {
            if (!sqlExecutionService.isAvailable()) {
                return PracticalExecutionResult.systemError(System.currentTimeMillis() - start);
            }

            List<TestCaseOutcome> outcomes = new ArrayList<>();
            for (PracticalTestCase testCase : testCases) {
                SqlRunOutcome run = sqlExecutionService.run(testCase.getInput(), studentQuery, testCase.getExpectedOutput(),
                        properties.getSqlTimeoutSeconds());
                if (run.status() == SqlRunStatus.SYSTEM_ERROR) {
                    return PracticalExecutionResult.systemError(System.currentTimeMillis() - start);
                }
                if (run.status() == SqlRunStatus.REJECTED) {
                    return new PracticalExecutionResult(ExecutionJobStatus.SECURITY_ERROR, cap(run.errorMessage()),
                            List.of(), 0, testCases.size(), System.currentTimeMillis() - start);
                }
                outcomes.add(toSqlOutcome(testCase, run, orderedComparison));
            }
            return complete(outcomes, System.currentTimeMillis() - start);
        } finally {
            semaphore.release();
        }
    }

    private PracticalExecutionResult complete(List<TestCaseOutcome> outcomes, long durationMs) {
        int passed = (int) outcomes.stream().filter(TestCaseOutcome::passed).count();
        return new PracticalExecutionResult(ExecutionJobStatus.COMPLETED, null, outcomes, passed, outcomes.size(), durationMs);
    }

    private TestCaseOutcome toCodingOutcome(PracticalTestCase testCase, RunOutcome run) {
        return switch (run.status()) {
            case SUCCESS -> {
                boolean passed = OutputComparator.matches(run.stdout(), testCase.getExpectedOutput(), testCase.getComparisonMode());
                yield new TestCaseOutcome(testCase.getId(), testCase.isHidden(), passed, cap(run.stdout()), run.durationMs(),
                        passed ? TestOutcomeStatus.PASSED : TestOutcomeStatus.WRONG_ANSWER);
            }
            case RUNTIME_ERROR -> new TestCaseOutcome(testCase.getId(), testCase.isHidden(), false, cap(run.stderr()),
                    run.durationMs(), TestOutcomeStatus.RUNTIME_ERROR);
            case TIMEOUT -> new TestCaseOutcome(testCase.getId(), testCase.isHidden(), false, null, run.durationMs(),
                    TestOutcomeStatus.TIMEOUT);
            case MEMORY_LIMIT -> new TestCaseOutcome(testCase.getId(), testCase.isHidden(), false, null, run.durationMs(),
                    TestOutcomeStatus.MEMORY_LIMIT);
            case OUTPUT_LIMIT -> new TestCaseOutcome(testCase.getId(), testCase.isHidden(), false, null, run.durationMs(),
                    TestOutcomeStatus.OUTPUT_LIMIT);
            case SYSTEM_ERROR -> throw new IllegalStateException("SYSTEM_ERROR must be handled by the caller");
        };
    }

    private TestCaseOutcome toSqlOutcome(PracticalTestCase testCase, SqlRunOutcome run, boolean ordered) {
        return switch (run.status()) {
            case SUCCESS -> {
                SqlResultComparator.ComparisonResult comparison =
                        SqlResultComparator.compare(run.studentCsv(), run.referenceCsv(), ordered);
                String summary = comparison.actualRowCount() + (comparison.actualRowCount() == 1 ? " row returned" : " rows returned");
                yield new TestCaseOutcome(testCase.getId(), testCase.isHidden(), comparison.matches(), summary, run.durationMs(),
                        comparison.matches() ? TestOutcomeStatus.PASSED : TestOutcomeStatus.WRONG_ANSWER);
            }
            case TIMEOUT -> new TestCaseOutcome(testCase.getId(), testCase.isHidden(), false, null, run.durationMs(),
                    TestOutcomeStatus.TIMEOUT);
            case QUERY_ERROR, REJECTED -> new TestCaseOutcome(testCase.getId(), testCase.isHidden(), false, cap(run.errorMessage()),
                    run.durationMs(), TestOutcomeStatus.RUNTIME_ERROR);
            case SYSTEM_ERROR -> throw new IllegalStateException("SYSTEM_ERROR must be handled by the caller");
        };
    }

    private boolean tryAcquire() {
        try {
            return semaphore.tryAcquire(properties.getQueueWaitSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String cap(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > DISPLAY_OUTPUT_CAP ? text.substring(0, DISPLAY_OUTPUT_CAP) + "\n... (truncated)" : text;
    }

    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
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
