package com.studen.practical;

import com.studen.common.exception.ForbiddenActionException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.integrity.IntegrityPolicyResolver;
import com.studen.portfolio.EligibilityState;
import com.studen.portfolio.PortfolioSkillProfileService;
import com.studen.portfolio.StudentSkillProfile;
import com.studen.questionbank.Difficulty;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student-facing browsing of practical assessments — PUBLISHED only (spec §23), no
 * {@code @PreAuthorize} (standard authentication only, same posture as
 * {@code com.studen.assessment.AssessmentController}). Additionally scoped to the caller's
 * portfolio skills — see {@link PortfolioSkillProfileService} — so a student only ever sees
 * practical assessments for skills actually on their portfolio.
 */
@Service
public class PracticalAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalQuestionRepository questionRepository;
    private final PracticalCodingLanguageRepository languageRepository;
    private final IntegrityPolicyResolver integrityPolicyResolver;
    private final PortfolioSkillProfileService skillProfileService;

    public PracticalAssessmentService(PracticalAssessmentRepository assessmentRepository,
            PracticalQuestionRepository questionRepository, PracticalCodingLanguageRepository languageRepository,
            IntegrityPolicyResolver integrityPolicyResolver, PortfolioSkillProfileService skillProfileService) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.languageRepository = languageRepository;
        this.integrityPolicyResolver = integrityPolicyResolver;
        this.skillProfileService = skillProfileService;
    }

    @Transactional(readOnly = true)
    public PracticalAssessmentListResponse list(UUID userId, UUID skillId, PracticalType practicalType,
            Difficulty difficulty, String search, int page, int size) {
        StudentSkillProfile profile = skillProfileService.resolve(userId);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        if (!profile.hasPortfolio()) {
            return new PracticalAssessmentListResponse(EligibilityState.NO_PORTFOLIO, PracticalPageResponse.empty(pageable));
        }
        if (!profile.hasSkills()) {
            return new PracticalAssessmentListResponse(EligibilityState.NO_SKILLS, PracticalPageResponse.empty(pageable));
        }

        // A caller-supplied skillId narrows within the student's own eligible set — a skillId the
        // student isn't eligible for must never leak whether that assessment exists, so it simply
        // yields zero results instead of an error.
        java.util.Set<UUID> effectiveSkillIds = skillId == null ? profile.skillIds()
                : (profile.isEligibleFor(skillId) ? java.util.Set.of(skillId) : java.util.Set.of());

        String normalizedSearch = search == null ? "" : search.trim();
        PracticalAssessmentListResponse emptyState = new PracticalAssessmentListResponse(
                EligibilityState.NO_MATCHING_ASSESSMENTS, PracticalPageResponse.empty(pageable));
        if (effectiveSkillIds.isEmpty()) {
            return emptyState;
        }

        Page<PracticalAssessment> result = assessmentRepository.searchForSkills(effectiveSkillIds, practicalType,
                difficulty, normalizedSearch, pageable);
        PracticalPageResponse<PracticalAssessmentSummaryResponse> pageResponse = PracticalPageResponse.of(
                result.map(a -> PracticalAssessmentSummaryResponse.from(a, questionCount(a.getId()))));
        EligibilityState state = result.getTotalElements() == 0 ? EligibilityState.NO_MATCHING_ASSESSMENTS
                : EligibilityState.HAS_AVAILABLE_ASSESSMENTS;
        return new PracticalAssessmentListResponse(state, pageResponse);
    }

    @Transactional(readOnly = true)
    public StudentPracticalAssessmentResponse get(UUID userId, UUID id) {
        PracticalAssessment assessment = assessmentRepository.findByIdAndStatus(id, PracticalAssessmentStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));
        // Defense-in-depth against a student bypassing the (already-filtered) listing by navigating
        // directly to an assessment id for a skill they aren't eligible for.
        if (!skillProfileService.resolve(userId).isEligibleFor(assessment.getSkill().getId())) {
            throw new ForbiddenActionException("This assessment is not available for your current skill profile.");
        }
        return toStudentResponse(assessment);
    }

    // Metadata-only builder — see StudentPracticalAssessmentResponse's javadoc for why this must
    // never include problem content. supportedLanguages is the union across every CODING question
    // on the assessment — which languages exist at all, not which starter code they carry.
    private StudentPracticalAssessmentResponse toStudentResponse(PracticalAssessment assessment) {
        List<PracticalQuestion> questions = questionRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        // Union across every CODING question's languages — fine to loop at this list size (a
        // handful of questions per assessment); which languages exist at all, no starter code.
        LinkedHashSet<CodingLanguage> union = new LinkedHashSet<>();
        for (PracticalQuestion question : questions) {
            languageRepository.findAllByPracticalQuestionIdOrderByLanguageAsc(question.getId())
                    .forEach(l -> union.add(l.getLanguage()));
        }

        return new StudentPracticalAssessmentResponse(assessment.getId(), assessment.getTitle(),
                assessment.getSkill().getId(), assessment.getSkill().getName(), assessment.getPracticalType(),
                assessment.getWorkspaceType(), assessment.getDifficulty(), assessment.getTimeLimitMinutes(),
                assessment.getInstructions(), questions.size(), union.stream().toList(),
                integrityPolicyResolver.resolve(assessment.getConfigurationJson()));
    }

    private int questionCount(UUID assessmentId) {
        return questionRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessmentId).size();
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
