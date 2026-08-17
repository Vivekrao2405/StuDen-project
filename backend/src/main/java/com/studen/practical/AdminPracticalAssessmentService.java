package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Difficulty;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin/content management for practical assessments — mirrors
 * {@code com.studen.questionbank.QuestionBankService} deliberately: same DRAFT/REVIEW/PUBLISHED/
 * ARCHIVED lifecycle, same fork-on-edit versioning, same "assumes ADMIN already checked by
 * {@code @PreAuthorize} on the controller" posture.
 */
@Service
public class AdminPracticalAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalCodingLanguageRepository languageRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final PracticalRubricCriterionRepository rubricRepository;
    private final PracticalAttemptRepository attemptRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public AdminPracticalAssessmentService(PracticalAssessmentRepository assessmentRepository,
            PracticalCodingLanguageRepository languageRepository, PracticalTestCaseRepository testCaseRepository,
            PracticalRubricCriterionRepository rubricRepository, PracticalAttemptRepository attemptRepository,
            SkillRepository skillRepository, UserRepository userRepository) {
        this.assessmentRepository = assessmentRepository;
        this.languageRepository = languageRepository;
        this.testCaseRepository = testCaseRepository;
        this.rubricRepository = rubricRepository;
        this.attemptRepository = attemptRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PracticalPageResponse<PracticalAssessmentSummaryResponse> list(UUID skillId, PracticalType practicalType,
            Difficulty difficulty, PracticalAssessmentStatus status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<PracticalAssessment> result = assessmentRepository.search(skillId, practicalType, difficulty, status,
                normalizedSearch, pageable);
        return PracticalPageResponse.of(result.map(PracticalAssessmentSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public PracticalAssessmentDetailResponse get(UUID id) {
        return toDetailResponse(findAssessment(id));
    }

    @Transactional
    public PracticalAssessmentDetailResponse create(UUID userId, PracticalAssessmentRequest request) {
        Skill skill = findSkill(request.skillId());
        User createdBy = findUser(userId);

        PracticalAssessment assessment = new PracticalAssessment(request.title().trim(), skill, request.practicalType(),
                request.workspaceType(), request.difficulty(), request.timeLimitMinutes(), request.instructions(),
                request.evaluationType(), createdBy);
        applyRequest(assessment, request, skill);
        PracticalAssessment saved = assessmentRepository.save(assessment);
        replaceChildren(saved, request);

        return toDetailResponse(saved);
    }

    @Transactional
    public PracticalAssessmentDetailResponse update(UUID id, PracticalAssessmentRequest request) {
        PracticalAssessment assessment = findAssessment(id);
        if (assessment.getStatus() == PracticalAssessmentStatus.PUBLISHED
                || assessment.getStatus() == PracticalAssessmentStatus.ARCHIVED) {
            throw new ConflictException(
                    "A " + assessment.getStatus() + " practical assessment can't be edited directly — use \"Create New Version\" instead");
        }

        Skill skill = findSkill(request.skillId());
        applyRequest(assessment, request, skill);
        replaceChildren(assessment, request);

        return toDetailResponse(assessment);
    }

    @Transactional
    public void delete(UUID id) {
        PracticalAssessment assessment = findAssessment(id);
        boolean neverPublished = assessment.getStatus() == PracticalAssessmentStatus.DRAFT
                && assessment.getVersion() == 1
                && assessment.getPreviousVersion() == null;
        if (!neverPublished) {
            throw new ConflictException(
                    "Only an untouched draft (never reviewed/published/versioned) can be deleted — archive it instead");
        }
        if (attemptRepository.existsByPracticalAssessmentId(id)) {
            throw new ConflictException("This assessment already has attempts and can't be deleted — archive it instead");
        }
        languageRepository.deleteAllByPracticalAssessmentId(id);
        testCaseRepository.deleteAllByPracticalAssessmentId(id);
        rubricRepository.deleteAllByPracticalAssessmentId(id);
        assessmentRepository.delete(assessment);
    }

    @Transactional
    public PracticalAssessmentDetailResponse submitForReview(UUID id) {
        PracticalAssessment assessment = findAssessment(id);
        if (assessment.getStatus() != PracticalAssessmentStatus.DRAFT) {
            throw new ConflictException("Only a DRAFT practical assessment can be submitted for review");
        }
        assessment.setStatus(PracticalAssessmentStatus.REVIEW);
        return toDetailResponse(assessment);
    }

    @Transactional
    public PracticalAssessmentDetailResponse publish(UUID id, UUID reviewerId) {
        PracticalAssessment assessment = findAssessment(id);
        if (assessment.getStatus() != PracticalAssessmentStatus.REVIEW) {
            throw new ConflictException("Only a practical assessment under REVIEW can be published");
        }
        validateForPublish(assessment);

        assessment.setStatus(PracticalAssessmentStatus.PUBLISHED);
        assessment.setReviewedBy(findUser(reviewerId));

        PracticalAssessment previous = assessment.getPreviousVersion();
        if (previous != null && previous.getStatus() == PracticalAssessmentStatus.PUBLISHED) {
            previous.setStatus(PracticalAssessmentStatus.ARCHIVED);
        }

        return toDetailResponse(assessment);
    }

    @Transactional
    public PracticalAssessmentDetailResponse archive(UUID id) {
        PracticalAssessment assessment = findAssessment(id);
        if (assessment.getStatus() == PracticalAssessmentStatus.ARCHIVED) {
            throw new ConflictException("This practical assessment is already archived");
        }
        assessment.setStatus(PracticalAssessmentStatus.ARCHIVED);
        return toDetailResponse(assessment);
    }

    @Transactional
    public PracticalAssessmentDetailResponse createNewVersion(UUID id, UUID userId) {
        PracticalAssessment original = findAssessment(id);
        if (original.getStatus() != PracticalAssessmentStatus.PUBLISHED) {
            throw new ConflictException("Only a PUBLISHED practical assessment can be versioned");
        }

        User createdBy = findUser(userId);
        PracticalAssessment next = new PracticalAssessment(original.getTitle(), original.getSkill(),
                original.getPracticalType(), original.getWorkspaceType(), original.getDifficulty(),
                original.getTimeLimitMinutes(), original.getInstructions(), original.getEvaluationType(), createdBy);
        next.setRequirements(original.getRequirements());
        next.setConstraints(original.getConstraints());
        next.setConfigurationJson(original.getConfigurationJson());
        next.setVersion(original.getVersion() + 1);
        next.setPreviousVersion(original);
        next = assessmentRepository.save(next);

        for (PracticalCodingLanguage lang : languageRepository.findAllByPracticalAssessmentIdOrderByLanguageAsc(original.getId())) {
            languageRepository.save(new PracticalCodingLanguage(next, lang.getLanguage(), lang.getStarterCode()));
        }
        for (PracticalTestCase tc : testCaseRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(original.getId())) {
            testCaseRepository.save(new PracticalTestCase(next, tc.getInput(), tc.getExpectedOutput(), tc.isHidden(),
                    tc.getDisplayOrder(), tc.getComparisonMode()));
        }
        for (PracticalRubricCriterion rc : rubricRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(original.getId())) {
            rubricRepository.save(new PracticalRubricCriterion(next, rc.getCriterion(), rc.getMaxPoints(), rc.getDisplayOrder()));
        }

        return toDetailResponse(next);
    }

    private void validateForPublish(PracticalAssessment assessment) {
        if (assessment.getPracticalType() == PracticalType.CODING) {
            List<PracticalCodingLanguage> languages = languageRepository
                    .findAllByPracticalAssessmentIdOrderByLanguageAsc(assessment.getId());
            if (languages.isEmpty()) {
                throw new InvalidRequestException("A CODING assessment needs at least one supported language");
            }
            List<PracticalTestCase> testCases = testCaseRepository
                    .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
            if (testCases.isEmpty()) {
                throw new InvalidRequestException("A CODING assessment needs at least one test case");
            }
        }

        List<PracticalRubricCriterion> criteria = rubricRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        if (!criteria.isEmpty()) {
            int total = criteria.stream().mapToInt(PracticalRubricCriterion::getMaxPoints).sum();
            if (total != 100) {
                throw new InvalidRequestException("Rubric criteria must add up to 100 points (currently " + total + ")");
            }
        }
    }

    private void applyRequest(PracticalAssessment assessment, PracticalAssessmentRequest request, Skill skill) {
        assessment.setTitle(request.title().trim());
        assessment.setSkill(skill);
        assessment.setPracticalType(request.practicalType());
        assessment.setWorkspaceType(request.workspaceType());
        assessment.setDifficulty(request.difficulty());
        assessment.setTimeLimitMinutes(request.timeLimitMinutes());
        assessment.setInstructions(request.instructions());
        assessment.setRequirements(blankToNull(request.requirements()));
        assessment.setConstraints(blankToNull(request.constraints()));
        assessment.setEvaluationType(request.evaluationType());
        assessment.setConfigurationJson(blankToNull(request.configurationJson()));
    }

    private void replaceChildren(PracticalAssessment assessment, PracticalAssessmentRequest request) {
        languageRepository.deleteAllByPracticalAssessmentId(assessment.getId());
        List<PracticalCodingLanguageRequest> languages = request.languages() == null ? List.of() : request.languages();
        for (PracticalCodingLanguageRequest lang : languages) {
            languageRepository.save(new PracticalCodingLanguage(assessment, lang.language(), lang.starterCode()));
        }

        testCaseRepository.deleteAllByPracticalAssessmentId(assessment.getId());
        List<PracticalTestCaseRequest> testCases = request.testCases() == null ? List.of() : request.testCases();
        for (PracticalTestCaseRequest tc : testCases) {
            testCaseRepository.save(new PracticalTestCase(assessment, tc.input(), tc.expectedOutput(), tc.hidden(),
                    tc.displayOrder(), tc.comparisonMode()));
        }

        rubricRepository.deleteAllByPracticalAssessmentId(assessment.getId());
        List<PracticalRubricCriterionRequest> criteria = request.rubricCriteria() == null ? List.of() : request.rubricCriteria();
        for (PracticalRubricCriterionRequest rc : criteria) {
            rubricRepository.save(new PracticalRubricCriterion(assessment, rc.criterion(), rc.maxPoints(), rc.displayOrder()));
        }
    }

    private PracticalAssessmentDetailResponse toDetailResponse(PracticalAssessment assessment) {
        List<PracticalCodingLanguage> languages = languageRepository
                .findAllByPracticalAssessmentIdOrderByLanguageAsc(assessment.getId());
        List<PracticalTestCase> testCases = testCaseRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        List<PracticalRubricCriterion> criteria = rubricRepository
                .findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        return PracticalAssessmentDetailResponse.from(assessment, languages, testCases, criteria);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private PracticalAssessment findAssessment(UUID id) {
        return assessmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));
    }

    private Skill findSkill(UUID id) {
        return skillRepository.findById(id).orElseThrow(() -> new InvalidRequestException("Skill not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
