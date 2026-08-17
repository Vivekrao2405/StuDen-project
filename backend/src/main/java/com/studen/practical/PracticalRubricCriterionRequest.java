package com.studen.practical;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record PracticalRubricCriterionRequest(
        @NotBlank(message = "Criterion is required") String criterion,
        @Positive(message = "Max points must be greater than zero") int maxPoints,
        int displayOrder) {
}
