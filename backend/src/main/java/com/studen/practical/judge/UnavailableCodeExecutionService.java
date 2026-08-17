package com.studen.practical.judge;

import com.studen.practical.CodingLanguage;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The only {@link CodeExecutionService} implementation in this codebase. Performs zero code
 * execution — every method returns {@link CodeJudgeResult#unavailable} with a message telling the
 * student their submission was saved for manual review. This is a deliberate, permanent-for-now
 * choice (spec §9/§37/§67), not a TODO: swapping in real execution requires a genuinely isolated
 * sandbox/container service that this phase does not build.
 */
@Service
public class UnavailableCodeExecutionService implements CodeExecutionService {

    private static final String MESSAGE =
            "Automated code execution isn't available in this environment yet. Your submission has been saved and will be reviewed manually.";

    @Override
    public CodeJudgeResult submitCode(CodingLanguage language, String sourceCode, List<TestCaseInput> testCases) {
        return CodeJudgeResult.unavailable(MESSAGE);
    }

    @Override
    public CodeJudgeResult compile(CodingLanguage language, String sourceCode) {
        return CodeJudgeResult.unavailable(MESSAGE);
    }

    @Override
    public CodeJudgeResult execute(CodingLanguage language, String sourceCode, String stdin) {
        return CodeJudgeResult.unavailable(MESSAGE);
    }

    @Override
    public CodeJudgeResult evaluate(CodingLanguage language, String sourceCode, List<TestCaseInput> testCases) {
        return CodeJudgeResult.unavailable(MESSAGE);
    }
}
