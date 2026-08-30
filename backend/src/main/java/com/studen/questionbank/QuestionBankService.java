package com.studen.questionbank;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin/content management operations on the Question Bank. Every method here assumes the caller
 * has already been authorized as ADMIN by {@code @PreAuthorize} on the controller — this class
 * does not re-check role, only ownership-irrelevant business rules (status transitions,
 * versioning, validation).
 */
@Service
public class QuestionBankService {

    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final TopicRepository topicRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final QuestionValidationService validationService;

    public QuestionBankService(QuestionRepository questionRepository, QuestionOptionRepository questionOptionRepository,
            TopicRepository topicRepository, SkillRepository skillRepository, UserRepository userRepository,
            QuestionValidationService validationService) {
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.topicRepository = topicRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.validationService = validationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryResponse> list(UUID skillId, UUID topicId, Difficulty difficulty,
            QuestionType questionType, QuestionStatus status, String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<Question> result = questionRepository.search(skillId, topicId, difficulty, questionType, status,
                normalizedSearch, pageable);
        return PageResponse.of(result.map(QuestionSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public QuestionResponse get(UUID id) {
        Question question = findQuestion(id);
        return toResponse(question, null);
    }

    @Transactional
    public QuestionResponse create(UUID userId, QuestionRequest request) {
        validationService.validate(request, false);

        Skill skill = findSkill(request.skillId());
        Topic topic = resolveTopic(request.topicId(), skill.getId());
        User createdBy = findUser(userId);

        Question question = new Question(skill, request.questionText().trim(), request.questionType(),
                request.difficulty(), createdBy);
        applyRequest(question, request, skill, topic);
        final Question saved = questionRepository.save(question);

        DuplicateWarningResponse duplicateWarning = questionRepository
                .findFirstBySkillIdAndNormalizedQuestionTextOrderByCreatedAtAsc(skill.getId(), saved.getNormalizedQuestionText())
                .filter(existing -> !existing.getId().equals(saved.getId()))
                .map(existing -> new DuplicateWarningResponse(existing.getId(), existing.getQuestionText()))
                .orElse(null);

        return toResponse(saved, duplicateWarning);
    }

    @Transactional
    public QuestionResponse update(UUID id, QuestionRequest request) {
        Question question = findQuestion(id);
        if (question.getStatus() == QuestionStatus.PUBLISHED || question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new ConflictException(
                    "A " + question.getStatus() + " question can't be edited directly — use \"Create New Version\" instead");
        }

        validationService.validate(request, false);

        Skill skill = findSkill(request.skillId());
        Topic topic = resolveTopic(request.topicId(), skill.getId());
        applyRequest(question, request, skill, topic);

        return toResponse(question, null);
    }

    @Transactional
    public void delete(UUID id) {
        Question question = findQuestion(id);
        boolean neverPublished = question.getStatus() == QuestionStatus.DRAFT
                && question.getVersion() == 1
                && question.getPreviousVersion() == null;
        if (!neverPublished) {
            throw new ConflictException("Only an untouched draft (never reviewed/published/versioned) can be deleted — archive it instead");
        }
        questionRepository.delete(question);
    }

    @Transactional
    public QuestionResponse submitForReview(UUID id) {
        Question question = findQuestion(id);
        if (question.getStatus() != QuestionStatus.DRAFT) {
            throw new ConflictException("Only a DRAFT question can be submitted for review");
        }
        question.setStatus(QuestionStatus.REVIEW);
        return toResponse(question, null);
    }

    @Transactional
    public QuestionResponse publish(UUID id, UUID reviewerId) {
        Question question = findQuestion(id);
        if (question.getStatus() != QuestionStatus.REVIEW) {
            throw new ConflictException("Only a question under REVIEW can be published");
        }

        validationService.validate(toRequest(question), true);

        question.setStatus(QuestionStatus.PUBLISHED);
        question.setReviewedBy(findUser(reviewerId));

        Question previous = question.getPreviousVersion();
        if (previous != null && previous.getStatus() == QuestionStatus.PUBLISHED) {
            previous.setStatus(QuestionStatus.ARCHIVED);
        }

        return toResponse(question, null);
    }

    @Transactional
    public QuestionResponse archive(UUID id) {
        Question question = findQuestion(id);
        if (question.getStatus() == QuestionStatus.ARCHIVED) {
            throw new ConflictException("This question is already archived");
        }
        question.setStatus(QuestionStatus.ARCHIVED);
        return toResponse(question, null);
    }

    @Transactional
    public QuestionResponse createNewVersion(UUID id, UUID userId) {
        Question original = findQuestion(id);
        if (original.getStatus() != QuestionStatus.PUBLISHED) {
            throw new ConflictException("Only a PUBLISHED question can be versioned");
        }

        User createdBy = findUser(userId);
        Question next = new Question(original.getSkill(), original.getQuestionText(), original.getQuestionType(),
                original.getDifficulty(), createdBy);
        next.setTopic(original.getTopic());
        next.setNormalizedQuestionText(original.getNormalizedQuestionText());
        next.setExplanation(original.getExplanation());
        next.setEstimatedTimeSeconds(original.getEstimatedTimeSeconds());
        next.setTag(original.getTag());
        next.setVersion(original.getVersion() + 1);
        next.setPreviousVersion(original);
        next = questionRepository.save(next);

        List<QuestionOption> originalOptions = questionOptionRepository.findAllByQuestionIdOrderByDisplayOrderAsc(original.getId());
        for (QuestionOption option : originalOptions) {
            next.getOptions().add(new QuestionOption(next, option.getOptionText(), option.getDisplayOrder(), option.isCorrect()));
        }

        return toResponse(next, null);
    }

    @Transactional(readOnly = true)
    public DuplicateWarningResponse checkDuplicate(UUID skillId, String text) {
        if (skillId == null || text == null || text.isBlank()) {
            return null;
        }
        String normalized = normalize(text);
        return questionRepository.findFirstBySkillIdAndNormalizedQuestionTextOrderByCreatedAtAsc(skillId, normalized)
                .map(existing -> new DuplicateWarningResponse(existing.getId(), existing.getQuestionText()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public QuestionBankStatsResponse stats() {
        return QuestionBankStatsResponse.from(questionRepository.countGroupedByStatus());
    }

    @Transactional
    public TopicResponse createTopic(TopicRequest request) {
        Skill skill = findSkill(request.skillId());
        String normalized = normalize(request.name());
        return topicRepository.findBySkillIdAndNormalizedName(skill.getId(), normalized)
                .map(TopicResponse::from)
                .orElseGet(() -> {
                    Topic topic = new Topic(skill, request.name().trim(), normalized);
                    return TopicResponse.from(topicRepository.save(topic));
                });
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> listTopics(UUID skillId) {
        return topicRepository.findBySkillIdOrderByDisplayOrderAscNameAsc(skillId).stream()
                .map(TopicResponse::from)
                .toList();
    }

    private void applyRequest(Question question, QuestionRequest request, Skill skill, Topic topic) {
        question.setSkill(skill);
        question.setTopic(topic);
        question.setQuestionText(request.questionText().trim());
        question.setNormalizedQuestionText(normalize(request.questionText()));
        question.setQuestionType(request.questionType());
        question.setDifficulty(request.difficulty());
        question.setExplanation(blankToNull(request.explanation()));
        question.setEstimatedTimeSeconds(request.estimatedTimeSeconds());
        question.setTag(blankToNull(request.tag()));

        question.getOptions().clear();
        List<QuestionOptionRequest> options = request.options() == null ? List.of() : request.options();
        for (int i = 0; i < options.size(); i++) {
            QuestionOptionRequest optionRequest = options.get(i);
            question.getOptions().add(new QuestionOption(question, optionRequest.optionText().trim(), i,
                    optionRequest.isCorrect()));
        }
    }

    // Re-derives a QuestionRequest from the current entity state so publish() can run the exact
    // same QuestionValidationService rules used at create/update time, without duplicating them.
    private QuestionRequest toRequest(Question question) {
        List<QuestionOptionRequest> options = question.getOptions().stream()
                .map(o -> new QuestionOptionRequest(o.getOptionText(), o.getDisplayOrder(), o.isCorrect()))
                .toList();
        return new QuestionRequest(question.getSkill().getId(), question.getTopic() == null ? null : question.getTopic().getId(),
                question.getQuestionText(), question.getQuestionType(), question.getDifficulty(),
                question.getExplanation(), question.getEstimatedTimeSeconds(), question.getTag(), options);
    }

    private Topic resolveTopic(UUID topicId, UUID skillId) {
        if (topicId == null) {
            return null;
        }
        return topicRepository.findByIdAndSkillId(topicId, skillId)
                .orElseThrow(() -> new InvalidRequestException("Topic doesn't belong to the selected skill"));
    }

    private QuestionResponse toResponse(Question question, DuplicateWarningResponse duplicateWarning) {
        List<QuestionOption> options = questionOptionRepository.findAllByQuestionIdOrderByDisplayOrderAsc(question.getId());
        return QuestionResponse.from(question, options, duplicateWarning);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private Question findQuestion(UUID id) {
        return questionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Question not found"));
    }

    private Skill findSkill(UUID id) {
        return skillRepository.findById(id).orElseThrow(() -> new InvalidRequestException("Skill not found"));
    }

    private User findUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
