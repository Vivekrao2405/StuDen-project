package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.integrity.IntegritySummaryFactory;
import com.studen.notification.NotificationType;
import com.studen.notification.Notifier;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin evaluation queue + manual grading for practical attempts. Assumes ADMIN already checked
 * by {@code @PreAuthorize} on the controller (same posture as every other admin service in this
 * codebase).
 *
 * <p>Phase 7.6: {@link #evaluate} now scores every {@link PracticalAttemptQuestion} on the attempt
 * in one action (mirrors the pre-7.6 single-question form, just looped) — the request must cover
 * every question or it's rejected, so an attempt can never end up with some questions silently
 * unscored.
 */
@Service
public class AdminPracticalAttemptService {

    private final PracticalAttemptRepository attemptRepository;
    private final PracticalAttemptQuestionRepository attemptQuestionRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final PracticalRubricCriterionRepository rubricRepository;
    private final PracticalRubricScoreRepository rubricScoreRepository;
    private final ExecutionJobRepository executionJobRepository;
    private final UserRepository userRepository;
    private final Notifier notifier;
    private final IntegritySummaryFactory integritySummaryFactory;

    public AdminPracticalAttemptService(PracticalAttemptRepository attemptRepository,
            PracticalAttemptQuestionRepository attemptQuestionRepository, PracticalTestCaseRepository testCaseRepository,
            PracticalRubricCriterionRepository rubricRepository, PracticalRubricScoreRepository rubricScoreRepository,
            ExecutionJobRepository executionJobRepository, UserRepository userRepository, Notifier notifier,
            IntegritySummaryFactory integritySummaryFactory) {
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.testCaseRepository = testCaseRepository;
        this.rubricRepository = rubricRepository;
        this.rubricScoreRepository = rubricScoreRepository;
        this.executionJobRepository = executionJobRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.integritySummaryFactory = integritySummaryFactory;
    }

    @Transactional(readOnly = true)
    public PracticalPageResponse<AdminPracticalAttemptSummaryResponse> queue(PracticalAttemptStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size <= 0 ? 20 : size, 100));
        PracticalAttemptStatus effectiveStatus = status == null ? PracticalAttemptStatus.UNDER_REVIEW : status;
        return PracticalPageResponse.of(
                attemptRepository.findAllByStatusOrderBySubmittedAtAsc(effectiveStatus, pageable)
                        .map(AdminPracticalAttemptSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminPracticalAttemptDetailResponse get(UUID id) {
        return toDetailResponse(findAttempt(id));
    }

    @Transactional
    public AdminPracticalAttemptDetailResponse evaluate(UUID id, UUID adminId, EvaluateAttemptRequest request) {
        PracticalAttempt attempt = findAttempt(id);
        if (attempt.getStatus() == PracticalAttemptStatus.EVALUATED) {
            throw new ConflictException("This attempt has already been evaluated");
        }
        if (attempt.getStatus() != PracticalAttemptStatus.UNDER_REVIEW) {
            throw new ConflictException("Only an attempt awaiting review can be evaluated");
        }

        List<PracticalAttemptQuestion> attemptQuestions = attemptQuestionRepository
                .findAllByPracticalAttemptIdOrderByDisplayOrderAsc(id);
        Map<UUID, PracticalAttemptQuestion> byId = attemptQuestions.stream()
                .collect(Collectors.toMap(PracticalAttemptQuestion::getId, q -> q));

        Set<UUID> requiredIds = byId.keySet();
        Set<UUID> providedIds = request.questions().stream()
                .map(EvaluateAttemptRequest.QuestionEvaluationEntry::attemptQuestionId).collect(Collectors.toSet());
        if (!providedIds.equals(requiredIds)) {
            throw new InvalidRequestException("An evaluation is required for every question in this attempt");
        }

        int totalEarned = 0;
        for (EvaluateAttemptRequest.QuestionEvaluationEntry entry : request.questions()) {
            PracticalAttemptQuestion attemptQuestion = byId.get(entry.attemptQuestionId());
            PracticalQuestion question = attemptQuestion.getPracticalQuestion();
            List<PracticalRubricCriterion> criteria = rubricRepository
                    .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId());

            int earned;
            if (!criteria.isEmpty()) {
                if (entry.rubricScores() == null || entry.rubricScores().isEmpty()) {
                    throw new InvalidRequestException("\"" + question.getTitle() + "\" uses a rubric — rubric scores are required");
                }
                Map<UUID, PracticalRubricCriterion> criteriaById = criteria.stream()
                        .collect(Collectors.toMap(PracticalRubricCriterion::getId, c -> c));
                int rubricTotal = 0;
                for (EvaluateAttemptRequest.QuestionEvaluationEntry.RubricScoreEntry rubricEntry : entry.rubricScores()) {
                    PracticalRubricCriterion criterion = criteriaById.get(rubricEntry.criterionId());
                    if (criterion == null) {
                        throw new InvalidRequestException(
                                "One or more rubric criteria don't belong to \"" + question.getTitle() + "\"");
                    }
                    if (rubricEntry.points() < 0 || rubricEntry.points() > criterion.getMaxPoints()) {
                        throw new InvalidRequestException("Points for \"" + criterion.getCriterion() + "\" must be between 0 and "
                                + criterion.getMaxPoints());
                    }
                    rubricScoreRepository.save(new PracticalRubricScore(attempt, criterion, rubricEntry.points()));
                    rubricTotal += rubricEntry.points();
                }
                // Criteria always sum to 100 (enforced at publish time) — scale that 0-100 rubric
                // total onto the question's actual point value. Server-computed, never trusted from
                // a client-supplied total (spec §22).
                earned = Math.round(rubricTotal * (float) attemptQuestion.getPointsPossible() / 100f);
            } else {
                if (entry.score() == null || entry.score() < 0 || entry.score() > attemptQuestion.getPointsPossible()) {
                    throw new InvalidRequestException("A score between 0 and " + attemptQuestion.getPointsPossible()
                            + " is required for \"" + question.getTitle() + "\"");
                }
                earned = entry.score();
            }

            attemptQuestion.setPointsEarned(earned);
            attemptQuestion.setFeedback(blankToNull(entry.feedback()));
            attemptQuestion.setStatus(PracticalAttemptQuestionStatus.EVALUATED);
            attemptQuestionRepository.save(attemptQuestion);
            totalEarned += earned;
        }

        attempt.setScore(totalEarned);
        attempt.setFeedback(blankToNull(request.feedback()));
        attempt.setStatus(PracticalAttemptStatus.EVALUATED);
        attempt.setEvaluatedAt(Instant.now());
        attempt.setEvaluatedBy(findUser(adminId));

        notifier.notify(attempt.getUser().getId(), NotificationType.PRACTICAL_ASSESSMENT_EVALUATED,
                "Your \"" + attempt.getPracticalAssessment().getTitle() + "\" practical assessment has been evaluated.",
                attempt.getId());

        return toDetailResponse(attempt);
    }

    private AdminPracticalAttemptDetailResponse toDetailResponse(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        List<PracticalAttemptQuestion> attemptQuestions = attemptQuestionRepository
                .findAllByPracticalAttemptIdOrderByDisplayOrderAsc(attempt.getId());

        List<AdminAttemptQuestionDetailResponse> questionDetails = attemptQuestions.stream().map(aq -> {
            PracticalQuestion question = aq.getPracticalQuestion();
            List<PracticalTestCaseResponse> testCases = testCaseRepository
                    .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId()).stream()
                    .map(PracticalTestCaseResponse::from).toList();
            List<PracticalRubricCriterion> criteriaEntities = rubricRepository
                    .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId());
            List<PracticalRubricCriterionResponse> criteria = criteriaEntities.stream()
                    .map(PracticalRubricCriterionResponse::from).toList();
            List<RubricScoreView> rubricScores = rubricScoreRepository
                    .findAllByAttemptIdAndQuestionId(attempt.getId(), question.getId()).stream()
                    .map(s -> new RubricScoreView(s.getRubricCriterion().getId(), s.getRubricCriterion().getCriterion(),
                            s.getRubricCriterion().getMaxPoints(), s.getPointsAwarded()))
                    .toList();
            List<ExecutionJobSummaryResponse> executionHistory = executionJobRepository
                    .findAllByPracticalAttemptQuestionIdOrderByCreatedAtAsc(aq.getId()).stream()
                    .map(ExecutionJobSummaryResponse::from).toList();

            return new AdminAttemptQuestionDetailResponse(aq.getId(), question.getId(), question.getTitle(),
                    aq.getPointsPossible(), aq.getPointsEarned(), aq.getTestsPassed(), aq.getTestsTotal(), aq.getStatus(),
                    aq.getFeedback(), aq.getSelectedLanguage(), aq.getSubmissionContent(), aq.getSubmissionFileUrl(),
                    aq.getSubmissionLinkUrl(), testCases, criteria, rubricScores, executionHistory);
        }).toList();

        return new AdminPracticalAttemptDetailResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), attempt.getUser().getId(), attempt.getUser().getFullName(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getDeadline(), attempt.getSubmittedAt(),
                attempt.getEvaluatedAt(), attempt.getScore(), attempt.getMaxScore(), attempt.getFeedback(),
                questionDetails, integritySummaryFactory.build(attempt));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private PracticalAttempt findAttempt(UUID id) {
        return attemptRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Practical attempt not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
