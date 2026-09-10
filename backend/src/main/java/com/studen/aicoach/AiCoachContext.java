package com.studen.aicoach;

import com.studen.practical.CodingLanguage;
import com.studen.practical.PracticalType;
import com.studen.practical.execution.ExecutionJobStatus;
import java.util.List;

/**
 * Everything {@link AiCoachPromptBuilder} needs to build a prompt for one request — assembled by
 * {@link AiCoachService} from server-recorded state only. {@code visibleTestResults} deliberately
 * excludes every hidden {@code PracticalTestCase}/{@code ExecutionTestResult} row; hidden test
 * outcomes are collapsed to {@code hiddenTestsPassed}/{@code hiddenTestsTotal} counts only — the
 * same aggregate-only shape {@code RunResultResponse} already exposes to students, so nothing here
 * can leak a hidden test's input/expected/actual content into an LLM prompt.
 */
public record AiCoachContext(
        String questionTitle,
        String instructions,
        String requirements,
        String constraints,
        PracticalType practicalType,
        CodingLanguage language,
        String currentCode,
        ExecutionJobStatus lastExecutionStatus,
        String lastCompileError,
        String lastRuntimeError,
        List<VisibleTestResult> visibleTestResults,
        int hiddenTestsPassed,
        int hiddenTestsTotal,
        int hintLevel,
        List<PriorInteraction> priorInteractions,
        String skillName,
        String placementRoleName,
        String placementSeriesName,
        String topic) {

    public record VisibleTestResult(String input, String expectedOutput, String actualOutput, boolean passed) {
    }

    public record PriorInteraction(AiCoachActionType actionType, String responseText) {
    }
}
