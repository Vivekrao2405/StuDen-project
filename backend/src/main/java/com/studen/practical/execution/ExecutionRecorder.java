package com.studen.practical.execution;

import com.studen.practical.CodingLanguage;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptQuestion;
import com.studen.practical.PracticalTestCase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns one {@link PracticalExecutionResult} into a persisted {@link ExecutionJob} + its
 * {@link ExecutionTestResult} rows -- the run-history/audit trail. Pure persistence, no scoring
 * policy: {@code com.studen.practical.PracticalAttemptService} decides what a result means for the
 * attempt.
 */
@Component
public class ExecutionRecorder {

    private final ExecutionJobRepository jobRepository;
    private final ExecutionTestResultRepository testResultRepository;

    public ExecutionRecorder(ExecutionJobRepository jobRepository, ExecutionTestResultRepository testResultRepository) {
        this.jobRepository = jobRepository;
        this.testResultRepository = testResultRepository;
    }

    @Transactional
    public ExecutionJob record(PracticalAttempt attempt, PracticalAttemptQuestion attemptQuestion, ExecutionJobKind kind,
            CodingLanguage language, String sourceCode, List<PracticalTestCase> testCases, PracticalExecutionResult result) {
        ExecutionJob job = new ExecutionJob(attempt, attemptQuestion, kind, language, sourceCode);
        job.setStatus(result.status());
        job.setTestsPassed(result.testsPassed());
        job.setTestsTotal(result.testsTotal());
        job.setCompileError(result.status() == ExecutionJobStatus.COMPILATION_ERROR ? result.compileError() : null);
        job.setDurationMs(result.durationMs());
        Instant completedAt = Instant.now();
        job.setCompletedAt(completedAt);
        job.setStartedAt(completedAt.minusMillis(Math.max(result.durationMs(), 0)));
        job = jobRepository.save(job);

        Map<java.util.UUID, PracticalTestCase> byId = testCases.stream()
                .collect(java.util.stream.Collectors.toMap(PracticalTestCase::getId, Function.identity()));
        for (TestCaseOutcome outcome : result.testResults()) {
            PracticalTestCase testCase = byId.get(outcome.testCaseId());
            if (testCase == null) {
                continue;
            }
            testResultRepository.save(new ExecutionTestResult(job, testCase, outcome.hidden(), outcome.passed(),
                    outcome.actualOutput(), outcome.executionTimeMs(), outcome.status()));
        }
        return job;
    }
}
