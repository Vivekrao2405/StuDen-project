package com.studen.executionserver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompileRequest(
        @NotBlank String executionId,
        @NotNull CodingLanguage language,
        @NotBlank String sourceCode,
        @Positive int timeoutSeconds,
        @Positive int memoryLimitMb,
        @Positive double cpuLimit,
        @Positive int pidsLimit,
        @Positive int outputLimitKb) {
}
