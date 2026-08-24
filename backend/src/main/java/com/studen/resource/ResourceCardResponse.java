package com.studen.resource;

import com.studen.questionbank.Difficulty;
import java.util.List;
import java.util.UUID;

// The card shape used inside a WeakAreaGroupResponse's resource list on My Learning.
public record ResourceCardResponse(
        UUID id,
        String title,
        String description,
        ResourceType resourceType,
        UUID skillId,
        String skillName,
        Difficulty difficulty,
        Integer estimatedMinutes,
        List<String> tags,
        ResourceProgressStatus progressStatus) {

    public static ResourceCardResponse from(Resource entity, ResourceProgressStatus progressStatus) {
        return new ResourceCardResponse(entity.getId(), entity.getTitle(), entity.getDescription(),
                entity.getResourceType(), entity.getSkill().getId(), entity.getSkill().getName(),
                entity.getDifficulty(), entity.getEstimatedMinutes(), List.copyOf(entity.getTags()), progressStatus);
    }
}
