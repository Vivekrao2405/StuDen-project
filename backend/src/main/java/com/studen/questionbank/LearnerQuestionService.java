package com.studen.questionbank;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, answer-safe query surface for any authenticated user (not admin-gated — see
 * LearnerQuestionController). Deliberately kept separate from QuestionBankService: this class
 * must never be able to return a DRAFT/REVIEW/ARCHIVED question or an isCorrect flag, and keeping
 * it as its own class makes that guarantee obvious at a glance rather than relying on every
 * QuestionBankService method remembering to filter status.
 */
@Service
public class LearnerQuestionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final TopicRepository topicRepository;

    public LearnerQuestionService(QuestionRepository questionRepository, QuestionOptionRepository questionOptionRepository,
            TopicRepository topicRepository) {
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<LearnerQuestionResponse> listPublished(UUID skillId, UUID topicId, Difficulty difficulty,
            QuestionType questionType, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<Question> result = questionRepository.findPublishedForSkill(skillId, topicId, difficulty, questionType, pageable);

        List<Question> questions = result.getContent();
        Map<UUID, List<QuestionOption>> optionsByQuestionId = questions.isEmpty()
                ? Map.of()
                : questionOptionRepository
                        .findAllByQuestionIdInOrderByDisplayOrderAsc(questions.stream().map(Question::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));

        Page<LearnerQuestionResponse> mapped = result.map(
                q -> LearnerQuestionResponse.from(q, optionsByQuestionId.getOrDefault(q.getId(), List.of())));
        return PageResponse.of(mapped);
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> listTopics(UUID skillId) {
        return topicRepository.findBySkillIdOrderByDisplayOrderAscNameAsc(skillId).stream()
                .map(TopicResponse::from)
                .toList();
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
