package com.studen.resource;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Admin list row shape.
public record ResourceSummaryResponse(
        UUID id,
        String title,
        ResourceType resourceType,
        UUID skillId,
        String skillName,
        Difficulty difficulty,
        Integer estimatedMinutes,
        ResourceStatus status,
        List<String> tags,
        Instant createdAt) {

    public static ResourceSummaryResponse from(Resource entity) {
        return new ResourceSummaryResponse(entity.getId(), entity.getTitle(), entity.getResourceType(),
                entity.getSkill().getId(), entity.getSkill().getName(), entity.getDifficulty(),
                entity.getEstimatedMinutes(), entity.getStatus(), List.copyOf(entity.getTags()), entity.getCreatedAt());
    }
}
