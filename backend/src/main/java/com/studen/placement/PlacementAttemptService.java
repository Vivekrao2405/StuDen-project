package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptRepository;
import com.studen.practical.PracticalAttemptResponse;
import com.studen.practical.PracticalAttemptService;
import com.studen.practical.PracticalAttemptStatus;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionOption;
import com.studen.questionbank.QuestionOptionRepository;
import com.studen.questionbank.QuestionType;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
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
 * submission — for both MCQ (QUESTION) slots and practical/coding (PRACTICAL_ASSESSMENT) slots.
 * Mirrors {@code com.studen.assessment.AssessmentService}'s question/expiry/finalize idioms for the
 * QUESTION path exactly. A PRACTICAL_ASSESSMENT slot never re-implements coding execution or
 * scoring: it delegates entirely to the existing {@link PracticalAttemptService} — this class only
 * starts/tracks which {@link PracticalAttempt} belongs to which slot and reads its score once
 * terminal.
 *
 * <p>Every learner-facing lookup is scoped by {@code studentId} via
 * {@link PlacementAttemptRepository#findByIdAndStudentId} — never a bare {@code findById} — so
 * cross-student access 404s instead of leaking another student's attempt.
 */
@Service
public class PlacementAttemptService {

    // A practical slot only contributes a real score to the overall/skill percentage once its
    // linked PracticalAttempt is one of these — EVALUATED/SUBMITTED always carry a score (auto- or
    // admin-graded); EXPIRED may not (treated as 0, the same "unanswered counts as incorrect"
    // convention the QUESTION path already applies).
    private static final Set<PracticalAttemptStatus> PRACTICAL_TERMINAL_STATUSES =
            Set.of(PracticalAttemptStatus.SUBMITTED, PracticalAttemptStatus.EVALUATED, PracticalAttemptStatus.EXPIRED);

    private final PlacementAttemptRepository attemptRepository;
    private final PlacementAttemptQuestionRepository attemptQuestionRepository;
    private final PlacementAttemptAnswerRepository attemptAnswerRepository;
    private final PlacementAssessmentRepository assessmentRepository;
    private final PlacementProfileRepository profileRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final PlacementSkillScoreRepository skillScoreRepository;
    private final UserRepository userRepository;
    private final PracticalAttemptService practicalAttemptService;
    private final PracticalAttemptRepository practicalAttemptRepository;

    public PlacementAttemptService(PlacementAttemptRepository attemptRepository,
            PlacementAttemptQuestionRepository attemptQuestionRepository,
            PlacementAttemptAnswerRepository attemptAnswerRepository, PlacementAssessmentRepository assessmentRepository,
            PlacementProfileRepository profileRepository, QuestionOptionRepository questionOptionRepository,
            PlacementSkillScoreRepository skillScoreRepository, UserRepository userRepository,
            PracticalAttemptService practicalAttemptService, PracticalAttemptRepository practicalAttemptRepository) {
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.assessmentRepository = assessmentRepository;
        this.profileRepository = profileRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.skillScoreRepository = skillScoreRepository;
        this.userRepository = userRepository;
        this.practicalAttemptService = practicalAttemptService;
        this.practicalAttemptRepository = practicalAttemptRepository;
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
            var skill = link.resolveSkill();
            PlacementAttemptQuestion aq;
            if (link.getItemType() == ModuleItemType.QUESTION) {
                aq = new PlacementAttemptQuestion(attempt, link.getQuestion(), skill, skill.getName(), order++,
                        link.getPoints());
            } else {
                // Delegates entirely to the existing practical engine: this call creates (or
                // resumes, though a brand-new placement attempt never has one yet) the real
                // PracticalAttempt the student will actually work in.
                PracticalAttemptResponse practicalAttempt =
                        practicalAttemptService.startOrResume(studentId, link.getPracticalAssessment().getId());
                aq = new PlacementAttemptQuestion(attempt, link.getPracticalAssessment(), skill, skill.getName(),
                        order++, link.getPoints());
                aq.setPracticalAttempt(practicalAttemptRepository.getReferenceById(practicalAttempt.id()));
            }
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
        if (aq.getItemType() != ModuleItemType.QUESTION) {
            throw new InvalidRequestException(
                    "This is a practical task — complete it in its own workspace, not by selecting an option.");
        }

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
            requireAllPracticalSlotsTerminal(attemptId);
            // A second, racing/double-click submit finds this already flipped to SUBMITTED and its
            // own finalizeIfInProgress affects 0 rows — harmless, every caller re-fetches and
            // returns whatever the stored result actually is.
            finalizeAttempt(attempt.getId(), PlacementAttemptStatus.SUBMITTED);
            attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId).orElseThrow();
        }
        return buildReviewView(attempt);
    }

    // A placement attempt can only be finalized once every practical slot's own attempt has
    // reached its terminal state through the existing practical-attempt flow — never auto-
    // submitted on the student's behalf here, since that engine owns its own submit semantics
    // (including auto-grading for AUTOMATED CODING/SQL).
    private void requireAllPracticalSlotsTerminal(UUID attemptId) {
        List<PlacementAttemptQuestion> questions = attemptQuestionRepository
                .findAllByAttemptIdOrderByDisplayOrderAsc(attemptId);
        List<String> unfinished = new ArrayList<>();
        for (PlacementAttemptQuestion aq : questions) {
            if (aq.getItemType() != ModuleItemType.PRACTICAL_ASSESSMENT) {
                continue;
            }
            PracticalAttemptStatus status = aq.getPracticalAttempt() == null ? null
                    : practicalAttemptRepository.findById(aq.getPracticalAttempt().getId())
                            .map(PracticalAttempt::getStatus).orElse(null);
            if (status == null || !PRACTICAL_TERMINAL_STATUSES.contains(status)) {
                unfinished.add(aq.getSkillName());
            }
        }
        if (!unfinished.isEmpty()) {
            throw new ConflictException(
                    "Finish the practical task(s) for " + String.join(", ", unfinished) + " before submitting.");
        }
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

    // Shared by submit() and the lazy expiry check. Each slot contributes a fractional score in
    // [0,1]: a QUESTION slot is 1.0/0.0 (exact-set-equality correctness, unchanged from the
    // MCQ-only cut); a PRACTICAL_ASSESSMENT slot is its linked PracticalAttempt's own
    // score/maxScore, read once terminal — never recomputed, never a hardcoded bonus. Summing
    // fractions instead of counting booleans is a pure generalization: for an all-MCQ attempt the
    // sum is numerically identical to the old integer correctCount.
    private void finalizeAttempt(UUID attemptId, PlacementAttemptStatus targetStatus) {
        LoadedAttemptData data = loadAttemptData(attemptId);
        double totalFraction = 0;
        Map<UUID, double[]> fractionsBySkill = new LinkedHashMap<>();
        for (PlacementAttemptQuestion aq : data.questions()) {
            double fraction = slotFraction(aq, data);
            totalFraction += fraction;
            double[] agg = fractionsBySkill.computeIfAbsent(aq.getSkill().getId(), k -> new double[2]);
            agg[0] += fraction;
            agg[1] += 1;
        }
        int total = data.questions().size();
        int scoreEquivalent = (int) Math.round(totalFraction);
        int scorePercentage = total == 0 ? 0 : (int) Math.round(totalFraction * 100 / total);
        int rows = attemptRepository.finalizeIfInProgress(attemptId, targetStatus, Instant.now(), scoreEquivalent,
                total, scorePercentage);
        if (rows > 0) {
            recordSkillScores(attemptId, data, fractionsBySkill);
        }
    }

    private double slotFraction(PlacementAttemptQuestion aq, LoadedAttemptData data) {
        if (aq.getItemType() == ModuleItemType.QUESTION) {
            Set<UUID> correctIds = correctOptionIds(data.optionsByQuestion().getOrDefault(aq.getQuestion().getId(), List.of()));
            Set<UUID> selected = selectedOptionIds(data.answerByAttemptQuestion().get(aq.getId()));
            return isCorrect(selected, correctIds) ? 1.0 : 0.0;
        }
        if (aq.getPracticalAttempt() == null) {
            return 0.0;
        }
        PracticalAttempt practical = practicalAttemptRepository.findById(aq.getPracticalAttempt().getId()).orElse(null);
        if (practical == null || practical.getScore() == null || practical.getMaxScore() == null
                || practical.getMaxScore() <= 0) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, practical.getScore().doubleValue() / practical.getMaxScore()));
    }

    private void recordSkillScores(UUID attemptId, LoadedAttemptData data, Map<UUID, double[]> fractionsBySkill) {
        PlacementAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        Map<UUID, PlacementAttemptQuestion> firstQuestionBySkill = new LinkedHashMap<>();
        for (PlacementAttemptQuestion aq : data.questions()) {
            firstQuestionBySkill.putIfAbsent(aq.getSkill().getId(), aq);
        }
        Instant now = Instant.now();
        for (Map.Entry<UUID, double[]> entry : fractionsBySkill.entrySet()) {
            double sumFraction = entry.getValue()[0];
            int total = (int) entry.getValue()[1];
            int percentage = total == 0 ? 0 : (int) Math.round(sumFraction * 100 / total);
            PlacementAttemptQuestion sample = firstQuestionBySkill.get(entry.getKey());
            PlacementSkillScore score = new PlacementSkillScore(attempt.getStudent(), sample.getSkill(), attempt,
                    percentage, now);
            score.setCorrectCount((int) Math.round(sumFraction));
            score.setTotalQuestions(total);
            skillScoreRepository.save(score);
        }
    }

    private PlacementAttemptDetailResponse buildInProgressView(PlacementAttempt attempt) {
        LoadedAttemptData data = loadAttemptData(attempt.getId());
        List<PlacementAttemptQuestionView> views = data.questions().stream().map(aq -> toQuestionView(aq, data)).toList();

        PlacementAssessment assessment = attempt.getPlacementAssessment();
        Integer timeLimitSeconds = assessment.getDurationMinutes() == null ? null : assessment.getDurationMinutes() * 60;
        return new PlacementAttemptDetailResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getRole().getId(), assessment.getRole().getName(), attempt.getStatus(),
                data.questions().size(), attempt.getStartedAt(), timeLimitSeconds, remainingSeconds(attempt), views);
    }

    private PlacementAttemptQuestionView toQuestionView(PlacementAttemptQuestion aq, LoadedAttemptData data) {
        if (aq.getItemType() == ModuleItemType.QUESTION) {
            List<QuestionOption> options = data.optionsByQuestion().getOrDefault(aq.getQuestion().getId(), List.of());
            List<UUID> selected = List.copyOf(selectedOptionIds(data.answerByAttemptQuestion().get(aq.getId())));
            return new PlacementAttemptQuestionView(aq.getId(), ModuleItemType.QUESTION, aq.getQuestion().getQuestionText(),
                    aq.getQuestion().getQuestionType(), aq.getQuestion().getDifficulty(),
                    options.stream().map(PlacementAttemptOptionView::from).toList(), selected, null, null, null, null,
                    null, aq.getSkill().getId(), aq.getSkillName(), aq.getDisplayOrder(), aq.getPoints());
        }
        PracticalAttemptStatus practicalStatus = aq.getPracticalAttempt() == null ? null
                : practicalAttemptRepository.findById(aq.getPracticalAttempt().getId())
                        .map(PracticalAttempt::getStatus).orElse(null);
        return new PlacementAttemptQuestionView(aq.getId(), ModuleItemType.PRACTICAL_ASSESSMENT, null, null, null,
                List.of(), List.of(), aq.getPracticalAssessment().getId(), aq.getPracticalAssessment().getTitle(),
                aq.getPracticalAssessment().getPracticalType(),
                aq.getPracticalAttempt() == null ? null : aq.getPracticalAttempt().getId(), practicalStatus,
                aq.getSkill().getId(), aq.getSkillName(), aq.getDisplayOrder(), aq.getPoints());
    }

    private PlacementAttemptReviewResponse buildReviewView(PlacementAttempt attempt) {
        LoadedAttemptData data = loadAttemptData(attempt.getId());
        List<PlacementAttemptResultQuestionView> views = data.questions().stream()
                .map(aq -> toResultQuestionView(aq, data))
                .toList();

        PlacementAssessment assessment = attempt.getPlacementAssessment();
        return new PlacementAttemptReviewResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getRole().getId(), assessment.getRole().getName(), attempt.getStatus(),
                data.questions().size(), attempt.getScore(), attempt.getScorePercentage(), attempt.getStartedAt(),
                attempt.getSubmittedAt(), views);
    }

    private PlacementAttemptResultQuestionView toResultQuestionView(PlacementAttemptQuestion aq, LoadedAttemptData data) {
        if (aq.getItemType() == ModuleItemType.QUESTION) {
            List<QuestionOption> options = data.optionsByQuestion().getOrDefault(aq.getQuestion().getId(), List.of());
            Set<UUID> correctIds = correctOptionIds(options);
            List<UUID> selected = List.copyOf(selectedOptionIds(data.answerByAttemptQuestion().get(aq.getId())));
            return new PlacementAttemptResultQuestionView(aq.getId(), ModuleItemType.QUESTION,
                    aq.getQuestion().getQuestionText(), aq.getQuestion().getQuestionType(), aq.getQuestion().getDifficulty(),
                    options.stream().map(PlacementAttemptResultOptionView::from).toList(), selected,
                    List.copyOf(correctIds), isCorrect(Set.copyOf(selected), correctIds), aq.getQuestion().getExplanation(),
                    null, null, null, null, null, null, aq.getSkill().getId(), aq.getSkillName(), aq.getDisplayOrder(),
                    aq.getPoints());
        }
        PracticalAttempt practical = aq.getPracticalAttempt() == null ? null
                : practicalAttemptRepository.findById(aq.getPracticalAttempt().getId()).orElse(null);
        Integer practicalPercentage = practical == null || practical.getScore() == null || practical.getMaxScore() == null
                || practical.getMaxScore() <= 0 ? null
                : (int) Math.round(practical.getScore().doubleValue() * 100 / practical.getMaxScore());
        return new PlacementAttemptResultQuestionView(aq.getId(), ModuleItemType.PRACTICAL_ASSESSMENT, null, null, null,
                List.of(), List.of(), List.of(), practicalPercentage != null && practicalPercentage >= 100, null,
                aq.getPracticalAssessment().getId(), aq.getPracticalAssessment().getTitle(),
                aq.getPracticalAssessment().getPracticalType(), practical == null ? null : practical.getId(),
                practical == null ? null : practical.getStatus(), practicalPercentage, aq.getSkill().getId(),
                aq.getSkillName(), aq.getDisplayOrder(), aq.getPoints());
    }

    private LoadedAttemptData loadAttemptData(UUID attemptId) {
        List<PlacementAttemptQuestion> questions = attemptQuestionRepository
                .findAllByAttemptIdOrderByDisplayOrderAsc(attemptId);
        List<UUID> questionIds = questions.stream()
                .filter(aq -> aq.getItemType() == ModuleItemType.QUESTION)
                .map(aq -> aq.getQuestion().getId())
                .distinct()
                .toList();
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
