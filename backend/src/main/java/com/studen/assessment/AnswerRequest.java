package com.studen.assessment;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record AnswerRequest(@NotEmpty List<UUID> selectedOptionIds) {
}
