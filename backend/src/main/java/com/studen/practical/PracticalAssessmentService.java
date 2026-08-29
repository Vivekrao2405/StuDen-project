package com.studen.practical;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.integrity.IntegrityPolicyResolver;
import com.studen.portfolio.EligibilityState;
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
 * {@code com.studen.assessment.AssessmentController}). Every published assessment is discoverable
 * by every student regardless of portfolio contents; an optional {@code skillId}/{@code category}
 * only ever narrows within that full catalog, never expands beyond it.
 */
@Service
public class PracticalAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalQuestionRepository questionRepository;
    private final PracticalCodingLanguageRepository languageRepository;
    private final IntegrityPolicyResolver integrityPolicyResolver;

    public PracticalAssessmentService(PracticalAssessmentRepository assessmentRepository,
            PracticalQuestionRepository questionRepository, PracticalCodingLanguageRepository languageRepository,
            IntegrityPolicyResolver integrityPolicyResolver) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.languageRepository = languageRepository;
        this.integrityPolicyResolver = integrityPolicyResolver;
    }

    @Transactional(readOnly = true)
    public PracticalAssessmentListResponse list(UUID userId, UUID skillId, PracticalType practicalType,
            Difficulty difficulty, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();

        Page<PracticalAssessment> result = assessmentRepository.searchPublished(skillId, practicalType,
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
