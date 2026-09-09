package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionOption;
import com.studen.questionbank.QuestionOptionRepository;
import com.studen.questionbank.QuestionType;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the whole readiness-attempt lifecycle: start/resume, autosave, expiry and
 * submission. Deliberately mirrors {@code com.studen.assessment.AssessmentService} question for
 * question — same expiry/finalize/scoring idioms — with one structural difference: the question
 * set is admin-configured per role ({@link PlacementAssessmentQuestion}), not randomly selected per
 * attempt, so {@code startOrResume} snapshots that configuration into
 * {@link PlacementAttemptQuestion} rows rather than calling a selection service.
 *
 * <p>Every learner-facing lookup is scoped by {@code studentId} via
 * {@link PlacementAttemptRepository#findByIdAndStudentId} — never a bare {@code findById} — so
 * cross-student access 404s instead of leaking another student's attempt.
 */
@Service
public class PlacementAttemptService {

    private final PlacementAttemptRepository attemptRepository;
    private final PlacementAttemptQuestionRepository attemptQuestionRepository;
    private final PlacementAttemptAnswerRepository attemptAnswerRepository;
    private final PlacementAssessmentRepository assessmentRepository;
    private final PlacementProfileRepository profileRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final PlacementSkillScoreRepository skillScoreRepository;
    private final UserRepository userRepository;

    public PlacementAttemptService(PlacementAttemptRepository attemptRepository,
            PlacementAttemptQuestionRepository attemptQuestionRepository,
            PlacementAttemptAnswerRepository attemptAnswerRepository, PlacementAssessmentRepository assessmentRepository,
            PlacementProfileRepository profileRepository, QuestionOptionRepository questionOptionRepository,
            PlacementSkillScoreRepository skillScoreRepository, UserRepository userRepository) {
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.assessmentRepository = assessmentRepository;
        this.profileRepository = profileRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PlacementAttemptSummaryResponse> listMyAttempts(UUID studentId) {
        return attemptRepository.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                .map(PlacementAttemptSummaryResponse::from)
                .toList();
    }

    @Transactional
    public PlacementAttemptDetailResponse startOrResume(UUID studentId) {
        PlacementAssessment assessment = resolveAssessmentForStudent(studentId);

        PlacementAttempt existing = attemptRepository
                .findByStudentIdAndPlacementAssessmentIdAndStatus(studentId, assessment.getId(),
                        PlacementAttemptStatus.IN_PROGRESS)
                .orElse(null);
        if (existing != null) {
            if (expireIfDue(existing)) {
                existing = attemptRepository.findByIdAndStudentId(existing.getId(), studentId).orElse(null);
            }
            if (existing != null && existing.getStatus() == PlacementAttemptStatus.IN_PROGRESS) {
                return buildInProgressView(existing);
            }
        }

        PlacementAssessment full = assessmentRepository.findByIdWithQuestions(assessment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
        if (full.getQuestions().isEmpty()) {
            throw new ConflictException("This readiness assessment has no configured questions yet.");
        }

        User studentRef = userRepository.getReferenceById(studentId);
        PlacementAttempt attempt = new PlacementAttempt(studentRef, full, Instant.now());
        attempt = attemptRepository.save(attempt);

        int order = 0;
        for (PlacementAssessmentQuestion link : full.getQuestions()) {
            Question question = link.getQuestion();
            PlacementAttemptQuestion aq = new PlacementAttemptQuestion(attempt, question, question.getSkill(),
                    question.getSkill().getName(), order++, link.getPoints());
            attemptQuestionRepository.save(aq);
        }

        return buildInProgressView(attempt);
    }

    // Resolves the PUBLISHED readiness assessment for the student's own target role — the single
    // source of "which assessment is available", shared by startOrResume and
    // PlacementReadinessService.getStatus so the two can never disagree.
    @Transactional(readOnly = true)
    PlacementAssessment resolveAssessmentForStudent(UUID studentId) {
        PlacementProfile profile = profileRepository.findByUserId(studentId)
                .orElseThrow(() -> new ConflictException("Complete your placement profile before taking a readiness assessment."));
        PlacementRole role = profile.getTargetRole();
        if (role.getStatus() != PlacementCatalogStatus.ACTIVE) {
            throw new ConflictException("Your target role is no longer available.");
        }
        return assessmentRepository.findFirstByRoleIdAndStatusOrderByUpdatedAtDesc(role.getId(),
                        PlacementContentStatus.PUBLISHED)
                .orElseThrow(() -> new ConflictException("A readiness assessment isn't available for your target role yet."));
    }

    @Transactional
    public Object getAttempt(UUID studentId, UUID attemptId) {
        PlacementAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        if (expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow();
        }
        return attempt.getStatus() == PlacementAttemptStatus.IN_PROGRESS ? buildInProgressView(attempt)
                : buildReviewView(attempt);
    }

    @Transactional
    public PlacementAnswerResponse saveAnswer(UUID studentId, UUID attemptId, UUID attemptQuestionId,
            List<UUID> selectedOptionIds) {
        PlacementAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        if (expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow();
        }
        if (attempt.getStatus() != PlacementAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt is no longer in progress.");
        }

        PlacementAttemptQuestion aq = attemptQuestionRepository.findByIdAndAttemptId(attemptQuestionId, attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found in this attempt"));

        Set<UUID> validOptionIds = questionOptionRepository
                .findAllByQuestionIdOrderByDisplayOrderAsc(aq.getQuestion().getId()).stream()
                .map(QuestionOption::getId)
                .collect(Collectors.toSet());

        Set<UUID> selected = new LinkedHashSet<>(selectedOptionIds);
        if (selected.isEmpty()) {
            throw new InvalidRequestException("At least one option must be selected.");
        }
        if (selected.size() != selectedOptionIds.size()) {
            throw new InvalidRequestException("Duplicate option selected.");
        }
        if (!validOptionIds.containsAll(selected)) {
            throw new InvalidRequestException("One or more selected options are invalid for this question.");
        }
        QuestionType type = aq.getQuestion().getQuestionType();
        if ((type == QuestionType.MCQ_SINGLE || type == QuestionType.TRUE_FALSE) && selected.size() != 1) {
            throw new InvalidRequestException("This question requires exactly one selected option.");
        }

        PlacementAttempt attemptRef = attempt;
        PlacementAttemptAnswer answer = attemptAnswerRepository.findByAttemptQuestionId(attemptQuestionId)
                .orElseGet(() -> new PlacementAttemptAnswer(attemptRef, aq));
        answer.setSelectedOptionIds(selected);
        answer.setAnsweredAt(Instant.now());
        attemptAnswerRepository.save(answer);

        return new PlacementAnswerResponse(attemptQuestionId, List.copyOf(selected), answer.getAnsweredAt());
    }

    @Transactional
    public PlacementAttemptReviewResponse submit(UUID studentId, UUID attemptId) {
        PlacementAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));

        if (attempt.getStatus() == PlacementAttemptStatus.IN_PROGRESS && expireIfDue(attempt)) {
            attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow();
        }
        if (attempt.getStatus() == PlacementAttemptStatus.IN_PROGRESS) {
            // A second, racing/double-click submit finds this already flipped to SUBMITTED and its
            // own finalizeIfInProgress affects 0 rows — harmless, every caller re-fetches and
            // returns whatever the stored result actually is.
            finalizeAttempt(attempt.getId(), PlacementAttemptStatus.SUBMITTED);
            attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow();
        }
        return buildReviewView(attempt);
    }

    // Returns true if this call actually transitioned the attempt to EXPIRED (caller must re-fetch).
    private boolean expireIfDue(PlacementAttempt attempt) {
        Integer durationMinutes = attempt.getPlacementAssessment().getDurationMinutes();
        if (attempt.getStatus() != PlacementAttemptStatus.IN_PROGRESS || durationMinutes == null) {
            return false;
        }
        Instant deadline = attempt.getStartedAt().plusSeconds(durationMinutes * 60L);
        if (Instant.now().isBefore(deadline)) {
            return false;
        }
        finalizeAttempt(attempt.getId(), PlacementAttemptStatus.EXPIRED);
        return true;
    }

    // Shared by submit() and the lazy expiry check. score/maxScore/scorePercentage are plain
    // correct/total counts — the same "existing assessment scoring convention" AssessmentService
    // uses (per-question `points` exists for assessment configuration but, matching that
    // precedent, is not folded into the percentage). Also persists a PlacementSkillScore per skill
    // measured on this attempt, inside the same transaction, so a skill breakdown is never left
    // half-recorded.
    private void finalizeAttempt(UUID attemptId, PlacementAttemptStatus targetStatus) {
        LoadedAttemptData data = loadAttemptData(attemptId);
        int correct = 0;
        Map<UUID, int[]> countsBySkill = new LinkedHashMap<>();
        for (PlacementAttemptQuestion aq : data.questions()) {
            Set<UUID> correctIds = correctOptionIds(data.optionsByQuestion().getOrDefault(aq.getQuestion().getId(), List.of()));
            Set<UUID> selected = selectedOptionIds(data.answerByAttemptQuestion().get(aq.getId()));
            boolean isCorrect = isCorrect(selected, correctIds);
            if (isCorrect) {
                correct++;
            }
            int[] counts = countsBySkill.computeIfAbsent(aq.getSkill().getId(), k -> new int[2]);
            counts[1]++;
            if (isCorrect) {
                counts[0]++;
            }
        }
        int total = data.questions().size();
        int scorePercentage = total == 0 ? 0 : Math.round(correct * 100f / total);
        int rows = attemptRepository.finalizeIfInProgress(attemptId, targetStatus, Instant.now(), correct, total,
                scorePercentage);
        if (rows > 0) {
            recordSkillScores(attemptId, data, countsBySkill);
        }
    }

    private void recordSkillScores(UUID attemptId, LoadedAttemptData data, Map<UUID, int[]> countsBySkill) {
        PlacementAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        Map<UUID, PlacementAttemptQuestion> firstQuestionBySkill = new LinkedHashMap<>();
        for (PlacementAttemptQuestion aq : data.questions()) {
            firstQuestionBySkill.putIfAbsent(aq.getSkill().getId(), aq);
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, int[]> entry : countsBySkill.entrySet()) {
            int correct = entry.getValue()[0];
            int total = entry.getValue()[1];
            int percentage = total == 0 ? 0 : Math.round(correct * 100f / total);
            PlacementAttemptQuestion sample = firstQuestionBySkill.get(entry.getKey());
            PlacementSkillScore score = new PlacementSkillScore(attempt.getStudent(), sample.getSkill(), attempt,
                    percentage, now);
            score.setCorrectCount(correct);
            score.setTotalQuestions(total);
            skillScoreRepository.save(score);
        }
    }

    private PlacementAttemptDetailResponse buildInProgressView(PlacementAttempt attempt) {
        LoadedAttemptData data = loadAttemptData(attempt.getId());
        List<PlacementAttemptQuestionView> views = data.questions().stream().map(aq -> {
            List<QuestionOption> options = data.optionsByQuestion().getOrDefault(aq.getQuestion().getId(), List.of());
            List<UUID> selected = List.copyOf(selectedOptionIds(data.answerByAttemptQuestion().get(aq.getId())));
            return new PlacementAttemptQuestionView(aq.getId(), aq.getQuestion().getQuestionText(),
                    aq.getQuestion().getQuestionType(), aq.getQuestion().getDifficulty(), aq.getSkill().getId(),
                    aq.getSkillName(), aq.getDisplayOrder(), aq.getPoints(),
                    options.stream().map(PlacementAttemptOptionView::from).toList(), selected);
        }).toList();

        PlacementAssessment assessment = attempt.getPlacementAssessment();
        Integer timeLimitSeconds = assessment.getDurationMinutes() == null ? null : assessment.getDurationMinutes() * 60;
        return new PlacementAttemptDetailResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getRole().getId(), assessment.getRole().getName(), attempt.getStatus(),
                data.questions().size(), attempt.getStartedAt(), timeLimitSeconds, remainingSeconds(attempt), views);
    }

    private PlacementAttemptReviewResponse buildReviewView(PlacementAttempt attempt) {
        LoadedAttemptData data = loadAttemptData(attempt.getId());
        List<PlacementAttemptResultQuestionView> views = data.questions().stream().map(aq -> {
            List<QuestionOption> options = data.optionsByQuestion().getOrDefault(aq.getQuestion().getId(), List.of());
            Set<UUID> correctIds = correctOptionIds(options);
            List<UUID> selected = List.copyOf(selectedOptionIds(data.answerByAttemptQuestion().get(aq.getId())));
            return new PlacementAttemptResultQuestionView(aq.getId(), aq.getQuestion().getQuestionText(),
                    aq.getQuestion().getQuestionType(), aq.getQuestion().getDifficulty(), aq.getSkill().getId(),
                    aq.getSkillName(), aq.getDisplayOrder(), aq.getPoints(),
                    options.stream().map(PlacementAttemptResultOptionView::from).toList(), selected,
                    List.copyOf(correctIds), isCorrect(Set.copyOf(selected), correctIds), aq.getQuestion().getExplanation());
        }).toList();

        PlacementAssessment assessment = attempt.getPlacementAssessment();
        return new PlacementAttemptReviewResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getRole().getId(), assessment.getRole().getName(), attempt.getStatus(),
                data.questions().size(), attempt.getScore(), attempt.getScorePercentage(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), views);
    }

    private LoadedAttemptData loadAttemptData(UUID attemptId) {
        List<PlacementAttemptQuestion> questions = attemptQuestionRepository
                .findAllByAttemptIdOrderByDisplayOrderAsc(attemptId);
        List<UUID> questionIds = questions.stream().map(aq -> aq.getQuestion().getId()).distinct().toList();
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionIds.isEmpty() ? Map.of()
                : questionOptionRepository.findAllByQuestionIdInOrderByDisplayOrderAsc(questionIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));
        Map<UUID, PlacementAttemptAnswer> answerByAttemptQuestion = attemptAnswerRepository
                .findAllByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getAttemptQuestion().getId(), a -> a));
        return new LoadedAttemptData(questions, optionsByQuestion, answerByAttemptQuestion);
    }

    private static Set<UUID> correctOptionIds(List<QuestionOption> options) {
        return options.stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<UUID> selectedOptionIds(PlacementAttemptAnswer answer) {
        return answer == null ? Set.of() : answer.getSelectedOptionIds();
    }

    // Uniform correctness rule for MCQ_SINGLE/TRUE_FALSE/MCQ_MULTIPLE alike: exact set equality —
    // same rule AssessmentService applies, giving MCQ_MULTIPLE all-or-nothing scoring for free.
    private static boolean isCorrect(Set<UUID> selected, Set<UUID> correctOptionIds) {
        return !selected.isEmpty() && selected.equals(correctOptionIds);
    }

    private Integer remainingSeconds(PlacementAttempt attempt) {
        Integer durationMinutes = attempt.getPlacementAssessment().getDurationMinutes();
        if (durationMinutes == null) {
            return null;
        }
        if (attempt.getStatus() != PlacementAttemptStatus.IN_PROGRESS) {
            return 0;
        }
        long elapsed = Instant.now().getEpochSecond() - attempt.getStartedAt().getEpochSecond();
        return (int) Math.max(durationMinutes * 60L - elapsed, 0);
    }

    private record LoadedAttemptData(List<PlacementAttemptQuestion> questions,
            Map<UUID, List<QuestionOption>> optionsByQuestion, Map<UUID, PlacementAttemptAnswer> answerByAttemptQuestion) {
    }
}
