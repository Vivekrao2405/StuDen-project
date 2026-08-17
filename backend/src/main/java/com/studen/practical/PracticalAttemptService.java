package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.judge.CodeExecutionService;
import com.studen.practical.judge.CodeJudgeResult;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the student-facing practical-attempt lifecycle: start/resume, autosave, deadline
 * expiry, submit, and the honest "run" stub. Every learner-facing lookup is scoped by
 * {@code userId} via {@link PracticalAttemptRepository#findByIdAndUserId} — never a bare
 * {@code findById} — mirroring {@code com.studen.assessment.AssessmentService} exactly (spec §31).
 */
@Service
public class PracticalAttemptService {

    private static final int DEFAULT_MAX_SCORE = 100;

    private final PracticalAttemptRepository attemptRepository;
    private final PracticalAssessmentRepository assessmentRepository;
    private final UserRepository userRepository;
    private final CodeExecutionService codeExecutionService;

    public PracticalAttemptService(PracticalAttemptRepository attemptRepository,
            PracticalAssessmentRepository assessmentRepository, UserRepository userRepository,
            CodeExecutionService codeExecutionService) {
        this.attemptRepository = attemptRepository;
        this.assessmentRepository = assessmentRepository;
        this.userRepository = userRepository;
        this.codeExecutionService = codeExecutionService;
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
            // trusts stored state (spec §26), mirroring AssessmentService.submit exactly.
            attemptRepository.transitionIfInProgress(attemptId, PracticalAttemptStatus.UNDER_REVIEW, Instant.now());
            attempt = attemptRepository.findByIdAndUserId(attemptId, userId).orElseThrow();
        } else if (attempt.getStatus() != PracticalAttemptStatus.UNDER_REVIEW
                && attempt.getStatus() != PracticalAttemptStatus.EVALUATED
                && attempt.getStatus() != PracticalAttemptStatus.EXPIRED) {
            throw new ConflictException("This attempt has already been finalized.");
        }
        return toResultView(attempt);
    }

    @Transactional(readOnly = true)
    public RunResultResponse run(UUID userId, UUID attemptId) {
        PracticalAttempt attempt = findOwnAttempt(userId, attemptId);
        if (attempt.getStatus() != PracticalAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is no longer in progress.");
        }
        PracticalType type = attempt.getPracticalAssessment().getPracticalType();
        if (type != PracticalType.CODING && type != PracticalType.SQL) {
            throw new InvalidRequestException("Run isn't available for this assessment type");
        }

        CodingLanguage language = attempt.getSelectedLanguage();
        String code = attempt.getSubmissionContent();
        CodeJudgeResult result = codeExecutionService.evaluate(language, code, List.of());
        return RunResultResponse.from(result);
    }

    @Transactional(readOnly = true)
    public PracticalPageResponse<MyPracticalAttemptSummaryResponse> myAttempts(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size <= 0 ? 20 : size, 100));
        return PracticalPageResponse.of(
                attemptRepository.findAllByUserIdOrderByStartedAtDesc(userId, pageable).map(MyPracticalAttemptSummaryResponse::from));
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

    private PracticalAttemptResponse toInProgressView(PracticalAttempt attempt, PracticalAssessment assessment) {
        Long remaining = attempt.getStatus() == PracticalAttemptStatus.IN_PROGRESS
                ? Math.max(0, attempt.getDeadline().getEpochSecond() - Instant.now().getEpochSecond())
                : 0L;
        return new PracticalAttemptResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getPracticalType(), assessment.getWorkspaceType(), attempt.getStatus(), attempt.getStartedAt(),
                attempt.getDeadline(), remaining, attempt.getSubmissionContent(), attempt.getSelectedLanguage(),
                attempt.getSubmissionLinkUrl(), attempt.getSubmissionFileUrl());
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
