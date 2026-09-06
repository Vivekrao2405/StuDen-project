package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionStatus;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin configuration of role-specific readiness assessments. Phase 0 scope: create the definition
 * and attach Question Bank questions to it. The engine that generates a paper, times it, scores it
 * and derives skill scores is a later phase and must read this configuration rather than assume
 * anything about a role.
 *
 * <p>Lifecycle mirrors {@code AdminResourceService}: DRAFT -> PUBLISHED -> ARCHIVED with no review
 * step. Publishing requires at least one configured question, so a student can never be handed an
 * empty assessment.
 */
@Service
public class PlacementAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlacementAssessmentRepository assessmentRepository;
    private final PlacementRoleRepository roleRepository;
    private final QuestionRepository questionRepository;
    private final PlacementAttemptRepository attemptRepository;
    private final UserRepository userRepository;

    public PlacementAssessmentService(PlacementAssessmentRepository assessmentRepository,
            PlacementRoleRepository roleRepository, QuestionRepository questionRepository,
            PlacementAttemptRepository attemptRepository, UserRepository userRepository) {
        this.assessmentRepository = assessmentRepository;
        this.roleRepository = roleRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PlacementPageResponse<PlacementAssessmentResponse> list(UUID roleId, PlacementContentStatus status,
            String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<PlacementAssessment> result = assessmentRepository.search(roleId, status, normalizedSearch, pageable);

        List<UUID> ids = result.getContent().stream().map(PlacementAssessment::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (IdCountView view : assessmentRepository.countQuestionsByAssessment(ids)) {
                counts.put(view.id(), view.count());
            }
        }
        return PlacementPageResponse.of(result.map(assessment ->
                PlacementAssessmentResponse.from(assessment, counts.getOrDefault(assessment.getId(), 0L).intValue())));
    }

    @Transactional(readOnly = true)
    public PlacementAssessmentDetailResponse get(UUID id) {
        PlacementAssessment assessment = assessmentRepository.findByIdWithQuestions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
        return PlacementAssessmentDetailResponse.from(assessment);
    }

    @Transactional
    public PlacementAssessmentDetailResponse create(UUID adminUserId, PlacementAssessmentRequest request) {
        PlacementRole role = findRole(request.roleId());
        User createdBy = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PlacementAssessment assessment =
                new PlacementAssessment(request.title().trim(), role, request.difficulty(), createdBy);
        applyRequest(assessment, request, role);
        return PlacementAssessmentDetailResponse.from(assessmentRepository.save(assessment));
    }

    @Transactional
    public PlacementAssessmentDetailResponse update(UUID id, PlacementAssessmentRequest request) {
        PlacementAssessment assessment = findAssessment(id);
        applyRequest(assessment, request, findRole(request.roleId()));
        return PlacementAssessmentDetailResponse.from(assessment);
    }

    @Transactional
    public void delete(UUID id) {
        PlacementAssessment assessment = findAssessment(id);
        if (attemptRepository.existsByPlacementAssessmentId(id)) {
            throw new ConflictException("Students have already attempted this assessment — archive it instead");
        }
        // Cascades to its own question link rows only; the questions themselves are untouched.
        assessmentRepository.delete(assessment);
    }

    @Transactional
    public PlacementAssessmentDetailResponse setStatus(UUID id, PlacementContentStatus status) {
        PlacementAssessment assessment = assessmentRepository.findByIdWithQuestions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
        if (status == PlacementContentStatus.PUBLISHED && assessment.getQuestions().isEmpty()) {
            throw new InvalidRequestException("Add at least one question before publishing this assessment");
        }
        assessment.setStatus(status);
        return PlacementAssessmentDetailResponse.from(assessment);
    }

    /**
     * Attaches an existing Question Bank question. Only PUBLISHED questions are eligible, matching
     * the rule the rest of the platform already enforces — a draft or archived question must never
     * reach a student.
     */
    @Transactional
    public PlacementAssessmentQuestionResponse addQuestion(UUID assessmentId,
            PlacementAssessmentQuestionRequest request) {
        PlacementAssessment assessment = findAssessment(assessmentId);
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        if (question.getStatus() != QuestionStatus.PUBLISHED) {
            throw new InvalidRequestException("Only published questions can be added to an assessment");
        }
        boolean alreadyLinked = assessment.getQuestions().stream()
                .anyMatch(link -> link.getQuestion().getId().equals(question.getId()));
        if (alreadyLinked) {
            throw new ConflictException("This question is already in this assessment");
        }

        int displayOrder = request.displayOrder() != null ? request.displayOrder() : assessment.getQuestions().size();
        int points = request.points() != null ? request.points() : 1;
        PlacementAssessmentQuestion link =
                new PlacementAssessmentQuestion(assessment, question, displayOrder, points);
        assessment.getQuestions().add(link);
        assessmentRepository.flush();
        return PlacementAssessmentQuestionResponse.from(link);
    }

    @Transactional
    public void removeQuestion(UUID assessmentId, UUID questionId) {
        PlacementAssessment assessment = findAssessment(assessmentId);
        boolean removed = assessment.getQuestions()
                .removeIf(link -> link.getQuestion().getId().equals(questionId));
        if (!removed) {
            throw new ResourceNotFoundException("This question is not in this assessment");
        }
    }

    private void applyRequest(PlacementAssessment assessment, PlacementAssessmentRequest request, PlacementRole role) {
        assessment.setTitle(request.title().trim());
        assessment.setDescription(trimToNull(request.description()));
        assessment.setRole(role);
        assessment.setDifficulty(request.difficulty());
        assessment.setDurationMinutes(request.durationMinutes());
        assessment.setPassingScore(request.passingScore());
    }

    private PlacementAssessment findAssessment(UUID id) {
        return assessmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
    }

    private PlacementRole findRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    private int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
