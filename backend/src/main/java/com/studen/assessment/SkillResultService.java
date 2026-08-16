package com.studen.assessment;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.skill.Skill;
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
 * Phase 7.3: turns a completed {@link Assessment}'s already-frozen score into a skill level, a
 * per-topic breakdown, and a deterministic strong/weak summary. Reuses Phase 7.2's assessment data
 * as-is (no duplicated storage, spec §29) — {@code correctCount}/{@code scorePercentage} are read
 * directly off the {@link Assessment} row rather than recomputed, matching spec §4's "do not
 * recalculate the assessment differently"; only the topic-level breakdown is derived here, since
 * Phase 7.2 never persisted one.
 */
@Service
public class SkillResultService {

    private static final String GENERAL_TOPIC_NAME = "General";
    private static final List<AssessmentStatus> TERMINAL_STATUSES = List.of(AssessmentStatus.SUBMITTED,
            AssessmentStatus.EXPIRED);

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;
    private final AssessmentQuestionOptionRepository assessmentQuestionOptionRepository;
    private final AssessmentAnswerRepository assessmentAnswerRepository;
    private final ScoringProperties scoringProperties;

    public SkillResultService(AssessmentRepository assessmentRepository,
            AssessmentQuestionRepository assessmentQuestionRepository,
            AssessmentQuestionOptionRepository assessmentQuestionOptionRepository,
            AssessmentAnswerRepository assessmentAnswerRepository, ScoringProperties scoringProperties) {
        this.assessmentRepository = assessmentRepository;
        this.assessmentQuestionRepository = assessmentQuestionRepository;
        this.assessmentQuestionOptionRepository = assessmentQuestionOptionRepository;
        this.assessmentAnswerRepository = assessmentAnswerRepository;
        this.scoringProperties = scoringProperties;
    }

    // IDOR-guarded the same way as every other learner-facing lookup in this package: a bare
    // findById is never used, so another student's assessment 404s instead of leaking (spec §11).
    @Transactional(readOnly = true)
    public AssessmentResultSummaryResponse getResult(UUID userId, UUID assessmentId) {
        Assessment assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
        if (assessment.getStatus() == AssessmentStatus.IN_PROGRESS) {
            // Final scoring must never be visible before submission (spec §10) — this is checked
            // server-side regardless of what the frontend route guards do.
            throw new ConflictException("This assessment has not been submitted yet.");
        }
        return buildSummary(assessment);
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentResultSummaryResponse> latestForUser(UUID userId) {
        List<Assessment> latest = assessmentRepository.findLatestByUserAndStatusIn(userId, TERMINAL_STATUSES,
                PageRequest.of(0, 1));
        return latest.isEmpty() ? Optional.empty() : Optional.of(buildSummary(latest.get(0)));
    }

    @Transactional(readOnly = true)
    public Optional<AssessmentResultSummaryResponse> latestForSkill(UUID userId, UUID skillId) {
        List<Assessment> latest = assessmentRepository.findLatestByUserAndSkillAndStatusIn(userId, skillId,
                TERMINAL_STATUSES, PageRequest.of(0, 1));
        return latest.isEmpty() ? Optional.empty() : Optional.of(buildSummary(latest.get(0)));
    }

    private AssessmentResultSummaryResponse buildSummary(Assessment assessment) {
        UUID assessmentId = assessment.getId();
        List<AssessmentQuestion> questions = assessmentQuestionRepository
                .findAllByAssessmentIdOrderByDisplayOrderAsc(assessmentId);
        List<UUID> aqIds = questions.stream().map(AssessmentQuestion::getId).toList();
        Map<UUID, List<AssessmentQuestionOption>> optionsByAQ = aqIds.isEmpty() ? Map.of()
                : assessmentQuestionOptionRepository.findAllByAssessmentQuestionIdInOrderByDisplayOrderAsc(aqIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getAssessmentQuestion().getId()));
        Map<UUID, AssessmentAnswer> answerByAQ = assessmentAnswerRepository.findAllByAssessmentId(assessmentId).stream()
                .collect(Collectors.toMap(a -> a.getAssessmentQuestion().getId(), a -> a));

        // LinkedHashMap: null is a valid key here (the "no topic assigned" bucket) and insertion
        // order gives a stable-ish base ordering before the final sort-by-name below.
        Map<UUID, int[]> countsByTopicId = new LinkedHashMap<>();
        Map<UUID, String> nameByTopicId = new LinkedHashMap<>();

        for (AssessmentQuestion aq : questions) {
            List<AssessmentQuestionOption> options = optionsByAQ.getOrDefault(aq.getId(), List.of());
            Set<UUID> correctOptionIds = options.stream()
                    .filter(o -> o.getQuestionOption().isCorrect())
                    .map(o -> o.getQuestionOption().getId())
                    .collect(Collectors.toSet());
            AssessmentAnswer answer = answerByAQ.get(aq.getId());
            Set<UUID> selected = answer == null ? Set.of() : answer.getSelectedOptionIds();
            boolean correct = !selected.isEmpty() && selected.equals(correctOptionIds);

            UUID topicId = aq.getTopicId();
            String topicName = topicId == null ? GENERAL_TOPIC_NAME
                    : (aq.getTopicName() != null ? aq.getTopicName() : GENERAL_TOPIC_NAME);
            nameByTopicId.putIfAbsent(topicId, topicName);
            int[] agg = countsByTopicId.computeIfAbsent(topicId, k -> new int[2]);
            agg[1]++;
            if (correct) {
                agg[0]++;
            }
        }

        List<TopicPerformanceView> topicPerformance = countsByTopicId.entrySet().stream()
                .map(entry -> {
                    int correct = entry.getValue()[0];
                    int total = entry.getValue()[1];
                    int percentage = total == 0 ? 0 : Math.round(correct * 100f / total);
                    return new TopicPerformanceView(entry.getKey(), nameByTopicId.get(entry.getKey()), correct, total,
                            percentage, scoringProperties.topicTierFor(percentage));
                })
                .sorted((a, b) -> a.topicName().compareToIgnoreCase(b.topicName()))
                .toList();

        int totalQuestions = assessment.getTotalQuestions();
        int correctCount = assessment.getCorrectCount();
        int incorrectCount = totalQuestions - correctCount;
        int scorePercentage = assessment.getScorePercentage();
        AssessmentLevel level = scoringProperties.levelFor(scorePercentage);

        List<String> strongTopics = topicPerformance.stream()
                .filter(t -> t.tier() == TopicPerformanceTier.STRONG)
                .map(TopicPerformanceView::topicName)
                .toList();
        List<String> needsImprovementTopics = topicPerformance.stream()
                .filter(t -> t.tier() == TopicPerformanceTier.NEEDS_IMPROVEMENT)
                .map(TopicPerformanceView::topicName)
                .toList();
        PerformanceSummaryView summary = new PerformanceSummaryView(scorePercentage, level, strongTopics,
                needsImprovementTopics);

        Skill skill = assessment.getSkill();
        return new AssessmentResultSummaryResponse(assessment.getId(), skill.getId(), skill.getName(),
                assessment.getStatus(), totalQuestions, correctCount, incorrectCount, scorePercentage, level,
                topicPerformance, summary, assessment.getStartedAt(), assessment.getSubmittedAt());
    }
}
