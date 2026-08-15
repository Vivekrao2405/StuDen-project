package com.studen.assessment;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartAssessmentRequest(@NotNull UUID skillId) {
}
