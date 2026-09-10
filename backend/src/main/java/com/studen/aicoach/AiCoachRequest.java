package com.studen.aicoach;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// `topic` is only meaningful for EXPLAIN_CONCEPT (optional free text — if blank, the AI infers the
// most relevant concept from the problem itself). Ignored for every other action type.
public record AiCoachRequest(
        @NotNull AiCoachActionType actionType,
        @Size(max = 300) String topic) {
}
