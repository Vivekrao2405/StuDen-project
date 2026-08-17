package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.notification.NotificationType;
import com.studen.notification.Notifier;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin evaluation queue + manual grading for practical attempts. Assumes ADMIN already checked
 * by {@code @PreAuthorize} on the controller (same posture as every other admin service in this
 * codebase).
 */
@Service
public class AdminPracticalAttemptService {

    private final PracticalAttemptRepository attemptRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final PracticalRubricCriterionRepository rubricRepository;
    private final PracticalRubricScoreRepository rubricScoreRepository;
    private final UserRepository userRepository;
    private final Notifier notifier;

    public AdminPracticalAttemptService(PracticalAttemptRepository attemptRepository,
            PracticalTestCaseRepository testCaseRepository, PracticalRubricCriterionRepository rubricRepository,
            PracticalRubricScoreRepository rubricScoreRepository, UserRepository userRepository, Notifier notifier) {
        this.attemptRepository = attemptRepository;
        this.testCaseRepository = testCaseRepository;
        this.rubricRepository = rubricRepository;
        this.rubricScoreRepository = rubricScoreRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
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

        PracticalAssessment assessment = attempt.getPracticalAssessment();
        List<PracticalRubricCriterion> criteria = rubricRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());

        int finalScore;
        if (!criteria.isEmpty()) {
            if (request.rubricScores() == null || request.rubricScores().isEmpty()) {
                throw new InvalidRequestException("This assessment uses a rubric — rubric scores are required");
            }
            Map<UUID, PracticalRubricCriterion> criteriaById = criteria.stream()
                    .collect(java.util.stream.Collectors.toMap(PracticalRubricCriterion::getId, c -> c));
            int total = 0;
            for (EvaluateAttemptRequest.RubricScoreEntry entry : request.rubricScores()) {
                PracticalRubricCriterion criterion = criteriaById.get(entry.criterionId());
                if (criterion == null) {
                    throw new InvalidRequestException("One or more rubric criteria don't belong to this assessment");
                }
                if (entry.points() < 0 || entry.points() > criterion.getMaxPoints()) {
                    throw new InvalidRequestException(
                            "Points for \"" + criterion.getCriterion() + "\" must be between 0 and " + criterion.getMaxPoints());
                }
                rubricScoreRepository.save(new PracticalRubricScore(attempt, criterion, entry.points()));
                total += entry.points();
            }
            // Server-computed from what was just persisted — the frontend's own displayed total,
            // if any, is never trusted (spec §22).
            finalScore = total;
        } else {
            if (request.score() == null || request.score() < 0 || request.score() > 100) {
                throw new InvalidRequestException("A score between 0 and 100 is required for this assessment");
            }
            finalScore = request.score();
        }

        attempt.setScore(finalScore);
        attempt.setFeedback(blankToNull(request.feedback()));
        attempt.setStatus(PracticalAttemptStatus.EVALUATED);
        attempt.setEvaluatedAt(Instant.now());
        attempt.setEvaluatedBy(findUser(adminId));

        notifier.notify(attempt.getUser().getId(), NotificationType.PRACTICAL_ASSESSMENT_EVALUATED,
                "Your \"" + assessment.getTitle() + "\" practical assessment has been evaluated.", attempt.getId());

        return toDetailResponse(attempt);
    }

    private AdminPracticalAttemptDetailResponse toDetailResponse(PracticalAttempt attempt) {
        PracticalAssessment assessment = attempt.getPracticalAssessment();
        List<PracticalTestCaseResponse> testCases = testCaseRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId()).stream()
                .map(PracticalTestCaseResponse::from).toList();
        List<PracticalRubricCriterion> criteriaEntities = rubricRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        List<PracticalRubricCriterionResponse> criteria = criteriaEntities.stream()
                .map(PracticalRubricCriterionResponse::from).toList();
        Map<UUID, PracticalRubricCriterion> criteriaById = criteriaEntities.stream()
                .collect(java.util.stream.Collectors.toMap(PracticalRubricCriterion::getId, c -> c));
        List<RubricScoreView> rubricScores = rubricScoreRepository.findAllByPracticalAttemptId(attempt.getId()).stream()
                .map(s -> new RubricScoreView(s.getRubricCriterion().getId(),
                        criteriaById.containsKey(s.getRubricCriterion().getId())
                                ? criteriaById.get(s.getRubricCriterion().getId()).getCriterion() : s.getRubricCriterion().getCriterion(),
                        s.getRubricCriterion().getMaxPoints(), s.getPointsAwarded()))
                .toList();

        return new AdminPracticalAttemptDetailResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), attempt.getUser().getId(), attempt.getUser().getFullName(),
                attempt.getStatus(), attempt.getStartedAt(), attempt.getDeadline(), attempt.getSubmittedAt(),
                attempt.getEvaluatedAt(), attempt.getScore(), attempt.getMaxScore(), attempt.getFeedback(),
                attempt.getSelectedLanguage(), attempt.getSubmissionContent(), attempt.getSubmissionFileUrl(),
                attempt.getSubmissionLinkUrl(), testCases, criteria, rubricScores);
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
