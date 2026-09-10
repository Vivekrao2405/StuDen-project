package com.studen.aicoach;

import java.time.Instant;
import java.util.UUID;

public record AiCoachInteractionResponse(
        UUID id,
        AiCoachActionType actionType,
        Integer hintLevel,
        String message,
        Instant createdAt) {

    public static AiCoachInteractionResponse from(AiCoachInteraction interaction) {
        return new AiCoachInteractionResponse(interaction.getId(), interaction.getActionType(),
                interaction.getHintLevel(), interaction.getResponseText(), interaction.getCreatedAt());
    }
}
