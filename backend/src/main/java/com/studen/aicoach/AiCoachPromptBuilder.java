package com.studen.aicoach;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Pure prompt assembly — one system-prompt fragment per {@link AiCoachActionType} layered on a
 * shared guardrail preamble (spec §18), plus a user-prompt built entirely from
 * {@link AiCoachContext}. Contains no I/O and no persistence, so it's directly unit-testable
 * (including proving hidden test content never appears in a built prompt).
 */
@Component
public class AiCoachPromptBuilder {

    public record PromptPair(String systemPrompt, String userPrompt) {
    }

    private static final String GUARDRAILS = """
            You are StuDen's AI Coach, helping a student practice a coding problem as part of their \
            placement preparation. Follow these rules strictly:
            - You are never given hidden test cases — none exist in your context. Never claim to know \
            or describe a "hidden test" beyond an aggregate pass/fail count if one is provided.
            - Only state that the student's code produced a specific output, passed, or failed when a \
            real recorded execution result is given to you below. Otherwise, speak in terms of your own \
            analysis ("I think the issue may be...", "This looks like it could...") — never claim a fact \
            you were not actually given.
            - Never fabricate test results, execution output, or company-specific interview requirements.
            - Be encouraging, concise, and focused on teaching reasoning rather than just handing over an \
            answer, except when the student has explicitly requested the full solution.
            """;

    public PromptPair build(AiCoachActionType actionType, AiCoachContext context) {
        String systemPrompt = GUARDRAILS + "\n" + actionInstructions(actionType, context);
        String userPrompt = buildUserPrompt(context);
        return new PromptPair(systemPrompt, userPrompt);
    }

    private String actionInstructions(AiCoachActionType actionType, AiCoachContext context) {
        return switch (actionType) {
            case HINT -> """
                    The student has requested hint #%d for this problem. Give exactly ONE small, \
                    progressive hint appropriate to that depth — nudge their thinking without revealing \
                    the full approach or any code. If earlier hints are listed in the conversation \
                    history below, build on them and do not repeat them. Never give the full solution as \
                    a hint, no matter how high the hint number is — if the student seems to need it, \
                    gently suggest they can request the full Solution instead.
                    """.formatted(context.hintLevel());
            case EXPLAIN_PATTERN -> """
                    Explain the underlying algorithmic / problem-solving pattern this problem uses \
                    (for example: two pointers, sliding window, hashing, binary search, BFS, DFS, dynamic \
                    programming, greedy, divide and conquer). Cover: what the pattern is, how to recognize \
                    when it applies, why it applies to this specific problem, and how to start approaching \
                    this problem using it. Do not write the full solution code.
                    """;
            case DEBUG -> """
                    Help the student debug the code below. If a real recorded execution result is \
                    provided, ground your analysis in it explicitly (quote the actual error/output). \
                    First explain what is likely wrong, then guide them toward how to fix it — do not \
                    rewrite their entire solution for them; point them at the fix, do not hand it to them \
                    fully solved unless they ask for the Solution action instead.
                    """;
            case EXPLAIN_CONCEPT -> """
                    Explain the relevant programming/CS concept in a clear, student-friendly way, \
                    connecting it to the current problem where possible. If a specific topic is given \
                    below, explain that topic; if none is given, infer and explain the single concept \
                    most relevant to solving this problem.
                    """;
            case SOLUTION -> """
                    The student has explicitly requested the full solution. Provide a complete worked \
                    solution: your approach, the reasoning behind it, its time and space complexity, and \
                    any important implementation details — followed by clean, working example code in the \
                    student's current language if known. Explain your reasoning; do not just dump \
                    unexplained code.
                    """;
        };
    }

    private String buildUserPrompt(AiCoachContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Problem\n");
        sb.append("Title: ").append(nullSafe(context.questionTitle())).append('\n');
        if (context.skillName() != null) {
            sb.append("Skill: ").append(context.skillName()).append('\n');
        }
        if (context.placementRoleName() != null || context.placementSeriesName() != null) {
            sb.append("Placement context: ");
            if (context.placementRoleName() != null) {
                sb.append(context.placementRoleName()).append(' ');
            }
            if (context.placementSeriesName() != null) {
                sb.append("— ").append(context.placementSeriesName());
            }
            sb.append('\n');
        }
        sb.append("Practical type: ").append(context.practicalType()).append('\n');
        sb.append("\nInstructions:\n").append(nullSafe(context.instructions())).append('\n');
        if (context.requirements() != null && !context.requirements().isBlank()) {
            sb.append("\nRequirements:\n").append(context.requirements()).append('\n');
        }
        if (context.constraints() != null && !context.constraints().isBlank()) {
            sb.append("\nConstraints:\n").append(context.constraints()).append('\n');
        }

        sb.append("\n## Student's current code");
        if (context.language() != null) {
            sb.append(" (").append(context.language()).append(')');
        }
        sb.append("\n");
        sb.append(context.currentCode() == null || context.currentCode().isBlank()
                ? "(no code written yet)"
                : context.currentCode());
        sb.append('\n');

        sb.append("\n## Most recent recorded execution result\n");
        if (context.lastExecutionStatus() == null) {
            sb.append("The student has not run their code yet — there is no execution result to report.\n");
        } else {
            sb.append("Status: ").append(context.lastExecutionStatus()).append('\n');
            if (context.lastCompileError() != null && !context.lastCompileError().isBlank()) {
                sb.append("Compile error: ").append(context.lastCompileError()).append('\n');
            }
            if (context.lastRuntimeError() != null && !context.lastRuntimeError().isBlank()) {
                sb.append("Runtime error: ").append(context.lastRuntimeError()).append('\n');
            }
            List<AiCoachContext.VisibleTestResult> visible = context.visibleTestResults();
            if (visible != null && !visible.isEmpty()) {
                long visiblePassed = visible.stream().filter(AiCoachContext.VisibleTestResult::passed).count();
                sb.append("Visible tests passed: ").append(visiblePassed).append('/').append(visible.size())
                        .append('\n');
                sb.append("Visible test case details:\n");
                for (int i = 0; i < visible.size(); i++) {
                    AiCoachContext.VisibleTestResult t = visible.get(i);
                    sb.append("  Test ").append(i + 1).append(" [").append(t.passed() ? "PASSED" : "FAILED")
                            .append("] input=").append(truncate(t.input())).append(" expected=")
                            .append(truncate(t.expectedOutput())).append(" actual=")
                            .append(truncate(t.actualOutput())).append('\n');
                }
            }
            if (context.hiddenTestsTotal() > 0) {
                // Aggregate only — never the hidden cases' own input/expected/actual content.
                sb.append("Hidden tests (contents withheld): ").append(context.hiddenTestsPassed()).append('/')
                        .append(context.hiddenTestsTotal()).append(" passed\n");
            }
        }

        if (context.priorInteractions() != null && !context.priorInteractions().isEmpty()) {
            sb.append("\n## Earlier AI Coach interactions on this question (most recent last)\n");
            for (AiCoachContext.PriorInteraction prior : context.priorInteractions()) {
                sb.append("- [").append(prior.actionType()).append("] ").append(truncate(prior.responseText()))
                        .append('\n');
            }
        }

        if (context.topic() != null && !context.topic().isBlank()) {
            sb.append("\n## Requested topic\n").append(context.topic()).append('\n');
        }

        return sb.toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value) {
        if (value == null) {
            return "(none)";
        }
        String trimmed = value.strip();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "…" : trimmed;
    }
}
