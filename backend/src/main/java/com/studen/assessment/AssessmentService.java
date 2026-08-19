package com.studen.assessment;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.questionbank.Question;
import com.studen.questionbank.QuestionOption;
import com.studen.questionbank.QuestionOptionRepository;
import com.studen.questionbank.QuestionRepository;
import com.studen.questionbank.QuestionRepository.SkillPublishedCount;
import com.studen.questionbank.QuestionType;
import com.studen.questionbank.Topic;
import com.studen.questionbank.TopicRepository;
import com.studen.skill.Skill;
import com.studen.skill.SkillRepository;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the whole knowledge-assessment lifecycle: generation, resume, autosave, expiry, and
 * submission. Every learner-facing lookup is scoped by {@code userId} via
 * {@link AssessmentRepository#findByIdAndUserId} — never a bare {@code findById} — so cross-user
 * access 404s instead of leaking another student's assessment (spec §33).
 */
@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;
    private final AssessmentQuestionOptionRepository assessmentQuestionOptionRepository;
    private final AssessmentAnswerRepository assessmentAnswerRepository;
    private final QuestionSelectionService questionSelectionService;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final TopicRepository topicRepository;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final AssessmentProperties properties;

    public AssessmentService(AssessmentRepository assessmentRepository,
            AssessmentQuestionRepository assessmentQuestionRepository,
            AssessmentQuestionOptionRepository assessmentQuestionOptionRepository,
            AssessmentAnswerRepository assessmentAnswerRepository, QuestionSelectionService questionSelectionService,
            QuestionRepository questionRepository, QuestionOptionRepository questionOptionRepository,
            TopicRepository topicRepository, SkillRepository skillRepository, UserRepository userRepository,
            AssessmentProperties properties) {
        this.assessmentRepository = assessmentRepository;
        this.assessmentQuestionRepository = assessmentQuestionRepository;
        this.assessmentQuestionOptionRepository = assessmentQuestionOptionRepository;
        this.assessmentAnswerRepository = assessmentAnswerRepository;
        this.questionSelectionService = questionSelectionService;
        this.questionRepository = questionRepository;
        this.questionOptionRepository = questionOptionRepository;
        this.topicRepository = topicRepository;
        this.skillRepository = skillRepository;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AssessableSkillResponse> listAssessableSkills() {
        List<SkillPublishedCount> counts = questionRepository.countPublishedGroupedBySkill();
        if (counts.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> countBySkillId = counts.stream()
                .collect(Collectors.toMap(SkillPublishedCount::getSkillId, SkillPublishedCount::getTotal));
        int required = properties.getDefaultQuestionCount();
        return skillRepository.findAllById(countBySkillId.keySet()).stream()
                .map(skill -> AssessableSkillResponse.of(skill, countBySkillId.get(skill.getId()).intValue(), required))
                .sorted(Comparator.comparing(AssessableSkillResponse::name))
                .toList();
    }

    @Transactional
    public AssessmentDetailResponse startOrResume(UUID userId, UUID skillId) {
        Skill skill = skillRepository.findById(skillId).orElseThrow(() -> new ResourceNotFoundException("Skill not found"));

        Assessment existing = assessmentRepository.findByUserIdAndSkillIdAndStatus(userId, skillId, AssessmentStatus.IN_PROGRESS)
                .orElse(null);
        if (existing != null) {
            if (expireIfDue(existing)) {
                existing = assessmentRepository.findByIdAndUserId(existing.getId(), userId).orElse(null);
            }
            if (existing != null && existing.getStatus() == AssessmentStatus.IN_PROGRESS) {
                return buildInProgressView(existing);
            }
        }

        if (!questionSelectionService.isAssessable(skillId)) {
            throw new ConflictException("Not enough published questions are available for this assessment yet.");
        }

        int total = properties.getDefaultQuestionCount();
        List<UUID> questionIds = questionSelectionService.select(skillId, total);
        Map<UUID, Question> questionById = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        User userRef = userRepository.getReferenceById(userId);
        Assessment assessment = new Assessment(userRef, skill, AssessmentType.KNOWLEDGE, questionIds.size(),
                properties.timeLimitSeconds());
        assessment = assessmentRepository.save(assessment);

        // Batch-fetch topic names in one query rather than touching each Question's lazy `topic`
        // association individually (would be an N+1 across the whole question set). Reading just
        // .getId() off the lazy proxy below is safe/query-free — Hibernate resolves an association
        // proxy's identifier from the already-loaded FK column without initializing it.
        Set<UUID> topicIds = questionById.values().stream()
                .map(q -> q.getTopic() != null ? q.getTopic().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> topicNameById = topicIds.isEmpty() ? Map.of()
                : topicRepository.findAllById(topicIds).stream().collect(Collectors.toMap(Topic::getId, Topic::getName));

        // Same reasoning as the topic-name batch-fetch above, applied to Question.tags — one query
        // for the whole question set rather than an N+1 lazy load per question. Snapshotted onto
        // each AssessmentQuestion (never read live) so a later admin tag edit can't retroactively
        // change a past assessment's tag-wise performance breakdown (SkillResultService).
        Map<UUID, Set<String>> tagsByQuestionId = questionRepository.findTagsForQuestionIds(questionIds).stream()
                .collect(Collectors.groupingBy(QuestionRepository.QuestionTagProjection::getQuestionId,
                        Collectors.mapping(QuestionRepository.QuestionTagProjection::getTag,
                                Collectors.toCollection(LinkedHashSet::new))));

        List<AssessmentQuestion> savedQuestions = new ArrayList<>();
        int order = 0;
        for (UUID questionId : questionIds) {
            Question question = questionById.get(questionId);
            UUID topicId = question.getTopic() != null ? question.getTopic().getId() : null;
            String topicName = topicId != null ? topicNameById.get(topicId) : null;
            AssessmentQuestion aq = new AssessmentQuestion(assessment, question, order++, topicId, topicName);
            aq.setTags(tagsByQuestionId.getOrDefault(questionId, Set.of()));
            savedQuestions.add(assessmentQuestionRepository.save(aq));
        }

        Map<UUID, List<QuestionOption>> optionsByQuestionId = questionOptionRepository
                .findAllByQuestionIdInOrderByDisplayOrderAsc(questionIds).stream()
                .collect(Collectors.groupingBy(o -> o.getQuestion().getId()));

        for (AssessmentQuestion aq : savedQuestions) {
            List<QuestionOption> options = new ArrayList<>(
                    optionsByQuestionId.getOrDefault(aq.getQuestion().getId(), List.of()));
            Collections.shuffle(options, ThreadLocalRandom.current());
            int optionOrder = 0;
            for (QuestionOption option : options) {
                assessmentQuestionOptionRepository.save(new AssessmentQuestionOption(aq, option, optionOrder++));
            }
        }

        return buildInProgressView(assessment);
    }

    @Transactional
    public Object getAssessment(UUID userId, UUID assessmentId) {
        Assessment assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
        if (expireIfDue(assessment)) {
            assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId).orElseThrow();
        }
        return assessment.getStatus() == AssessmentStatus.IN_PROGRESS ? buildInProgressView(assessment)
                : buildResultView(assessment);
    }

    @Transactional
    public AnswerResponse saveAnswer(UUID userId, UUID assessmentId, UUID assessmentQuestionId, List<UUID> selectedOptionIds) {
        Assessment assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));
        if (expireIfDue(assessment)) {
            assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId).orElseThrow();
        }
        if (assessment.getStatus() != AssessmentStatus.IN_PROGRESS) {
            throw new ConflictException("This assessment is no longer in progress.");
        }

        AssessmentQuestion aq = assessmentQuestionRepository.findByIdAndAssessmentId(assessmentQuestionId, assessmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found in this assessment"));

        Set<UUID> validOptionIds = assessmentQuestionOptionRepository
                .findAllByAssessmentQuestionIdOrderByDisplayOrderAsc(assessmentQuestionId).stream()
                .map(o -> o.getQuestionOption().getId())
                .collect(Collectors.toSet());

        Set<UUID> selected = new LinkedHashSet<>(selectedOptionIds);
        if (selected.isEmpty()) {
            throw new InvalidRequestException("At least one option must be selected.");
        }
        if (selected.size() != selectedOptionIds.size()) {
            throw new InvalidRequestException("Duplicate option selected.");
        }
        if (!validOptionIds.containsAll(selected)) {
            throw new InvalidRequestException("One or more selected options are invalid for this question.");
        }
        QuestionType type = aq.getQuestion().getQuestionType();
        if ((type == QuestionType.MCQ_SINGLE || type == QuestionType.TRUE_FALSE) && selected.size() != 1) {
            throw new InvalidRequestException("This question requires exactly one selected option.");
        }

        Assessment assessmentRef = assessment;
        AssessmentAnswer answer = assessmentAnswerRepository.findByAssessmentQuestionId(assessmentQuestionId)
                .orElseGet(() -> new AssessmentAnswer(assessmentRef, aq));
        answer.setSelectedOptionIds(selected);
        answer.setAnsweredAt(Instant.now());
        assessmentAnswerRepository.save(answer);

        return new AnswerResponse(assessmentQuestionId, List.copyOf(selected), answer.getAnsweredAt());
    }

    @Transactional
    public AssessmentResultResponse submit(UUID userId, UUID assessmentId) {
        Assessment assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Assessment not found"));

        if (assessment.getStatus() == AssessmentStatus.IN_PROGRESS && expireIfDue(assessment)) {
            assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId).orElseThrow();
        }
        if (assessment.getStatus() == AssessmentStatus.IN_PROGRESS) {
            // A second, racing/double-click submit call finds this already flipped to SUBMITTED
            // and its own finalizeIfInProgress affects 0 rows — harmless, since every caller
            // re-fetches and returns whatever the stored result actually is (spec §26).
            finalizeAssessment(assessmentId, AssessmentStatus.SUBMITTED);
            assessment = assessmentRepository.findByIdAndUserId(assessmentId, userId).orElseThrow();
        }
        return buildResultView(assessment);
    }

    // Returns true if this call actually transitioned the assessment to EXPIRED (i.e. the caller
    // must re-fetch to see fresh state) — false if it was already terminal or not yet due.
    private boolean expireIfDue(Assessment assessment) {
        if (assessment.getStatus() != AssessmentStatus.IN_PROGRESS || assessment.getTimeLimitSeconds() == null) {
            return false;
        }
        Instant deadline = assessment.getStartedAt().plusSeconds(assessment.getTimeLimitSeconds());
        if (Instant.now().isBefore(deadline)) {
            return false;
        }
        finalizeAssessment(assessment.getId(), AssessmentStatus.EXPIRED);
        return true;
    }

    // Shared by submit() and the lazy expiry check — computes the raw score from whatever answers
    // exist right now and atomically flips status via the same compare-and-set idiom as
    // WorkOrderRepository. A 0-row update (lost a race to a concurrent finalize) is not an error:
    // every caller re-fetches afterward and trusts whatever is actually stored.
    private void finalizeAssessment(UUID assessmentId, AssessmentStatus targetStatus) {
        LoadedAssessmentData data = loadQuestionsWithOptionsAndAnswers(assessmentId);
        int correct = 0;
        for (AssessmentQuestion aq : data.questions()) {
            Set<UUID> correctIds = correctOptionIds(data.optionsByAssessmentQuestion().getOrDefault(aq.getId(), List.of()));
            Set<UUID> selected = selectedOptionIds(data.answerByAssessmentQuestion().get(aq.getId()));
            if (isCorrect(selected, correctIds)) {
                correct++;
            }
        }
        int total = data.questions().size();
        int scorePercentage = total == 0 ? 0 : Math.round(correct * 100f / total);
        assessmentRepository.finalizeIfInProgress(assessmentId, targetStatus, Instant.now(), correct, scorePercentage);
    }

    private AssessmentDetailResponse buildInProgressView(Assessment assessment) {
        LoadedAssessmentData data = loadQuestionsWithOptionsAndAnswers(assessment.getId());
        List<AssessmentQuestionView> questionViews = data.questions().stream()
                .map(aq -> new AssessmentQuestionView(
                        aq.getId(),
                        aq.getQuestion().getQuestionText(),
                        aq.getQuestion().getQuestionType(),
                        aq.getQuestion().getDifficulty(),
                        aq.getDisplayOrder(),
                        aq.getPoints(),
                        data.optionsByAssessmentQuestion().getOrDefault(aq.getId(), List.of()).stream()
                                .map(AssessmentOptionView::from).toList(),
                        List.copyOf(selectedOptionIds(data.answerByAssessmentQuestion().get(aq.getId())))))
                .toList();

        Skill skill = assessment.getSkill();
        return new AssessmentDetailResponse(assessment.getId(), skill.getId(), skill.getName(),
                assessment.getAssessmentType(), assessment.getStatus(), assessment.getTotalQuestions(),
                assessment.getStartedAt(), assessment.getTimeLimitSeconds(), remainingSeconds(assessment), questionViews);
    }

    private AssessmentResultResponse buildResultView(Assessment assessment) {
        LoadedAssessmentData data = loadQuestionsWithOptionsAndAnswers(assessment.getId());
        List<AssessmentResultQuestionView> questionViews = data.questions().stream().map(aq -> {
            List<AssessmentQuestionOption> options = data.optionsByAssessmentQuestion().getOrDefault(aq.getId(), List.of());
            Set<UUID> correctIds = correctOptionIds(options);
            List<UUID> selected = List.copyOf(selectedOptionIds(data.answerByAssessmentQuestion().get(aq.getId())));
            return new AssessmentResultQuestionView(
                    aq.getId(), aq.getQuestion().getQuestionText(), aq.getQuestion().getQuestionType(),
                    aq.getQuestion().getDifficulty(), aq.getDisplayOrder(), aq.getPoints(),
                    options.stream().map(AssessmentResultOptionView::from).toList(),
                    selected, List.copyOf(correctIds), isCorrect(Set.copyOf(selected), correctIds),
                    aq.getQuestion().getExplanation());
        }).toList();

        Skill skill = assessment.getSkill();
        return new AssessmentResultResponse(assessment.getId(), skill.getId(), skill.getName(),
                assessment.getAssessmentType(), assessment.getStatus(), assessment.getTotalQuestions(),
                assessment.getCorrectCount(), assessment.getScorePercentage(), assessment.getStartedAt(),
                assessment.getSubmittedAt(), questionViews);
    }

    private LoadedAssessmentData loadQuestionsWithOptionsAndAnswers(UUID assessmentId) {
        List<AssessmentQuestion> questions = assessmentQuestionRepository.findAllByAssessmentIdOrderByDisplayOrderAsc(assessmentId);
        List<UUID> aqIds = questions.stream().map(AssessmentQuestion::getId).toList();
        Map<UUID, List<AssessmentQuestionOption>> optionsByAQ = aqIds.isEmpty() ? Map.of()
                : assessmentQuestionOptionRepository.findAllByAssessmentQuestionIdInOrderByDisplayOrderAsc(aqIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getAssessmentQuestion().getId()));
        Map<UUID, AssessmentAnswer> answerByAQ = assessmentAnswerRepository.findAllByAssessmentId(assessmentId).stream()
                .collect(Collectors.toMap(a -> a.getAssessmentQuestion().getId(), a -> a));
        return new LoadedAssessmentData(questions, optionsByAQ, answerByAQ);
    }

    private static Set<UUID> correctOptionIds(List<AssessmentQuestionOption> options) {
        return options.stream()
                .filter(o -> o.getQuestionOption().isCorrect())
                .map(o -> o.getQuestionOption().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<UUID> selectedOptionIds(AssessmentAnswer answer) {
        return answer == null ? Set.of() : answer.getSelectedOptionIds();
    }

    // Uniform correctness rule for MCQ_SINGLE/TRUE_FALSE/MCQ_MULTIPLE alike: exact set equality.
    // A singleton correct set makes this identical to "selected option == the correct option" for
    // single-answer types, and gives MCQ_MULTIPLE's required all-or-nothing scoring (spec §29) for
    // free — no per-type branching needed.
    private static boolean isCorrect(Set<UUID> selected, Set<UUID> correctOptionIds) {
        return !selected.isEmpty() && selected.equals(correctOptionIds);
    }

    private Integer remainingSeconds(Assessment assessment) {
        if (assessment.getTimeLimitSeconds() == null) {
            return null;
        }
        if (assessment.getStatus() != AssessmentStatus.IN_PROGRESS) {
            return 0;
        }
        long elapsed = Instant.now().getEpochSecond() - assessment.getStartedAt().getEpochSecond();
        return (int) Math.max(assessment.getTimeLimitSeconds() - elapsed, 0);
    }

    private record LoadedAssessmentData(List<AssessmentQuestion> questions,
            Map<UUID, List<AssessmentQuestionOption>> optionsByAssessmentQuestion,
            Map<UUID, AssessmentAnswer> answerByAssessmentQuestion) {
    }
}
