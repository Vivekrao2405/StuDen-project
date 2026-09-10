package com.studen.aicoach;

import com.studen.common.exception.RateLimitExceededException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.placement.PlacementModuleItem;
import com.studen.placement.PlacementModuleItemRepository;
import com.studen.placement.PlacementSeries;
import com.studen.practical.PracticalAssessment;
import com.studen.practical.PracticalAttemptQuestion;
import com.studen.practical.PracticalAttemptQuestionRepository;
import com.studen.practical.PracticalQuestion;
import com.studen.practical.execution.ExecutionJob;
import com.studen.practical.execution.ExecutionJobRepository;
import com.studen.practical.execution.ExecutionTestResult;
import com.studen.practical.execution.ExecutionTestResultRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates one AI Coach request: ownership → rate limit → context assembly (server-recorded
 * state only) → prompt build → OpenAI call → persist. Ownership is verified by injecting
 * {@link PracticalAttemptQuestionRepository} directly and reusing its existing
 * {@code findByIdAndAttemptIdAndUserId} query — the same precedent
 * {@code com.studen.integrity.IntegrityEventService} already establishes for a sibling package,
 * rather than adding a new public method to {@code PracticalAttemptService}.
 */
@Service
public class AiCoachService {

    private final PracticalAttemptQuestionRepository attemptQuestionRepository;
    private final AiCoachInteractionRepository interactionRepository;
    private final ExecutionJobRepository executionJobRepository;
    private final ExecutionTestResultRepository executionTestResultRepository;
    private final PlacementModuleItemRepository moduleItemRepository;
    private final AiCoachPromptBuilder promptBuilder;
    private final OpenAiCoachClient openAiCoachClient;
    private final AiCoachProperties properties;

    public AiCoachService(PracticalAttemptQuestionRepository attemptQuestionRepository,
            AiCoachInteractionRepository interactionRepository, ExecutionJobRepository executionJobRepository,
            ExecutionTestResultRepository executionTestResultRepository,
            PlacementModuleItemRepository moduleItemRepository, AiCoachPromptBuilder promptBuilder,
            OpenAiCoachClient openAiCoachClient, AiCoachProperties properties) {
        this.attemptQuestionRepository = attemptQuestionRepository;
        this.interactionRepository = interactionRepository;
        this.executionJobRepository = executionJobRepository;
        this.executionTestResultRepository = executionTestResultRepository;
        this.moduleItemRepository = moduleItemRepository;
        this.promptBuilder = promptBuilder;
        this.openAiCoachClient = openAiCoachClient;
        this.properties = properties;
    }

    @Transactional
    public AiCoachInteractionResponse requestInteraction(UUID userId, UUID attemptId, UUID attemptQuestionId,
            AiCoachActionType actionType, String topic) {
        PracticalAttemptQuestion attemptQuestion = findOwnAttemptQuestion(userId, attemptId, attemptQuestionId);
        enforceRateLimit(userId);

        List<AiCoachInteraction> priorAll = interactionRepository
                .findAllByPracticalAttemptQuestionIdAndUserIdOrderByCreatedAtAsc(attemptQuestionId, userId);
        int hintLevel = actionType == AiCoachActionType.HINT
                ? (int) priorAll.stream().filter(i -> i.getActionType() == AiCoachActionType.HINT).count() + 1
                : 0;

        AiCoachContext context = buildContext(attemptQuestion, topic, hintLevel, priorAll);
        AiCoachPromptBuilder.PromptPair prompt = promptBuilder.build(actionType, context);
        String responseText = openAiCoachClient.complete(prompt.systemPrompt(), prompt.userPrompt());

        AiCoachInteraction interaction = new AiCoachInteraction(attemptQuestion.getPracticalAttempt().getUser(),
                attemptQuestion, actionType, actionType == AiCoachActionType.HINT ? hintLevel : null,
                actionType == AiCoachActionType.EXPLAIN_CONCEPT ? topic : null, responseText, properties.getModel());
        interactionRepository.save(interaction);
        return AiCoachInteractionResponse.from(interaction);
    }

    @Transactional(readOnly = true)
    public List<AiCoachInteractionResponse> getHistory(UUID userId, UUID attemptId, UUID attemptQuestionId) {
        findOwnAttemptQuestion(userId, attemptId, attemptQuestionId);
        return interactionRepository
                .findAllByPracticalAttemptQuestionIdAndUserIdOrderByCreatedAtAsc(attemptQuestionId, userId).stream()
                .map(AiCoachInteractionResponse::from).toList();
    }

    private PracticalAttemptQuestion findOwnAttemptQuestion(UUID userId, UUID attemptId, UUID attemptQuestionId) {
        return attemptQuestionRepository.findByIdAndAttemptIdAndUserId(attemptQuestionId, attemptId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Practical attempt question not found"));
    }

    private void enforceRateLimit(UUID userId) {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(properties.getWindowMinutes()));
        if (interactionRepository.countByUserIdAndCreatedAtAfter(userId, windowStart) >= properties.getMaxRequestsPerWindow()) {
            throw new RateLimitExceededException("You've reached the AI Coach limit for now. Please try again shortly.");
        }
    }

    private AiCoachContext buildContext(PracticalAttemptQuestion attemptQuestion, String topic, int hintLevel,
            List<AiCoachInteraction> priorAll) {
        PracticalQuestion question = attemptQuestion.getPracticalQuestion();
        PracticalAssessment assessment = question.getPracticalAssessment();

        ExecutionJob lastJob = executionJobRepository
                .findFirstByPracticalAttemptQuestionIdOrderByCreatedAtDesc(attemptQuestion.getId()).orElse(null);

        List<AiCoachContext.VisibleTestResult> visible = List.of();
        int hiddenPassed = 0;
        int hiddenTotal = 0;
        if (lastJob != null) {
            List<ExecutionTestResult> results = executionTestResultRepository
                    .findAllByExecutionJobIdOrderByCreatedAtAsc(lastJob.getId());
            visible = results.stream().filter(r -> !r.isHidden())
                    .map(r -> new AiCoachContext.VisibleTestResult(r.getTestCase().getInput(),
                            r.getTestCase().getExpectedOutput(), r.getActualOutput(), r.isPassed()))
                    .toList();
            List<ExecutionTestResult> hidden = results.stream().filter(ExecutionTestResult::isHidden).toList();
            hiddenTotal = hidden.size();
            hiddenPassed = (int) hidden.stream().filter(ExecutionTestResult::isPassed).count();
        }

        List<AiCoachContext.PriorInteraction> priorSummaries = priorAll.stream()
                .skip(Math.max(0, priorAll.size() - properties.getHistoryContextSize()))
                .map(i -> new AiCoachContext.PriorInteraction(i.getActionType(), i.getResponseText())).toList();

        String roleName = null;
        String seriesName = null;
        PlacementModuleItem moduleItem = moduleItemRepository.findFirstByPracticalAssessmentId(assessment.getId())
                .orElse(null);
        if (moduleItem != null) {
            PlacementSeries series = moduleItem.getModule().getSeries();
            seriesName = series.getName();
            roleName = series.getTargetRole().getName();
        }

        String skillName = question.getSkill() != null ? question.getSkill().getName() : assessment.getSkill().getName();

        return new AiCoachContext(question.getTitle(), question.getInstructions(), question.getRequirements(),
                question.getConstraints(), assessment.getPracticalType(), attemptQuestion.getSelectedLanguage(),
                attemptQuestion.getSubmissionContent(), lastJob == null ? null : lastJob.getStatus(),
                lastJob == null ? null : lastJob.getCompileError(), lastJob == null ? null : lastJob.getRuntimeError(),
                visible, hiddenPassed, hiddenTotal, hintLevel, priorSummaries, skillName, roleName, seriesName, topic);
    }
}
