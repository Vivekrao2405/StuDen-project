package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.execution.ExecutionJobKind;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.practical.execution.ExecutionJobStatus;
import com.studen.practical.execution.ExecutionMessages;
import com.studen.practical.execution.ExecutionOrchestrator;
import com.studen.practical.execution.ExecutionRecorder;
import com.studen.practical.execution.PracticalExecutionResult;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the student-facing practical-attempt lifecycle: start/resume, autosave, deadline
 * expiry, submit, and (Phase 7.5) real Run/Submit execution. Every learner-facing lookup is scoped
 * by {@code userId} via {@link PracticalAttemptRepository#findByIdAndUserId} — never a bare
 * {@code findById} — mirroring {@code com.studen.assessment.AssessmentService} exactly (spec §31).
 *
 * <p>Owns none of the execution/scoring policy itself — {@link ExecutionOrchestrator} does the
 * actual grading and returns a factual result; this class only decides what that result means for
 * the attempt (score, status transition), per this phase's "infrastructure layer only" rule.
 */
@Service
public class PracticalAttemptService {

    private static final int DEFAULT_MAX_SCORE = 100;

    private final PracticalAttemptRepository attemptRepository;
    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalAssessmentService assessmentService;
    private final PracticalTestCaseRepository testCaseRepository;
    private final ExecutionJobRepository executionJobRepository;
    private final UserRepository userRepository;
    private final ExecutionOrchestrator executionOrchestrator;
    private final ExecutionRecorder executionRecorder;
    private final ObjectMapper objectMapper;

    public PracticalAttemptService(PracticalAttemptRepository attemptRepository,
            PracticalAssessmentRepository assessmentRepository, PracticalAssessmentService assessmentService,
            PracticalTestCaseRepository testCaseRepository, ExecutionJobRepository executionJobRepository,
            UserRepository userRepository, ExecutionOrchestrator executionOrchestrator,
            ExecutionRecorder executionRecorder, ObjectMapper objectMapper) {
        this.attemptRepository = attemptRepository;
        this.assessmentRepository = assessmentRepository;
        this.assessmentService = assessmentService;
        this.testCaseRepository = testCaseRepository;
        this.executionJobRepository = executionJobRepository;
        this.userRepository = userRepository;
        this.executionOrchestrator = executionOrchestrator;
        this.executionRecorder = executionRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PracticalAttemptResponse startOrResume(UUID userId, UUID assessmentId) {
        PracticalAssessment assessment = assessmentRepository
                .findByIdAndStatus(assessmentId, PracticalAssessmentStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));

        PracticalAttempt existing = attemptRepository
                .findByUserIdAndPracticalAssessmentIdAndStatus(userId, assessmentId, PracticalAttemptStatus.IN_PROGRESS)
                .orElse(null);
        if (existing != null) {
            if (expireIfDue(existing)) {
                existing = attemptRepository.findByIdAndUserId(existing.getId(), userId).orElse(null);
            }
            if (existing != null && existing.getStatus() == PracticalAttemptStatus.IN_PROGRESS) {
                return toInProgressView(existing, assessment);
            }
        }

        User userRef = userRepository.getReferenceById(userId);
        Instant now = Instant.now();
        Instant deadline = now.plusSeconds(assessment.getTimeLimitMinutes() * 60L);
        PracticalAttempt attempt = new PracticalAttempt(assessment, userRef, now, deadline, DEFAULT_MAX_SCORE);
        attempt = attemptRepository.save(attempt);

        return toInProgressView(attempt, assessment);
    }

    @Transactional
    public Object getAttempt(UUID userId, UUID attemptId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
        }
        return attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS
                ? toInProgressView(attempt, attempt.getPracticalAssessment())
                : toResultView(attempt);
    }

    @Transactional
    public PracticalAttemptResponse saveProgress(UUID userId, UUID attemptId, SaveAttemptRequest request) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
        }
        if (attempt.getStatus() != PracticalAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is no longer in progress.");
        }

        if (request.submissionContent() != null) {
            attempt.setSubmissionContent(request.submissionContent());
        }
        if (request.selectedLanguage() != null) {
            attempt.setSelectedLanguage(request.selectedLanguage());
        }
        if (request.submissionLinkUrl() != null) {
            attempt.setSubmissionLinkUrl(request.submissionLinkUrl());
        }
        attemptRepository.save(attempt);

        return toInProgressView(attempt, attempt.getPracticalAssessment());
    }

    @Transactional
    public PracticalAttemptResultResponse submit(UUID userId, UUID attemptId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);

        if (attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS && expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
        }
        if (attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS) {
            // A racing/double-click second submit finds this already flipped and its own
            // transitionIfInProgress affects 0 rows — harmless, every caller re-fetches and
            // trusts stored state (spec §26), mirroring AssessmentService.submit exactly. This
            // claim also doubles as the auto-grading gate: only the request that wins it grades.
            attemptRepository.transitionIfInProgress(attemptId, PracticalAttemptStatus.UNDER_REVIEW, Instant.now());
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
            attempt = tryAutoGrade(attempt);
        } else if (attempt.getStatus() == PracticalAttemptStatus.UNDER_REVIEW && attempt.getScore() == null) {
            // Safe retry: a prior auto-grading attempt may have hit an infrastructure failure
            // (Docker unreachable, etc) and left this UNDER_REVIEW without ever scoring it.
            attempt = tryAutoGrade(attempt);
        } else if (attempt.getStatus() != PracticalAttemptStatus.UNDER_REVIEW
                && attempt.getStatus() != PracticalAttemptStatus.EVALUATED
                && attempt.getStatus() != PracticalAttemptStatus.EXPIRED
                && attempt.getStatus() != PracticalAttemptStatus.SUBMITTED) {
            throw new ConflictException("This attempt has already been finalized.");
        }
        return toResultView(attempt);
    }

    /**
     * Full test-set (public + hidden) grading for AUTOMATED CODING/SQL assessments. No-op (returns
     * the attempt unchanged, still UNDER_REVIEW for manual review) for every other
     * practicalType/evaluationType combination — that path is entirely unchanged from Phase 7.4.
     */
    private PracticalAttempt tryAutoGrade(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        boolean automatable = assessment.getEvaluationType() == EvaluationType.AUTOMATED
                && (assessment.getPracticalType() == PracticalType.CODING || assessment.getPracticalType() == PracticalType.SQL);
        if (!automatable) {
            return attempt;
        }

        String sourceCode = attempt.getSubmissionContent();
        if (sourceCode == null || sourceCode.isBlank()) {
            return attempt;
        }

        List<PracticalTestCase> testCases = testCaseRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        if (testCases.isEmpty()) {
            return attempt;
        }

        CodingLanguage language = null;
        PracticalExecutionResult result;
        if (assessment.getPracticalType() == PracticalType.CODING) {
            language = attempt.getSelectedLanguage();
            if (language == null) {
                return attempt;
            }
            result = executionOrchestrator.runCoding(language, sourceCode, testCases);
        } else {
            boolean ordered = isOrderedSqlComparison(assessment.getConfigurationJson());
            result = executionOrchestrator.runSql(sourceCode, testCases, ordered);
        }

        executionRecorder.record(attempt, ExecutionJobKind.SUBMIT, language, sourceCode, testCases, result);
        markFirstCompilationIfNeeded(attempt, assessment, result);

        if (result.status().isInfrastructureFailure()) {
            // Never the student's fault — leaves the attempt UNDER_REVIEW, unscored, retryable.
            return attempt;
        }

        int score = result.testsTotal() == null || result.testsTotal() == 0 ? 0
                : Math.round(result.testsPassed() * (float) attempt.getMaxScore() / result.testsTotal());
        attempt.setScore(score);
        attempt.setStatus(PracticalAttemptStatus.SUBMITTED);
        attempt.setEvaluatedAt(Instant.now());
        return attempt;
    }

    @Transactional
    public RunResultResponse run(UUID userId, UUID attemptId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (attempt.getStatus() != PracticalAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is no longer in progress.");
        }
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        PracticalType type = assessment.getPracticalType();
        if (type != PracticalType.CODING && type != PracticalType.SQL) {
            throw new InvalidRequestException("Run isn't available for this assessment type");
        }

        String sourceCode = attempt.getSubmissionContent();
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidRequestException(type == PracticalType.SQL ? "Write a query first." : "Write some code first.");
        }

        List<PracticalTestCase> publicTestCases = testCaseRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId()).stream()
                .filter(tc -> !tc.isHidden())
                .toList();

        CodingLanguage language = null;
        PracticalExecutionResult result;
        if (type == PracticalType.CODING) {
            language = attempt.getSelectedLanguage();
            if (language == null) {
                throw new InvalidRequestException("Select a language first.");
            }
            result = executionOrchestrator.runCoding(language, sourceCode, publicTestCases);
        } else {
            boolean ordered = isOrderedSqlComparison(assessment.getConfigurationJson());
            result = executionOrchestrator.runSql(sourceCode, publicTestCases, ordered);
        }

        executionRecorder.record(attempt, ExecutionJobKind.RUN, language, sourceCode, publicTestCases, result);
        markFirstCompilationIfNeeded(attempt, assessment, result);

        return toRunResponse(result, publicTestCases, type);
    }

    @Transactional(readOnly = true)
    public List<ExecutionJobSummaryResponse> executionHistory(UUID userId, UUID attemptId) {
        findOwnAttempt(userId, attemptId);
        return executionJobRepository.findAllByPracticalAttemptIdOrderByCreatedAtAsc(attemptId).stream()
                .map(ExecutionJobSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PracticalPageResponse<MyPracticalAttemptSummaryResponse> myAttempts(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size <= 0 ? 20 : size, 100));
        return PracticalPageResponse.of(
                attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId, pageable).map(MyPracticalAttemptSummaryResponse::from));
    }

    private void markFirstCompilationIfNeeded(PracticalAttempt attempt, PracticalAssessment assessment, PracticalExecutionResult result) {
        if (assessment.getPracticalType() != PracticalType.CODING) {
            return;
        }
        if (attempt.getFirstSuccessfulCompilationAt() != null) {
            return;
        }
        if (result.status() == ExecutionJobStatus.COMPLETED) {
            attempt.setFirstSuccessfulCompilationAt(Instant.now());
        }
    }

    private boolean isOrderedSqlComparison(String configurationJson) {
        if (configurationJson == null || configurationJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(configurationJson);
            JsonNode ordered = node.get("sqlOrderedComparison");
            return ordered != null && ordered.asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private RunResultResponse toRunResponse(PracticalExecutionResult result, List<PracticalTestCase> publicTestCases,
            PracticalType type) {
        Map<UUID, PracticalTestCase> byId = publicTestCases.stream()
                .collect(Collectors.toMap(PracticalTestCase::getId, Function.identity()));
        List<ExecutionTestResultResponse> testResults = result.testResults().stream()
                .map(outcome -> {
                    PracticalTestCase testCase = byId.get(outcome.testCaseId());
                    String input = testCase != null ? testCase.getInput() : null;
                    String expected = type == PracticalType.SQL || testCase == null ? null : testCase.getExpectedOutput();
                    return new ExecutionTestResultResponse(outcome.testCaseId(), outcome.passed(), input, expected,
                            outcome.actualOutput(), outcome.executionTimeMs(), outcome.status());
                }).toList();

        boolean isErrorStatus = result.status() == ExecutionJobStatus.COMPILATION_ERROR
                || result.status() == ExecutionJobStatus.SECURITY_ERROR;
        return new RunResultResponse(result.status(), ExecutionMessages.forResult(result),
                isErrorStatus ? result.compileError() : null, result.testsTotal() == null ? null : result.testsPassed(),
                result.testsTotal(), 0, 0, result.durationMs(), testResults);
    }

    private boolean expireIfDue(PracticalAttempt attempt) {
        if (attempt.getStatus() != PracticalAttemptStatus.IN_PROGRESS) {
            return false;
        }
        if (Instant.now().isBefore(attempt.getDeadline())) {
            return false;
        }
        attemptRepository.transitionIfInProgress(attempt.getId(), PracticalAttemptStatus.EXPIRED, Instant.now());
        return true;
    }

    // The one and only place a student ever receives the actual problem content — every caller of
    // this method has already resolved `attempt` via a userId-scoped lookup (findOwnAttempt,
    // or a freshly-created/resumed attempt in startOrResume), so ownership is already proven by
    // the time we get here. See PracticalAttemptResponse's javadoc.
    private PracticalAttemptResponse toInProgressView(PracticalAttempt attempt, PracticalAssessment assessment) {
        Long remaining = attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS
                ? Math.max(0, attempt.getDeadline().getEpochSecond() - Instant.now().getEpochSecond())
                : 0L;
        List<PracticalCodingLanguageResponse> languages = assessmentService.languagesWithStarterCode(assessment.getId());
        List<StudentTestCaseView> publicTestCases = assessmentService.publicTestCasesFor(assessment.getId());
        return new PracticalAttemptResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), assessment.getWorkspaceType(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getDeadline(), remaining, attempt.getSubmissionContent(), attempt.getSelectedLanguage(),
                attempt.getSubmissionLinkUrl(), attempt.getSubmissionFileUrl(), assessment.getInstructions(),
                assessment.getRequirements(), assessment.getConstraints(), assessment.getConfigurationJson(),
                languages, publicTestCases);
    }

    private PracticalAttemptResultResponse toResultView(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        return new PracticalAttemptResultResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), assessment.getDifficulty(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), attempt.getEvaluatedAt(), attempt.getScore(), attempt.getMaxScore(),
                attempt.getFeedback(), List.of());
    }

    private PracticalAttempt findOwnAttempt(UUID userId, UUID attemptId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Practical attempt not found"));
    }
}
