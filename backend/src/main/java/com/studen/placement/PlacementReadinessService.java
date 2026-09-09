package com.studen.placement;

import com.studen.assessment.AssessmentLevel;
import com.studen.assessment.ScoringProperties;
import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.QuestionOption;
import com.studen.questionbank.QuestionOptionRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a completed {@link PlacementAttempt}'s already-frozen per-question data into a skill
 * breakdown and a ranked list of priority skill gaps, and answers "what's available / what's my
 * latest result" for the Placement Readiness entry screen.
 *
 * <p>Gap ranking (spec: "based on Role-required skill, Student performance, Required proficiency
 * where configured, RoleSkill priority, RoleSkill weight") is a strict, documented, multi-key
 * sort — never "lowest score wins":
 * <ol>
 *   <li>status severity: CRITICAL, then IMPROVE, then a GOOD/STRONG skill that still fails its
 *       configured {@code RoleSkill.requiredProficiency}</li>
 *   <li>{@code RoleSkill.priority} ascending (1 = highest priority, per its own javadoc)</li>
 *   <li>{@code RoleSkill.weight} descending (heavier-weighted skills first among equal priority)</li>
 *   <li>measured percentage ascending, as a final deterministic tie-break</li>
 * </ol>
 * A skill with no RoleSkill mapping for this role (possible since {@code addQuestion} does not
 * enforce one) falls back to the same defaults {@code RoleSkill} itself uses for an unconfigured
 * mapping (weight 1, lowest priority) rather than being silently dropped from the breakdown.
 */
@Service
public class PlacementReadinessService {

    private static final List<PlacementAttemptStatus> TERMINAL_STATUSES =
            List.of(PlacementAttemptStatus.SUBMITTED, PlacementAttemptStatus.EXPIRED);

    private final PlacementProfileRepository profileRepository;
    private final PlacementAssessmentRepository assessmentRepository;
    private final PlacementAttemptRepository attemptRepository;
    private final PlacementAttemptQuestionRepository attemptQuestionRepository;
    private final PlacementAttemptAnswerRepository attemptAnswerRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final PlacementRoleRepository roleRepository;
    private final PlacementScoringProperties placementScoringProperties;
    private final ScoringProperties assessmentScoringProperties;

    public PlacementReadinessService(PlacementProfileRepository profileRepository,
            PlacementAssessmentRepository assessmentRepository, PlacementAttemptRepository attemptRepository,
            PlacementAttemptQuestionRepository attemptQuestionRepository,
            PlacementAttemptAnswerRepository attemptAnswerRepository, QuestionOptionRepository questionOptionRepository,
            PlacementRoleRepository roleRepository, PlacementScoringProperties placementScoringProperties,
            ScoringProperties assessmentScoringProperties) {
        this.profileRepository = profileRepository;
        this.assessmentRepository = assessmentRepository;
        this.attemptRepository = attemptRepository;
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.roleRepository = roleRepository;
        this.placementScoringProperties = placementScoringProperties;
        this.assessmentScoringProperties = assessmentScoringProperties;
    }

    @Transactional(readOnly = true)
    public PlacementReadinessStatusResponse getStatus(UUID studentId) {
        Optional<PlacementProfile> profileOpt = profileRepository.findByUserId(studentId);
        if (profileOpt.isEmpty()) {
            return new PlacementReadinessStatusResponse(PlacementReadinessState.NO_PROFILE, null, null, null, null,
                    null, 0, null, null);
        }
        PlacementRole role = profileOpt.get().getTargetRole();

        Optional<PlacementAssessment> assessmentOpt = role.getStatus() == PlacementCatalogStatus.ACTIVE
                ? assessmentRepository.findFirstByRoleIdAndStatusOrderByUpdatedAtDesc(role.getId(),
                        PlacementContentStatus.PUBLISHED)
                : Optional.empty();
        if (assessmentOpt.isEmpty()) {
            return new PlacementReadinessStatusResponse(PlacementReadinessState.NO_ASSESSMENT_AVAILABLE, role.getId(),
                    role.getName(), null, null, null, 0, null, null);
        }

        PlacementAssessment assessment = assessmentOpt.get();
        List<IdCountView> counts = assessmentRepository.countQuestionsByAssessment(List.of(assessment.getId()));
        int questionCount = counts.isEmpty() ? 0 : (int) counts.get(0).count();

        UUID inProgressAttemptId = attemptRepository
                .findByStudentIdAndPlacementAssessmentIdAndStatus(studentId, assessment.getId(),
                        PlacementAttemptStatus.IN_PROGRESS)
                .map(PlacementAttempt::getId)
                .orElse(null);

        PlacementReadinessResultResponse latest = latestForStudent(studentId).orElse(null);

        return new PlacementReadinessStatusResponse(PlacementReadinessState.ASSESSMENT_AVAILABLE, role.getId(),
                role.getName(), assessment.getId(), assessment.getTitle(), assessment.getDurationMinutes(),
                questionCount, inProgressAttemptId, latest);
    }

    // IDOR-guarded the same way as every other learner-facing lookup here: a bare findById is
    // never used, so another student's attempt 404s instead of leaking.
    @Transactional(readOnly = true)
    public PlacementReadinessResultResponse getResult(UUID studentId, UUID attemptId) {
        PlacementAttempt attempt = attemptRepository.findByIdAndStudentId(attemptId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attempt not found"));
        if (attempt.getStatus() == PlacementAttemptStatus.IN_PROGRESS) {
            throw new ConflictException("This attempt has not been submitted yet.");
        }
        return buildResult(attempt);
    }

    @Transactional(readOnly = true)
    public Optional<PlacementReadinessResultResponse> latestForStudent(UUID studentId) {
        List<PlacementAttempt> latest = attemptRepository.findLatestByStudentAndStatusIn(studentId, TERMINAL_STATUSES,
                PageRequest.of(0, 1));
        return latest.isEmpty() ? Optional.empty() : Optional.of(buildResult(latest.get(0)));
    }

    private PlacementReadinessResultResponse buildResult(PlacementAttempt attempt) {
        UUID attemptId = attempt.getId();
        List<PlacementAttemptQuestion> questions = attemptQuestionRepository
                .findAllByAttemptIdOrderByDisplayOrderAsc(attemptId);
        List<UUID> questionIds = questions.stream().map(aq -> aq.getQuestion().getId()).distinct().toList();
        Map<UUID, List<QuestionOption>> optionsByQuestion = questionIds.isEmpty() ? Map.of()
                : questionOptionRepository.findAllByQuestionIdInOrderByDisplayOrderAsc(questionIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));
        Map<UUID, PlacementAttemptAnswer> answerByAttemptQuestion = attemptAnswerRepository
                .findAllByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getAttemptQuestion().getId(), a -> a));

        Map<UUID, int[]> countsBySkill = new LinkedHashMap<>();
        Map<UUID, String> nameBySkill = new LinkedHashMap<>();
        for (PlacementAttemptQuestion aq : questions) {
            Set<UUID> correctIds = correctOptionIds(optionsByQuestion.getOrDefault(aq.getQuestion().getId(), List.of()));
            Set<UUID> selected = selectedOptionIds(answerByAttemptQuestion.get(aq.getId()));
            boolean correct = isCorrect(selected, correctIds);
            UUID skillId = aq.getSkill().getId();
            nameBySkill.putIfAbsent(skillId, aq.getSkillName());
            int[] counts = countsBySkill.computeIfAbsent(skillId, k -> new int[2]);
            counts[1]++;
            if (correct) {
                counts[0]++;
            }
        }

        UUID roleId = attempt.getPlacementAssessment().getRole().getId();
        Map<UUID, RoleSkill> roleSkillBySkillId = roleRepository.findRoleSkills(roleId).stream()
                .collect(Collectors.toMap(rs -> rs.getSkill().getId(), rs -> rs));

        List<PlacementSkillBreakdownView> breakdown = new ArrayList<>();
        List<GapCandidate> gapCandidates = new ArrayList<>();
        for (Map.Entry<UUID, int[]> entry : countsBySkill.entrySet()) {
            UUID skillId = entry.getKey();
            int correct = entry.getValue()[0];
            int total = entry.getValue()[1];
            int percentage = total == 0 ? 0 : Math.round(correct * 100f / total);
            SkillReadinessStatus status = placementScoringProperties.statusFor(percentage);
            RoleSkill roleSkill = roleSkillBySkillId.get(skillId);
            AssessmentLevel requiredProficiency = roleSkill == null ? null : roleSkill.getRequiredProficiency();
            boolean meetsRequired = requiredProficiency == null
                    || assessmentScoringProperties.levelFor(percentage).ordinal() >= requiredProficiency.ordinal();

            breakdown.add(new PlacementSkillBreakdownView(skillId, nameBySkill.get(skillId), correct, total,
                    percentage, status, requiredProficiency, meetsRequired));

            boolean isGap = status != SkillReadinessStatus.STRONG || !meetsRequired;
            if (isGap) {
                int priority = roleSkill == null ? Integer.MAX_VALUE : roleSkill.getPriority();
                int weight = roleSkill == null ? 1 : roleSkill.getWeight();
                gapCandidates.add(new GapCandidate(skillId, nameBySkill.get(skillId), percentage, status, priority, weight));
            }
        }
        breakdown.sort(Comparator.comparing(PlacementSkillBreakdownView::skillName, String.CASE_INSENSITIVE_ORDER));

        gapCandidates.sort(Comparator
                .comparingInt((GapCandidate g) -> statusSeverityRank(g.status()))
                .thenComparingInt(GapCandidate::priority)
                .thenComparing(Comparator.comparingInt(GapCandidate::weight).reversed())
                .thenComparingInt(GapCandidate::percentage));

        List<PlacementSkillGapView> gaps = new ArrayList<>();
        int rank = 1;
        for (GapCandidate g : gapCandidates) {
            int displayPriority = g.priority() == Integer.MAX_VALUE ? 0 : g.priority();
            gaps.add(new PlacementSkillGapView(rank++, g.skillId(), g.skillName(), g.percentage(), g.status(),
                    displayPriority, g.weight()));
        }

        PlacementAssessment assessment = attempt.getPlacementAssessment();
        int correctCount = attempt.getScore() == null ? 0 : attempt.getScore();
        int scorePercentage = attempt.getScorePercentage() == null ? 0 : attempt.getScorePercentage();
        return new PlacementReadinessResultResponse(attempt.getId(), assessment.getId(), assessment.getTitle(),
                assessment.getRole().getId(), assessment.getRole().getName(), attempt.getStatus(), questions.size(),
                correctCount, scorePercentage, breakdown, gaps, attempt.getStartedAt(), attempt.getSubmittedAt());
    }

    private static int statusSeverityRank(SkillReadinessStatus status) {
        return switch (status) {
            case CRITICAL -> 0;
            case IMPROVE -> 1;
            case GOOD -> 2;
            case STRONG -> 3;
        };
    }

    private static Set<UUID> correctOptionIds(List<QuestionOption> options) {
        return options.stream()
                .filter(QuestionOption::isCorrect)
                .map(QuestionOption::getId)
                .collect(Collectors.toSet());
    }

    private static Set<UUID> selectedOptionIds(PlacementAttemptAnswer answer) {
        return answer == null ? Set.of() : answer.getSelectedOptionIds();
    }

    private static boolean isCorrect(Set<UUID> selected, Set<UUID> correctOptionIds) {
        return !selected.isEmpty() && selected.equals(correctOptionIds);
    }

    private record GapCandidate(UUID skillId, String skillName, int percentage, SkillReadinessStatus status,
            int priority, int weight) {
    }
}
