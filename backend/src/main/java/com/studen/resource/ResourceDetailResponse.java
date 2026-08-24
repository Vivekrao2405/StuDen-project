package com.studen.resource;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Admin-only detail shape — includes filePublicId (needed by the editor to know a file is
// already attached) and status. Never returned from a student-facing endpoint; see
// ResourceResponse for that shape.
public record ResourceDetailResponse(
        UUID id,
        String title,
        String description,
        ResourceType resourceType,
        UUID skillId,
        String skillName,
        Difficulty difficulty,
        Integer estimatedMinutes,
        String fileUrl,
        String filePublicId,
        String externalUrl,
        String notesContent,
        List<String> tags,
        ResourceStatus status,
        Instant createdAt) {

    public static ResourceDetailResponse from(Resource entity) {
        return new ResourceDetailResponse(entity.getId(), entity.getTitle(), entity.getDescription(),
                entity.getResourceType(), entity.getSkill().getId(), entity.getSkill().getName(),
                entity.getDifficulty(), entity.getEstimatedMinutes(), entity.getFileUrl(), entity.getFilePublicId(),
                entity.getExternalUrl(), entity.getNotesContent(), List.copyOf(entity.getTags()), entity.getStatus(),
                entity.getCreatedAt());
    }
}
