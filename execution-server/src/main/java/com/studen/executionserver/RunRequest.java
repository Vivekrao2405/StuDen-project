package com.studen.executionserver;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// No `language` field -- /run operates on a workspace already created by a prior /compile call
// for the same executionId, which already recorded the language.
public record RunRequest(
        @NotBlank String executionId,
        @NotNull String stdin,
        @Positive int timeoutSeconds,
        @Positive int memoryLimitMb,
        @Positive double cpuLimit,
        @Positive int pidsLimit,
        @Positive int outputLimitKb) {
}
