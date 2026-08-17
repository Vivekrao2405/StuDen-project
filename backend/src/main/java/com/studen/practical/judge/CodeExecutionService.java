package com.studen.practical.judge;

import com.studen.practical.CodingLanguage;
import java.util.List;

/**
 * Abstraction for compiling/running/evaluating student-submitted code against a
 * {@link com.studen.practical.PracticalTestCase} set. Spec §9/§37 are explicit: student code must
 * NEVER execute inside the main Spring Boot process or against production infrastructure — a real
 * implementation of this interface would call out to an isolated sandbox/container service (CPU/
 * memory/time/process limits, no network, filesystem isolation) that does not exist in this
 * codebase yet.
 *
 * <p>{@link UnavailableCodeExecutionService} is the only implementation registered — it performs
 * no execution at all and honestly reports that automated judging isn't available, routing every
 * CODING/SQL attempt to manual admin review instead. Do not implement a "real" version of this
 * interface by shelling out from this process — that is exactly what this abstraction exists to
 * prevent.
 */
public interface CodeExecutionService {

    CodeJudgeResult submitCode(CodingLanguage language, String sourceCode, List<TestCaseInput> testCases);

    CodeJudgeResult compile(CodingLanguage language, String sourceCode);

    CodeJudgeResult execute(CodingLanguage language, String sourceCode, String stdin);

    CodeJudgeResult evaluate(CodingLanguage language, String sourceCode, List<TestCaseInput> testCases);

    record TestCaseInput(String input, String expectedOutput, boolean hidden) {
    }
}
