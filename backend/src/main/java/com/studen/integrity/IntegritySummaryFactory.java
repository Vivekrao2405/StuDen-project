package com.studen.integrity;

import com.studen.practical.PracticalAttempt;
import com.studen.practical.execution.ExecutionJob;
import com.studen.practical.execution.ExecutionJobKind;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.practical.execution.ExecutionJobStatus;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds {@link IntegritySummaryResponse} for both the student-facing summary
 * ({@code IntegrityEventService#getSummary}) and the admin detail view
 * ({@code AdminIntegrityService}) -- a single place that reuses
 * {@link ExecutionJobRepository} for run/submission/compile-failure counts rather than each
 * caller re-deriving them (goal #10: no second execution-history system).
 */
@Component
public class IntegritySummaryFactory {

    private final ExecutionJobRepository executionJobRepository;

    public IntegritySummaryFactory(ExecutionJobRepository executionJobRepository) {
        this.executionJobRepository = executionJobRepository;
    }

    public IntegritySummaryResponse build(PracticalAttempt attempt) {
        List<ExecutionJob> jobs = executionJobRepository.findAllByPracticalAttemptIdOrderByCreatedAtAsc(attempt.getId());
        int runCount = (int) jobs.stream().filter(j -> j.getKind() == ExecutionJobKind.RUN).count();
        int submissionCount = (int) jobs.stream().filter(j -> j.getKind() == ExecutionJobKind.SUBMIT).count();
        int compilationFailureCount = (int) jobs.stream()
                .filter(j -> j.getStatus() == ExecutionJobStatus.COMPILATION_ERROR).count();

        IntegrityStatus effectiveStatus = attempt.getIntegrityOverrideStatus() != null
                ? attempt.getIntegrityOverrideStatus() : attempt.getIntegrityStatus();

        return new IntegritySummaryResponse(
                attempt.getId(),
                attempt.getIntegrityScore(),
                attempt.getIntegrityStatus(),
                effectiveStatus,
                attempt.getIntegrityOverrideStatus() != null,
                attempt.getIntegrityOverrideReason(),
                attempt.getIntegrityOverrideBy() != null ? attempt.getIntegrityOverrideBy().getFullName() : null,
                attempt.getIntegrityOverriddenAt(),
                attempt.getTotalIntegrityEventCount(),
                attempt.getSuspiciousEventCount(),
                attempt.getCriticalEventCount(),
                attempt.getTabSwitchCount(),
                attempt.getCopyAttemptCount(),
                attempt.getPasteAttemptCount(),
                attempt.getCutAttemptCount(),
                attempt.getFullscreenExitCount(),
                attempt.getMultipleSessionCount(),
                attempt.getFirstSuccessfulCompilationAt(),
                runCount,
                submissionCount,
                compilationFailureCount);
    }
}
