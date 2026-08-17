package com.studen.practical;

import com.studen.practical.judge.CodeJudgeResult;

public record RunResultResponse(CodeJudgeResult.CodeJudgeStatus status, String message) {

    public static RunResultResponse from(CodeJudgeResult result) {
        return new RunResultResponse(result.status(), result.message());
    }
}
