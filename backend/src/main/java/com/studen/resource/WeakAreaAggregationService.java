package com.studen.resource;

import com.studen.assessment.AssessmentResultSummaryResponse;
import com.studen.assessment.ScoringProperties;
import com.studen.assessment.SkillResultService;
import com.studen.assessment.TopicPerformanceTier;
import com.studen.assessment.TopicPerformanceView;
import com.studen.portfolio.PortfolioSkillProfileService;
import com.studen.portfolio.StudentSkillProfile;
import com.studen.practical.PracticalAttempt;
import com.studen.practical.PracticalAttemptQuestion;
import com.studen.practical.PracticalAttemptQuestionRepository;
import com.studen.practical.PracticalAttemptRepository;
import com.studen.practical.PracticalQuestion;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a student's existing MCQ ({@link SkillResultService}) and practical
 * ({@link PracticalAttemptRepository}) performance data into a single ordered list of weak areas
 * — the input to {@link ResourceMatchingService}. Purely additive/read-only: never edits
 * {@code SkillResultService}/{@code PracticalAttemptService}'s own behavior or contracts, only
 * reads their already-public outputs (plus one small, locally-reimplemented aggregation for the
 * practical side, since {@code PracticalAttemptService.computeSkillPerformance} is private).
 *
 * <p>Load-bearing constraint: {@code PracticalQuestion} carries no tags (only MCQ {@code Question}
 * does), so a practical-sourced weak area can only ever be skill-scoped
 * ({@code WeakAreaView.tagScoped=false}), never tag-scoped — {@link ResourceMatchingService} has
 * to honor that honestly rather than pretending practical has tag-level data it doesn't.
 */
@Service
public class WeakAreaAggregationService {

    private final PortfolioSkillProfileService skillProfileService;
    private final SkillResultService skillResultService;
    private final SkillRepository skillRepository;
    private final PracticalAttemptRepository practicalAttemptRepository;
    private final PracticalAttemptQuestionRepository practicalAttemptQuestionRepository;
    private final ScoringProperties scoringProperties;

    public WeakAreaAggregationService(PortfolioSkillProfileService skillProfileService,
            SkillResultService skillResultService, SkillRepository skillRepository,
            PracticalAttemptRepository practicalAttemptRepository,
            PracticalAttemptQuestionRepository practicalAttemptQuestionRepository, ScoringProperties scoringProperties) {
        this.skillProfileService = skillProfileService;
        this.skillResultService = skillResultService;
        this.skillRepository = skillRepository;
        this.practicalAttemptRepository = practicalAttemptRepository;
        this.practicalAttemptQuestionRepository = practicalAttemptQuestionRepository;
        this.scoringProperties = scoringProperties;
    }

    @Transactional(readOnly = true)
    public List<WeakAreaView> resolveWeakAreas(UUID userId) {
        StudentSkillProfile profile = skillProfileService.resolve(userId);
        if (!profile.hasPortfolio() || !profile.hasSkills()) {
            return List.of();
        }

        List<Skill> skills = skillRepository.findAllById(profile.skillIds()).stream()
                .sorted(Comparator.comparing(Skill::getName))
                .toList();

        List<WeakAreaView> weakAreas = new ArrayList<>();
        for (Skill skill : skills) {
            weakAreas.addAll(mcqWeakAreas(userId, skill));
            practicalWeakArea(userId, skill).ifPresent(weakAreas::add);
        }
        return weakAreas;
    }

    // "Have I completed an assessment for this skill" count — a portfolio skill counts once if it
    // has a latest MCQ result OR a latest evaluated practical attempt (mirrors the two lookups
    // resolveWeakAreas already does per skill). Not a raw attempt-history count — no such count is
    // exposed anywhere else in the app, and Phase 7.3's multi-attempt history only ever exposes
    // "latest per skill" lookups (see SkillResultService/PracticalAttemptRepository).
    @Transactional(readOnly = true)
    public int countCompletedAssessments(UUID userId) {
        StudentSkillProfile profile = skillProfileService.resolve(userId);
        if (!profile.hasPortfolio() || !profile.hasSkills()) {
            return 0;
        }
        List<Skill> skills = skillRepository.findAllById(profile.skillIds());
        int count = 0;
        for (Skill skill : skills) {
            boolean mcqCompleted = skillResultService.latestForSkill(userId, skill.getId()).isPresent();
            boolean practicalCompleted = !practicalAttemptRepository
                    .findLatestEvaluatedByUserAndSkill(userId, skill.getId(), PageRequest.of(0, 1)).isEmpty();
            if (mcqCompleted || practicalCompleted) {
                count++;
            }
        }
        return count;
    }

    private List<WeakAreaView> mcqWeakAreas(UUID userId, Skill skill) {
        Optional<AssessmentResultSummaryResponse> result = skillResultService.latestForSkill(userId, skill.getId());
        if (result.isEmpty()) {
            return List.of();
        }
        AssessmentResultSummaryResponse summary = result.get();
        List<String> needsImprovement = summary.summary().needsImprovementTopics();
        if (needsImprovement.isEmpty()) {
            return List.of();
        }
        List<WeakAreaView> areas = new ArrayList<>();
        for (String topicName : needsImprovement) {
            int percentage = summary.topicPerformance().stream()
                    .filter(t -> t.topicName().equals(topicName))
                    .map(TopicPerformanceView::percentage)
                    .findFirst()
                    .orElse(summary.scorePercentage());
            areas.add(new WeakAreaView(skill.getId(), skill.getName(), topicName, true, percentage));
        }
        return areas;
    }

    // Reimplements PracticalAttemptService.computeSkillPerformance's effective-skill grouping rule
    // locally (that method is private, so it can't be called directly) — sums pointsEarned/
    // pointsPossible over the student's latest evaluated/submitted attempt for this skill, then
    // classifies via ScoringProperties.topicTierFor (the first use of that class outside the
    // knowledge-assessment package — reused rather than re-deriving thresholds).
    private Optional<WeakAreaView> practicalWeakArea(UUID userId, Skill skill) {
        List<PracticalAttempt> latest = practicalAttemptRepository.findLatestEvaluatedByUserAndSkill(userId,
                skill.getId(), PageRequest.of(0, 1));
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        PracticalAttempt attempt = latest.get(0);
        List<PracticalAttemptQuestion> attemptQuestions = practicalAttemptQuestionRepository
                .findAllByPracticalAttemptIdOrderByDisplayOrderAsc(attempt.getId());

        int earned = 0;
        int possible = 0;
        for (PracticalAttemptQuestion aq : attemptQuestions) {
            PracticalQuestion question = aq.getPracticalQuestion();
            Skill effectiveSkill = question.getSkill() != null ? question.getSkill()
                    : attempt.getPracticalAssessment().getSkill();
            if (!effectiveSkill.getId().equals(skill.getId())) {
                continue;
            }
            earned += aq.getPointsEarned() == null ? 0 : aq.getPointsEarned();
            possible += aq.getPointsPossible();
        }
        if (possible == 0) {
            return Optional.empty();
        }
        int percentage = Math.round(earned * 100f / possible);
        if (scoringProperties.topicTierFor(percentage) != TopicPerformanceTier.NEEDS_IMPROVEMENT) {
            return Optional.empty();
        }
        return Optional.of(new WeakAreaView(skill.getId(), skill.getName(), skill.getName(), false, percentage));
    }
}
