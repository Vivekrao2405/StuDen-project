package com.studen.placement;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.practical.PracticalAssessment;
import com.studen.practical.PracticalAssessmentRepository;
import com.studen.practical.PracticalAssessmentStatus;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionStatus;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.HashMap;
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
 * Admin configuration of role-specific readiness assessments. Create the definition and attach
 * Question Bank questions or existing practical assessments to it (Phase 3 completion — the
 * engine that starts/scores an attempt lives in {@link PlacementAttemptService} and must read
 * this configuration rather than assume anything about a role).
 *
 * <p>Lifecycle mirrors {@code AdminResourceService}: DRAFT -> PUBLISHED -> ARCHIVED with no review
 * step. Publishing requires at least one configured item, so a student can never be handed an
 * empty assessment.
 */
@Service
public class PlacementAssessmentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PlacementAssessmentRepository assessmentRepository;
    private final PlacementRoleRepository roleRepository;
    private final QuestionRepository questionRepository;
    private final PracticalAssessmentRepository practicalAssessmentRepository;
    private final PlacementAttemptRepository attemptRepository;
    private final UserRepository userRepository;

    public PlacementAssessmentService(PlacementAssessmentRepository assessmentRepository,
            PlacementRoleRepository roleRepository, QuestionRepository questionRepository,
            PracticalAssessmentRepository practicalAssessmentRepository, PlacementAttemptRepository attemptRepository,
            UserRepository userRepository) {
        this.assessmentRepository = assessmentRepository;
        this.roleRepository = roleRepository;
        this.questionRepository = questionRepository;
        this.practicalAssessmentRepository = practicalAssessmentRepository;
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
        return PlacementAssessmentDetailResponse.from(assessment, roleSkillIds(assessment.getRole().getId()));
    }

    @Transactional
    public PlacementAssessmentDetailResponse create(UUID adminUserId, PlacementAssessmentRequest request) {
        PlacementRole role = findRole(request.roleId());
        User createdBy = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PlacementAssessment assessment =
                new PlacementAssessment(request.title().trim(), role, request.difficulty(), createdBy);
        applyRequest(assessment, request, role);
        PlacementAssessment saved = assessmentRepository.save(assessment);
        return PlacementAssessmentDetailResponse.from(saved, roleSkillIds(saved.getRole().getId()));
    }

    @Transactional
    public PlacementAssessmentDetailResponse update(UUID id, PlacementAssessmentRequest request) {
        PlacementAssessment assessment = findAssessment(id);
        PlacementRole role = findRole(request.roleId());
        applyRequest(assessment, request, role);
        return PlacementAssessmentDetailResponse.from(assessment, roleSkillIds(role.getId()));
    }

    @Transactional
    public void delete(UUID id) {
        PlacementAssessment assessment = findAssessment(id);
        if (attemptRepository.existsByPlacementAssessmentId(id)) {
            throw new ConflictException("Students have already attempted this assessment — archive it instead");
        }
        // Cascades to its own item link rows only; the linked questions/practicals are untouched.
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
        return PlacementAssessmentDetailResponse.from(assessment, roleSkillIds(assessment.getRole().getId()));
    }

    /**
     * Attaches an existing Question Bank question OR an existing practical assessment — exactly
     * one of {@code request.questionId()}/{@code request.practicalAssessmentId()} must be set.
     * Only PUBLISHED content is eligible either way, matching the rule the rest of the platform
     * already enforces — a draft/archived question or practical must never reach a student.
     */
    @Transactional
    public PlacementAssessmentQuestionResponse addQuestion(UUID assessmentId,
            PlacementAssessmentQuestionRequest request) {
        PlacementAssessment assessment = findAssessment(assessmentId);
        boolean hasQuestion = request.questionId() != null;
        boolean hasPractical = request.practicalAssessmentId() != null;
        if (hasQuestion == hasPractical) {
            throw new InvalidRequestException("Provide exactly one of questionId or practicalAssessmentId");
        }

        int displayOrder = request.displayOrder() != null ? request.displayOrder() : assessment.getQuestions().size();
        int points = request.points() != null ? request.points() : 1;
        PlacementAssessmentQuestion link;

        if (hasQuestion) {
            Question question = questionRepository.findById(request.questionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
            if (question.getStatus() != QuestionStatus.PUBLISHED) {
                throw new InvalidRequestException("Only published questions can be added to an assessment");
            }
            boolean alreadyLinked = assessment.getQuestions().stream()
                    .anyMatch(l -> l.getItemType() == ModuleItemType.QUESTION
                            && l.getQuestion().getId().equals(question.getId()));
            if (alreadyLinked) {
                throw new ConflictException("This question is already in this assessment");
            }
            link = new PlacementAssessmentQuestion(assessment, question, displayOrder, points);
        } else {
            PracticalAssessment practical = practicalAssessmentRepository.findById(request.practicalAssessmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Practical assessment not found"));
            if (practical.getStatus() != PracticalAssessmentStatus.PUBLISHED) {
                throw new InvalidRequestException("Only published practical assessments can be added to an assessment");
            }
            boolean alreadyLinked = assessment.getQuestions().stream()
                    .anyMatch(l -> l.getItemType() == ModuleItemType.PRACTICAL_ASSESSMENT
                            && l.getPracticalAssessment().getId().equals(practical.getId()));
            if (alreadyLinked) {
                throw new ConflictException("This practical assessment is already in this assessment");
            }
            link = new PlacementAssessmentQuestion(assessment, practical, displayOrder, points);
        }

        assessment.getQuestions().add(link);
        assessmentRepository.flush();
        return PlacementAssessmentQuestionResponse.from(link, roleSkillIds(assessment.getRole().getId()));
    }

    @Transactional
    public void removeQuestion(UUID assessmentId, UUID itemContentId) {
        PlacementAssessment assessment = findAssessment(assessmentId);
        boolean removed = assessment.getQuestions().removeIf(link ->
                (link.getItemType() == ModuleItemType.QUESTION && link.getQuestion().getId().equals(itemContentId))
             || (link.getItemType() == ModuleItemType.PRACTICAL_ASSESSMENT
                    && link.getPracticalAssessment().getId().equals(itemContentId)));
        if (!removed) {
            throw new ResourceNotFoundException("This item is not in this assessment");
        }
    }

    // Every RoleSkill's skill id for this role, used only to flag (never block) an item whose
    // skill isn't part of the role's configured mapping.
    private Set<UUID> roleSkillIds(UUID roleId) {
        return roleRepository.findRoleSkills(roleId).stream()
                .map(rs -> rs.getSkill().getId())
                .collect(Collectors.toSet());
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
