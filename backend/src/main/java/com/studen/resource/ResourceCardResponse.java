package com.studen.resource;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// The card shape used inside a WeakAreaGroupResponse's resource list on My Learning. createdAt
// (from Resource.BaseEntity) powers "Latest" sort; startedAt/completedAt (null unless the caller has
// a progress row) power the Continue Learning / Recently Completed sections without a second lookup.
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
        ResourceProgressStatus progressStatus,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {

    public static ResourceCardResponse from(Resource entity, ResourceProgressStatus progressStatus) {
        return from(entity, progressStatus, null, null);
    }

    public static ResourceCardResponse from(Resource entity, ResourceProgressStatus progressStatus,
            Instant startedAt, Instant completedAt) {
        return new ResourceCardResponse(entity.getId(), entity.getTitle(), entity.getDescription(),
                entity.getResourceType(), entity.getSkill().getId(), entity.getSkill().getName(),
                entity.getDifficulty(), entity.getEstimatedMinutes(), List.copyOf(entity.getTags()), progressStatus,
                entity.getCreatedAt(), startedAt, completedAt);
    }
}
