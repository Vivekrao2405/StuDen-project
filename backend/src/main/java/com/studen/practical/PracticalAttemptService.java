package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ForbiddenActionException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.integrity.IntegrityPolicyResolver;
import com.studen.portfolio.PortfolioSkillProfileService;
import com.studen.practical.execution.ExecutionJobKind;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.practical.execution.ExecutionJobStatus;
import com.studen.practical.execution.ExecutionMessages;
import com.studen.practical.execution.ExecutionOrchestrator;
import com.studen.practical.execution.ExecutionRecorder;
import com.studen.practical.execution.PracticalExecutionResult;
import com.studen.practical.execution.TestOutcomeStatus;
import com.studen.skill.Skill;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Orchestrates the student-facing practical-attempt lifecycle: start/resume, per-question
 * autosave/run, deadline expiry, and submit (which auto-grades every AUTOMATED CODING/SQL question
 * and aggregates the total server-side — spec §11/§16). Every learner-facing lookup is scoped by
 * {@code userId} via a repository join-through-owner query — never a bare {@code findById} —
 * mirroring {@code com.studen.assessment.AssessmentService} exactly (spec §29/§31).
 *
 * <p>Phase 7.6: an attempt now owns one {@link PracticalAttemptQuestion} per {@link
 * PracticalQuestion} on the assessment (created once, at {@link #startOrResume}) instead of holding
 * a single submission directly on itself. {@code evaluationType} stays uniform across an
 * assessment's questions (decided at assessment-creation time), so grading branches on it exactly
 * once per submit, not per question.
 */
@Service
public class PracticalAttemptService {

    private final PracticalAttemptRepository attemptRepository;
    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalQuestionRepository questionRepository;
    private final PracticalAttemptQuestionRepository attemptQuestionRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final PracticalCodingLanguageRepository languageRepository;
    private final PracticalRubricScoreRepository rubricScoreRepository;
    private final ExecutionJobRepository executionJobRepository;
    private final UserRepository userRepository;
    private final ExecutionOrchestrator executionOrchestrator;
    private final ExecutionRecorder executionRecorder;
    private final ObjectMapper objectMapper;
    private final IntegrityPolicyResolver integrityPolicyResolver;
    private final PortfolioSkillProfileService skillProfileService;

    public PracticalAttemptService(PracticalAttemptRepository attemptRepository,
            PracticalAssessmentRepository assessmentRepository, PracticalQuestionRepository questionRepository,
            PracticalAttemptQuestionRepository attemptQuestionRepository, PracticalTestCaseRepository testCaseRepository,
            PracticalCodingLanguageRepository languageRepository, PracticalRubricScoreRepository rubricScoreRepository,
            ExecutionJobRepository executionJobRepository, UserRepository userRepository,
            ExecutionOrchestrator executionOrchestrator, ExecutionRecorder executionRecorder, ObjectMapper objectMapper,
            IntegrityPolicyResolver integrityPolicyResolver, PortfolioSkillProfileService skillProfileService) {
        this.attemptRepository = attemptRepository;
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.testCaseRepository = testCaseRepository;
        this.languageRepository = languageRepository;
        this.rubricScoreRepository = rubricScoreRepository;
        this.executionJobRepository = executionJobRepository;
        this.userRepository = userRepository;
        this.executionOrchestrator = executionOrchestrator;
        this.executionRecorder = executionRecorder;
        this.objectMapper = objectMapper;
        this.integrityPolicyResolver = integrityPolicyResolver;
        this.skillProfileService = skillProfileService;
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
                return toInProgressView(existing);
            }
        }

        // Only gates a brand-new attempt — resuming one already in progress (above) or reading a
        // historical one is never blocked, even if the student later removes this skill from their
        // portfolio (spec: don't invalidate history).
        if (!skillProfileService.resolve(userId).isEligibleFor(assessment.getSkill().getId())) {
            throw new ForbiddenActionException("This assessment is not available for your current skill profile.");
        }

        List<PracticalQuestion> questions = questionRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessmentId);
        if (questions.isEmpty()) {
            throw new ConflictException("This assessment has no questions configured yet.");
        }

        User userRef = userRepository.getReferenceById(userId);
        Instant now = Instant.now();
        Instant deadline = now.plusSeconds(assessment.getTimeLimitMinutes() * 60L);
        int totalPoints = questions.stream().mapToInt(PracticalQuestion::getPoints).sum();
        PracticalAttempt attempt = new PracticalAttempt(assessment, userRef, now, deadline, totalPoints);
        attempt = attemptRepository.save(attempt);

        int order = 0;
        for (PracticalQuestion question : questions) {
            attemptQuestionRepository.save(new PracticalAttemptQuestion(attempt, question, order++, question.getPoints()));
        }

        return toInProgressView(attempt);
    }

    @Transactional
    public Object getAttempt(UUID userId, UUID attemptId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
        }
        return attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS ? toInProgressView(attempt) : toResultView(attempt);
    }

    @Transactional
    public PracticalAttemptResponse saveProgress(UUID userId, UUID attemptId, UUID attemptQuestionId, SaveAttemptRequest request) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
        }
        if (attempt.getStatus() != PracticalAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is no longer in progress.");
        }

        PracticalAttemptQuestion attemptQuestion = findOwnAttemptQuestion(userId, attemptId, attemptQuestionId);
        if (request.submissionContent() != null) {
            attemptQuestion.setSubmissionContent(request.submissionContent());
        }
        if (request.selectedLanguage() != null) {
            attemptQuestion.setSelectedLanguage(request.selectedLanguage());
        }
        if (request.submissionLinkUrl() != null) {
            attemptQuestion.setSubmissionLinkUrl(request.submissionLinkUrl());
        }
        if (attemptQuestion.getStatus() == PracticalAttemptQuestionStatus.NOT_ATTEMPTED && hasAnySubmission(attemptQuestion)) {
            attemptQuestion.setStatus(PracticalAttemptQuestionStatus.IN_PROGRESS);
        }
        attemptQuestionRepository.save(attemptQuestion);

        return toInProgressView(attempt);
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
            attempt = gradeAllQuestions(attempt);
        } else if (attempt.getStatus() == PracticalAttemptStatus.UNDER_REVIEW && attempt.getScore() == null) {
            // Safe retry: a prior auto-grading pass may have hit an infrastructure failure
            // (Docker unreachable, etc) on one or more questions and left this UNDER_REVIEW
            // without ever scoring the attempt.
            attempt = gradeAllQuestions(attempt);
        } else if (attempt.getStatus() != PracticalAttemptStatus.UNDER_REVIEW
                && attempt.getStatus() != PracticalAttemptStatus.EVALUATED
                && attempt.getStatus() != PracticalAttemptStatus.EXPIRED
                && attempt.getStatus() != PracticalAttemptStatus.SUBMITTED) {
            throw new ConflictException("This attempt has already been finalized.");
        }
        return toResultView(attempt);
    }

    /**
     * Full test-set (public + hidden) grading for every question, for AUTOMATED CODING/SQL
     * assessments only. No-op (returns the attempt unchanged, still UNDER_REVIEW pending admin
     * review) for MANUAL/HYBRID assessments — {@code evaluationType} is uniform across an
     * assessment's questions (decided at creation time), so this branches once, not per question.
     */
    private PracticalAttempt gradeAllQuestions(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        boolean automatable = assessment.getEvaluationType() == EvaluationType.AUTOMATED
                && (assessment.getPracticalType() == PracticalType.CODING || assessment.getPracticalType() == PracticalType.SQL);
        if (!automatable) {
            return attempt;
        }

        List<PracticalAttemptQuestion> attemptQuestions = attemptQuestionRepository
                .findAllByPracticalAttemptIdOrderByDisplayOrderAsc(attempt.getId());
        boolean anyInfrastructureFailure = false;

        for (PracticalAttemptQuestion attemptQuestion : attemptQuestions) {
            if (attemptQuestion.getPointsEarned() != null) {
                continue; // already resolved on a prior (partial) grading pass
            }
            String sourceCode = attemptQuestion.getSubmissionContent();
            PracticalQuestion question = attemptQuestion.getPracticalQuestion();
            if (sourceCode == null || sourceCode.isBlank()) {
                markUngraded(attemptQuestion, PracticalAttemptQuestionStatus.NOT_ATTEMPTED);
                continue;
            }
            List<PracticalTestCase> testCases = testCaseRepository
                    .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId());
            if (testCases.isEmpty()) {
                markUngraded(attemptQuestion, PracticalAttemptQuestionStatus.NOT_ATTEMPTED);
                continue;
            }

            CodingLanguage language = null;
            PracticalExecutionResult result;
            if (assessment.getPracticalType() == PracticalType.CODING) {
                language = attemptQuestion.getSelectedLanguage();
                if (language == null) {
                    markUngraded(attemptQuestion, PracticalAttemptQuestionStatus.NOT_ATTEMPTED);
                    continue;
                }
                result = executionOrchestrator.runCoding(language, sourceCode, testCases);
            } else {
                boolean ordered = isOrderedSqlComparison(question.getConfigurationJson());
                result = executionOrchestrator.runSql(sourceCode, testCases, ordered);
            }

            executionRecorder.record(attempt, attemptQuestion, ExecutionJobKind.SUBMIT, language, sourceCode, testCases, result);
            markFirstCompilationIfNeeded(attemptQuestion, assessment, result);

            if (result.status().isInfrastructureFailure()) {
                anyInfrastructureFailure = true; // never the student's fault — retryable
                continue;
            }
            applyAutomatedResult(attemptQuestion, result);
            attemptQuestionRepository.save(attemptQuestion);
        }

        if (anyInfrastructureFailure) {
            return attempt; // stays UNDER_REVIEW, unscored, retryable
        }

        int totalEarned = attemptQuestionRepository.findAllByPracticalAttemptIdOrderByDisplayOrderAsc(attempt.getId()).stream()
                .mapToInt(aq -> aq.getPointsEarned() == null ? 0 : aq.getPointsEarned()).sum();
        attempt.setScore(totalEarned);
        attempt.setStatus(PracticalAttemptStatus.SUBMITTED);
        attempt.setEvaluatedAt(Instant.now());
        return attempt;
    }

    private void markUngraded(PracticalAttemptQuestion attemptQuestion, PracticalAttemptQuestionStatus status) {
        attemptQuestion.setPointsEarned(0);
        attemptQuestion.setStatus(status);
        attemptQuestionRepository.save(attemptQuestion);
    }

    private void applyAutomatedResult(PracticalAttemptQuestion attemptQuestion, PracticalExecutionResult result) {
        attemptQuestion.setTestsPassed(result.testsPassed());
        attemptQuestion.setTestsTotal(result.testsTotal());
        switch (result.status()) {
            case COMPILATION_ERROR -> {
                attemptQuestion.setPointsEarned(0);
                attemptQuestion.setStatus(PracticalAttemptQuestionStatus.COMPILE_ERROR);
            }
            case SECURITY_ERROR -> {
                attemptQuestion.setPointsEarned(0);
                attemptQuestion.setStatus(PracticalAttemptQuestionStatus.FAILED);
            }
            case COMPLETED -> {
                int passed = result.testsPassed() == null ? 0 : result.testsPassed();
                int total = result.testsTotal() == null ? 0 : result.testsTotal();
                int earned = total == 0 ? 0 : Math.round(passed * (float) attemptQuestion.getPointsPossible() / total);
                attemptQuestion.setPointsEarned(earned);
                attemptQuestion.setStatus(deriveOutcomeStatus(result, passed, total));
            }
            default -> {
                attemptQuestion.setPointsEarned(0);
                attemptQuestion.setStatus(PracticalAttemptQuestionStatus.FAILED);
            }
        }
    }

    private PracticalAttemptQuestionStatus deriveOutcomeStatus(PracticalExecutionResult result, int passed, int total) {
        if (total == 0) {
            return PracticalAttemptQuestionStatus.FAILED;
        }
        if (passed == total) {
            return PracticalAttemptQuestionStatus.PASSED;
        }
        if (passed == 0 && !result.testResults().isEmpty()) {
            if (result.testResults().stream().allMatch(o -> o.status() == TestOutcomeStatus.TIMEOUT)) {
                return PracticalAttemptQuestionStatus.TIME_LIMIT;
            }
            if (result.testResults().stream().allMatch(o -> o.status() == TestOutcomeStatus.MEMORY_LIMIT)) {
                return PracticalAttemptQuestionStatus.MEMORY_LIMIT;
            }
        }
        return passed == 0 ? PracticalAttemptQuestionStatus.FAILED : PracticalAttemptQuestionStatus.PARTIAL;
    }

    @Transactional
    public RunResultResponse run(UUID userId, UUID attemptId, UUID attemptQuestionId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (attempt.getStatus() != PracticalAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is no longer in progress.");
        }
        PracticalAttemptQuestion attemptQuestion = findOwnAttemptQuestion(userId, attemptId, attemptQuestionId);
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        PracticalQuestion question = attemptQuestion.getPracticalQuestion();
        PracticalType type = assessment.getPracticalType();
        if (type != PracticalType.CODING && type != PracticalType.SQL) {
            throw new InvalidRequestException("Run isn't available for this assessment type");
        }

        String sourceCode = attemptQuestion.getSubmissionContent();
        if (sourceCode == null || sourceCode.isBlank()) {
            throw new InvalidRequestException(type == PracticalType.SQL ? "Write a query first." : "Write some code first.");
        }

        List<PracticalTestCase> publicTestCases = testCaseRepository
                .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId()).stream()
                .filter(tc -> !tc.isHidden())
                .toList();

        CodingLanguage language = null;
        PracticalExecutionResult result;
        if (type == PracticalType.CODING) {
            language = attemptQuestion.getSelectedLanguage();
            if (language == null) {
                throw new InvalidRequestException("Select a language first.");
            }
            result = executionOrchestrator.runCoding(language, sourceCode, publicTestCases);
        } else {
            boolean ordered = isOrderedSqlComparison(question.getConfigurationJson());
            result = executionOrchestrator.runSql(sourceCode, publicTestCases, ordered);
        }

        executionRecorder.record(attempt, attemptQuestion, ExecutionJobKind.RUN, language, sourceCode, publicTestCases, result);
        markFirstCompilationIfNeeded(attemptQuestion, assessment, result);

        return toRunResponse(result, publicTestCases, type);
    }

    @Transactional(readOnly = true)
    public List<ExecutionJobSummaryResponse> executionHistory(UUID userId, UUID attemptId, UUID attemptQuestionId) {
        PracticalAttemptQuestion attemptQuestion = findOwnAttemptQuestion(userId, attemptId, attemptQuestionId);
        return executionJobRepository.findAllByPracticalAttemptQuestionIdOrderByCreatedAtAsc(attemptQuestion.getId()).stream()
                .map(ExecutionJobSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PracticalPageResponse<MyPracticalAttemptSummaryResponse> myAttempts(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size <= 0 ? 20 : size, 100));
        return PracticalPageResponse.of(
                attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId, pageable).map(MyPracticalAttemptSummaryResponse::from));
    }

    private boolean hasAnySubmission(PracticalAttemptQuestion attemptQuestion) {
        return (attemptQuestion.getSubmissionContent() != null && !attemptQuestion.getSubmissionContent().isBlank())
                || (attemptQuestion.getSubmissionLinkUrl() != null && !attemptQuestion.getSubmissionLinkUrl().isBlank());
    }

    private void markFirstCompilationIfNeeded(PracticalAttemptQuestion attemptQuestion, PracticalAssessment assessment,
            PracticalExecutionResult result) {
        if (assessment.getPracticalType() != PracticalType.CODING) {
            return;
        }
        if (attemptQuestion.getFirstSuccessfulCompilationAt() != null) {
            return;
        }
        if (result.status() == ExecutionJobStatus.COMPLETED) {
            attemptQuestion.setFirstSuccessfulCompilationAt(Instant.now());
            attemptQuestionRepository.save(attemptQuestion);
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
    private PracticalAttemptResponse toInProgressView(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        Long remaining = attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS
                ? Math.max(0, attempt.getDeadline().getEpochSecond() - Instant.now().getEpochSecond())
                : 0L;

        List<PracticalAttemptQuestion> attemptQuestions = attemptQuestionRepository
                .findAllByPracticalAttemptIdOrderByDisplayOrderAsc(attempt.getId());
        List<PracticalAttemptQuestionResponse> questionViews = attemptQuestions.stream().map(aq -> {
            PracticalQuestion question = aq.getPracticalQuestion();
            List<PracticalCodingLanguageResponse> languages = languageRepository
                    .findAllByPracticalQuestionIdOrderByLanguageAsc(question.getId()).stream()
                    .map(PracticalCodingLanguageResponse::from).toList();
            List<StudentTestCaseView> publicTestCases = testCaseRepository
                    .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId()).stream()
                    .filter(tc -> !tc.isHidden())
                    .map(StudentTestCaseView::from).toList();
            return new PracticalAttemptQuestionResponse(aq.getId(), question.getId(), question.getTitle(),
                    aq.getDisplayOrder(), question.getPoints(), aq.getStatus(), aq.getSubmissionContent(),
                    aq.getSelectedLanguage(), aq.getSubmissionLinkUrl(), aq.getSubmissionFileUrl(),
                    question.getInstructions(), question.getRequirements(), question.getConstraints(),
                    question.getConfigurationJson(), languages, publicTestCases);
        }).toList();

        return new PracticalAttemptResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), assessment.getWorkspaceType(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getDeadline(), remaining, questionViews, integrityPolicyResolver.resolve(assessment.getConfigurationJson()));
    }

    private PracticalAttemptResultResponse toResultView(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        List<PracticalAttemptQuestion> attemptQuestions = attemptQuestionRepository
                .findAllByPracticalAttemptIdOrderByDisplayOrderAsc(attempt.getId());

        List<PracticalAttemptQuestionResultResponse> questionResults = attemptQuestions.stream().map(aq -> {
            PracticalQuestion question = aq.getPracticalQuestion();
            List<RubricScoreView> rubricScores = rubricScoreRepository
                    .findAllByAttemptIdAndQuestionId(attempt.getId(), question.getId()).stream()
                    .map(s -> new RubricScoreView(s.getRubricCriterion().getId(), s.getRubricCriterion().getCriterion(),
                            s.getRubricCriterion().getMaxPoints(), s.getPointsAwarded()))
                    .toList();
            return new PracticalAttemptQuestionResultResponse(question.getId(), question.getTitle(), aq.getPointsPossible(),
                    aq.getPointsEarned(), aq.getTestsPassed(), aq.getTestsTotal(), aq.getStatus(), aq.getFeedback(), rubricScores);
        }).toList();

        return new PracticalAttemptResultResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), assessment.getDifficulty(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), attempt.getEvaluatedAt(), attempt.getScore(), attempt.getMaxScore(),
                attempt.getFeedback(), questionResults, computeSkillPerformance(assessment, attemptQuestions));
    }

    // Spec §15 — grouped by each question's effective skill (its own override, or the assessment's
    // skill when unset), summed server-side from PracticalAttemptQuestion rows.
    private List<SkillPerformanceView> computeSkillPerformance(PracticalAssessment assessment,
            List<PracticalAttemptQuestion> attemptQuestions) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, int[]> totals = new LinkedHashMap<>();
        for (PracticalAttemptQuestion aq : attemptQuestions) {
            PracticalQuestion question = aq.getPracticalQuestion();
            Skill skill = question.getSkill() != null ? question.getSkill() : assessment.getSkill();
            names.putIfAbsent(skill.getId(), skill.getName());
            int[] totalsForSkill = totals.computeIfAbsent(skill.getId(), k -> new int[2]);
            totalsForSkill[0] += aq.getPointsEarned() == null ? 0 : aq.getPointsEarned();
            totalsForSkill[1] += aq.getPointsPossible();
        }
        List<SkillPerformanceView> result = new ArrayList<>();
        for (Map.Entry<UUID, int[]> entry : totals.entrySet()) {
            int earned = entry.getValue()[0];
            int possible = entry.getValue()[1];
            int percentage = possible == 0 ? 0 : Math.round(earned * 100f / possible);
            result.add(new SkillPerformanceView(entry.getKey(), names.get(entry.getKey()), earned, possible, percentage));
        }
        return result;
    }

    private PracticalAttempt findOwnAttempt(UUID userId, UUID attemptId) {
        return attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Practical attempt not found"));
    }

    private PracticalAttemptQuestion findOwnAttemptQuestion(UUID userId, UUID attemptId, UUID attemptQuestionId) {
        return attemptQuestionRepository.findByIdAndAttemptIdAndUserId(attemptQuestionId, attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Practical attempt question not found"));
    }
}
