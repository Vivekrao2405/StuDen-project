package com.studen.practical;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Difficulty;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
 *
 * <p>Phase 7.6: a practical assessment now holds a list of {@link PracticalQuestion}s instead of
 * being one itself. Add/reorder/edit/delete/duplicate a question are all just edits to the
 * {@code questions} array in {@link PracticalAssessmentRequest} — {@link #replaceQuestions} upserts
 * by id and deletes whatever's missing, exactly the same "replace the child list" pattern the
 * pre-7.6 code already used for languages/testCases/rubricCriteria, just one level deeper. No new
 * per-question CRUD endpoints are needed.
 */
@Service
public class AdminPracticalAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PracticalAssessmentRepository assessmentRepository;
    private final PracticalQuestionRepository questionRepository;
    private final PracticalCodingLanguageRepository languageRepository;
    private final PracticalTestCaseRepository testCaseRepository;
    private final PracticalRubricCriterionRepository rubricRepository;
    private final PracticalAttemptRepository attemptRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;

    public AdminPracticalAssessmentService(PracticalAssessmentRepository assessmentRepository,
            PracticalQuestionRepository questionRepository, PracticalCodingLanguageRepository languageRepository,
            PracticalTestCaseRepository testCaseRepository, PracticalRubricCriterionRepository rubricRepository,
            PracticalAttemptRepository attemptRepository, SkillRepository skillRepository, UserRepository userRepository) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
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
        Map<UUID, Integer> counts = questionCounts(result.getContent().stream().map(PracticalAssessment::getId).toList());
        return PracticalPageResponse.of(result.map(a -> PracticalAssessmentSummaryResponse.from(a, counts.getOrDefault(a.getId(), 0))));
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
        assessment.setConfigurationJson(blankToNull(request.configurationJson()));
        PracticalAssessment saved = assessmentRepository.save(assessment);
        replaceQuestions(saved, request.questions());

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
        replaceQuestions(assessment, request.questions());

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
        // DB-level ON DELETE CASCADE (migration V25) takes care of each question's languages/
        // testCases/rubricCriteria once the question row itself is gone.
        questionRepository.deleteAllByPracticalAssessmentId(id);
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
        next.setConfigurationJson(original.getConfigurationJson());
        next.setVersion(original.getVersion() + 1);
        next.setPreviousVersion(original);
        next = assessmentRepository.save(next);

        for (PracticalQuestion question : questionRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(original.getId())) {
            PracticalQuestion clone = new PracticalQuestion(next, question.getTitle(), question.getInstructions(),
                    question.getPoints(), question.getDisplayOrder());
            clone.setSkill(question.getSkill());
            clone.setDifficulty(question.getDifficulty());
            clone.setRequirements(question.getRequirements());
            clone.setConstraints(question.getConstraints());
            clone.setConfigurationJson(question.getConfigurationJson());
            clone = questionRepository.save(clone);

            for (PracticalCodingLanguage lang : languageRepository.findAllByPracticalQuestionIdOrderByLanguageAsc(question.getId())) {
                languageRepository.save(new PracticalCodingLanguage(clone, lang.getLanguage(), lang.getStarterCode()));
            }
            for (PracticalTestCase tc : testCaseRepository.findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId())) {
                testCaseRepository.save(new PracticalTestCase(clone, tc.getInput(), tc.getExpectedOutput(), tc.isHidden(),
                        tc.getDisplayOrder(), tc.getComparisonMode()));
            }
            for (PracticalRubricCriterion rc : rubricRepository.findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId())) {
                rubricRepository.save(new PracticalRubricCriterion(clone, rc.getCriterion(), rc.getMaxPoints(), rc.getDisplayOrder()));
            }
        }

        return toDetailResponse(next);
    }

    // Upserts every question by id (present+known id = edit that row, otherwise a fresh row —
    // this is what makes "duplicate" trivial: the frontend just resends an existing question's
    // content with id=null), then deletes whichever previously-existing questions weren't present
    // in this save. Every language/testCase/rubricCriterion under a kept question is fully
    // replaced (same discipline the pre-7.6 code already used at the assessment level) rather than
    // diffed — simpler, and cheap at this scale (a handful of rows per question).
    private void replaceQuestions(PracticalAssessment assessment, List<PracticalQuestionRequest> requests) {
        List<PracticalQuestionRequest> incoming = requests == null ? List.of() : requests;
        List<PracticalQuestion> existing = questionRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        Map<UUID, PracticalQuestion> existingById = existing.stream()
                .collect(Collectors.toMap(PracticalQuestion::getId, q -> q));
        Set<UUID> keptIds = new HashSet<>();

        for (PracticalQuestionRequest qr : incoming) {
            PracticalQuestion question = qr.id() != null ? existingById.get(qr.id()) : null;
            if (question == null) {
                question = new PracticalQuestion();
                question.setPracticalAssessment(assessment);
            }
            question.setTitle(qr.title().trim());
            question.setSkill(qr.skillId() != null ? findSkill(qr.skillId()) : null);
            question.setDifficulty(qr.difficulty());
            question.setInstructions(qr.instructions());
            question.setRequirements(blankToNull(qr.requirements()));
            question.setConstraints(blankToNull(qr.constraints()));
            question.setConfigurationJson(blankToNull(qr.configurationJson()));
            question.setPoints(qr.points());
            question.setDisplayOrder(qr.displayOrder());
            question = questionRepository.save(question);
            keptIds.add(question.getId());

            languageRepository.deleteAllByPracticalQuestionId(question.getId());
            for (PracticalCodingLanguageRequest lang : nullToEmpty(qr.languages())) {
                languageRepository.save(new PracticalCodingLanguage(question, lang.language(), lang.starterCode()));
            }

            testCaseRepository.deleteAllByPracticalQuestionId(question.getId());
            for (PracticalTestCaseRequest tc : nullToEmpty(qr.testCases())) {
                testCaseRepository.save(new PracticalTestCase(question, tc.input(), tc.expectedOutput(), tc.hidden(),
                        tc.displayOrder(), tc.comparisonMode()));
            }

            rubricRepository.deleteAllByPracticalQuestionId(question.getId());
            for (PracticalRubricCriterionRequest rc : nullToEmpty(qr.rubricCriteria())) {
                rubricRepository.save(new PracticalRubricCriterion(question, rc.criterion(), rc.maxPoints(), rc.displayOrder()));
            }
        }

        for (PracticalQuestion question : existing) {
            if (!keptIds.contains(question.getId())) {
                languageRepository.deleteAllByPracticalQuestionId(question.getId());
                testCaseRepository.deleteAllByPracticalQuestionId(question.getId());
                rubricRepository.deleteAllByPracticalQuestionId(question.getId());
                questionRepository.delete(question);
            }
        }
    }

    private void validateForPublish(PracticalAssessment assessment) {
        List<PracticalQuestion> questions = questionRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        if (questions.isEmpty()) {
            throw new InvalidRequestException("A practical assessment needs at least one question");
        }
        for (PracticalQuestion question : questions) {
            if (assessment.getPracticalType() == PracticalType.CODING) {
                List<PracticalCodingLanguage> languages = languageRepository
                        .findAllByPracticalQuestionIdOrderByLanguageAsc(question.getId());
                if (languages.isEmpty()) {
                    throw new InvalidRequestException(
                            "\"" + question.getTitle() + "\" needs at least one supported language");
                }
            }
            if (assessment.getPracticalType() == PracticalType.CODING || assessment.getPracticalType() == PracticalType.SQL) {
                List<PracticalTestCase> testCases = testCaseRepository
                        .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId());
                if (testCases.isEmpty()) {
                    throw new InvalidRequestException("\"" + question.getTitle() + "\" needs at least one test case");
                }
            }

            List<PracticalRubricCriterion> criteria = rubricRepository
                    .findAllByPracticalQuestionIdOrderByDisplayOrderAsc(question.getId());
            if (!criteria.isEmpty()) {
                int total = criteria.stream().mapToInt(PracticalRubricCriterion::getMaxPoints).sum();
                if (total != 100) {
                    throw new InvalidRequestException("Rubric criteria for \"" + question.getTitle()
                            + "\" must add up to 100 points (currently " + total + ")");
                }
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
        assessment.setEvaluationType(request.evaluationType());
        assessment.setConfigurationJson(blankToNull(request.configurationJson()));
    }

    private PracticalAssessmentDetailResponse toDetailResponse(PracticalAssessment assessment) {
        List<PracticalQuestion> questions = questionRepository.findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(assessment.getId());
        List<PracticalQuestionResponse> questionResponses = questions.stream()
                .map(q -> PracticalQuestionResponse.from(q,
                        languageRepository.findAllByPracticalQuestionIdOrderByLanguageAsc(q.getId()),
                        testCaseRepository.findAllByPracticalQuestionIdOrderByDisplayOrderAsc(q.getId()),
                        rubricRepository.findAllByPracticalQuestionIdOrderByDisplayOrderAsc(q.getId())))
                .toList();
        return PracticalAssessmentDetailResponse.from(assessment, questionResponses);
    }

    private Map<UUID, Integer> questionCounts(List<UUID> assessmentIds) {
        if (assessmentIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (Object[] row : questionRepository.countByAssessmentIds(assessmentIds)) {
            counts.put((UUID) row[0], ((Long) row[1]).intValue());
        }
        return counts;
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
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
