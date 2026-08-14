package com.studen.questionbank;

import java.util.UUID;

public record DuplicateWarningResponse(UUID existingQuestionId, String existingQuestionText) {
}
