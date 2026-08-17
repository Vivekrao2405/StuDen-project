package com.studen.practical;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Difficulty;
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
 * {@code com.studen.assessment.AssessmentController}).
 */
@Service
public class PracticalAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalCodingLanguageRepository languageRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final PracticalRubricCriterionRepository rubricRepository;

    public PracticalAssessmentService(PracticalAssessmentRepository assessmentRepository,
            PracticalCodingLanguageRepository languageRepository, PracticalTestCaseRepository testCaseRepository,
            PracticalRubricCriterionRepository rubricRepository) {
        this.assessmentRepository = assessmentRepository;
        this.languageRepository = languageRepository;
        this.testCaseRepository = testCaseRepository;
        this.rubricRepository = rubricRepository;
    }

    @Transactional(readOnly = true)
    public PracticalPageResponse<PracticalAssessmentSummaryResponse> list(UUID skillId, PracticalType practicalType,
            Difficulty difficulty, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<PracticalAssessment> result = assessmentRepository.search(skillId, practicalType, difficulty,
                PracticalAssessmentStatus.PUBLISHED, normalizedSearch, pageable);
        return PracticalPageResponse.of(result.map(PracticalAssessmentSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public StudentPracticalAssessmentResponse get(UUID id) {
        PracticalAssessment assessment = assessmentRepository.findByIdAndStatus(id, PracticalAssessmentStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));
        return toStudentResponse(assessment);
    }

    // Package-private: reused by PracticalAttemptService when it needs the same student-safe
    // shape while building an in-progress attempt view.
    StudentPracticalAssessmentResponse toStudentResponse(PracticalAssessment assessment) {
        List<PracticalCodingLanguageResponse> languages = languageRepository
                .findAllByPracticalAssessmentIdOrderByLanguageAsc(assessment.getId()).stream()
                .map(PracticalCodingLanguageResponse::from).toList();
        List<StudentTestCaseView> publicTestCases = testCaseRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId()).stream()
                .filter(tc -> !tc.isHidden())
                .map(StudentTestCaseView::from).toList();
        List<PracticalRubricCriterionResponse> criteria = rubricRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId()).stream()
                .map(PracticalRubricCriterionResponse::from).toList();

        return new StudentPracticalAssessmentResponse(assessment.getId(), assessment.getTitle(),
                assessment.getSkill().getId(), assessment.getSkill().getName(), assessment.getPracticalType(),
                assessment.getWorkspaceType(), assessment.getDifficulty(), assessment.getTimeLimitMinutes(),
                assessment.getInstructions(), assessment.getRequirements(), assessment.getConstraints(),
                assessment.getConfigurationJson(), languages, publicTestCases, criteria);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
