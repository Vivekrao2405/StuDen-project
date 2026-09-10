package com.studen.aicoach;

import static org.assertj.core.api.Assertions.assertThat;

import com.studen.practical.CodingLanguage;
import com.studen.practical.PracticalType;
import com.studen.practical.execution.ExecutionJobStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link AiCoachPromptBuilder} — no Spring context, no I/O. Proves the
 * guardrails and hidden-test-safety properties directly on the built prompt string, since
 * {@link AiCoachContext} structurally has no field capable of carrying a hidden test case's own
 * input/expected/actual content (only an aggregate pass/fail count) — see {@link AiCoachContext}.
 */
class AiCoachPromptBuilderTest {

    private final AiCoachPromptBuilder builder = new AiCoachPromptBuilder();

    private AiCoachContext baseContext(int hintLevel, List<AiCoachContext.PriorInteraction> prior, String topic) {
        return new AiCoachContext("Two Sum", "Return indices of two numbers that add to target.",
                null, "1 <= n <= 10^4", PracticalType.CODING, CodingLanguage.PYTHON, "def solve(): pass",
                ExecutionJobStatus.COMPLETED, null, null,
                List.of(new AiCoachContext.VisibleTestResult("[2,7,11,15], 9", "[0,1]", "[0,2]", false)),
                3, 5, hintLevel, prior, "DSA", "SDE", "SDE Placement Prep", topic);
    }

    @Test
    void hint_onlyExposesHiddenTestsAsAnAggregateCount() {
        AiCoachPromptBuilder.PromptPair pair = builder.build(AiCoachActionType.HINT, baseContext(1, List.of(), null));

        assertThat(pair.userPrompt()).contains("Hidden tests (contents withheld): 3/5");
    }

    @Test
    void hint_systemPromptEmbedsHintLevelAndNeverRevealsFullSolutionGuardrail() {
        AiCoachPromptBuilder.PromptPair pair = builder.build(AiCoachActionType.HINT, baseContext(2, List.of(), null));

        assertThat(pair.systemPrompt()).contains("hint #2").contains("Never give the full solution as a hint");
    }

    @Test
    void solution_systemPromptAllowsFullExplainedSolution() {
        AiCoachPromptBuilder.PromptPair pair = builder.build(AiCoachActionType.SOLUTION, baseContext(0, List.of(), null));

        assertThat(pair.systemPrompt()).contains("requested the full solution").contains("complete worked solution");
    }

    @Test
    void explainConcept_withTopic_includesTopicInUserPrompt() {
        AiCoachPromptBuilder.PromptPair pair =
                builder.build(AiCoachActionType.EXPLAIN_CONCEPT, baseContext(0, List.of(), "HashMap"));

        assertThat(pair.userPrompt()).contains("Requested topic").contains("HashMap");
    }

    @Test
    void debug_whenNoExecutionYet_saysSoRatherThanFabricating() {
        AiCoachContext context = new AiCoachContext("Two Sum", "Return indices.", null, null, PracticalType.CODING,
                CodingLanguage.PYTHON, "def solve(): pass", null, null, null, List.of(), 0, 0, 0, List.of(), "DSA",
                null, null, null);

        AiCoachPromptBuilder.PromptPair pair = builder.build(AiCoachActionType.DEBUG, context);

        assertThat(pair.userPrompt()).contains("has not run their code yet");
        assertThat(pair.systemPrompt()).contains("Only state that the student's code produced");
    }

    @Test
    void priorInteractions_areIncludedForConversationalContinuity() {
        AiCoachContext context = baseContext(2,
                List.of(new AiCoachContext.PriorInteraction(AiCoachActionType.HINT, "Think about a frequency map.")),
                null);

        AiCoachPromptBuilder.PromptPair pair = builder.build(AiCoachActionType.HINT, context);

        assertThat(pair.userPrompt()).contains("Earlier AI Coach interactions").contains("frequency map");
    }
}
