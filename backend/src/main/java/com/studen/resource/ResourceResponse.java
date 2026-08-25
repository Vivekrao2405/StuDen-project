package com.studen.resource;

import com.studen.questionbank.Difficulty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Student-facing single-resource shape (GET /api/v1/resources/{id}) — no filePublicId (internal
// storage reference, never exposed to students), no status (only PUBLISHED resources are ever
// returned by this endpoint in the first place). Carries the caller's own progress inline (including
// startedAt/completedAt, for the detail page's "Last read"/completion-date line) so the detail page
// never needs a second round-trip.
public record ResourceResponse(
        UUID id,
        String title,
        String description,
        ResourceType resourceType,
        UUID skillId,
        String skillName,
        Difficulty difficulty,
        Integer estimatedMinutes,
        String fileUrl,
        String externalUrl,
        String notesContent,
        List<String> tags,
        ResourceProgressStatus progressStatus,
        Instant startedAt,
        Instant completedAt) {

    public static ResourceResponse from(Resource entity, ResourceProgressStatus progressStatus) {
        return from(entity, progressStatus, null, null);
    }

    public static ResourceResponse from(Resource entity, ResourceProgressStatus progressStatus, Instant startedAt,
            Instant completedAt) {
        return new ResourceResponse(entity.getId(), entity.getTitle(), entity.getDescription(),
                entity.getResourceType(), entity.getSkill().getId(), entity.getSkill().getName(),
                entity.getDifficulty(), entity.getEstimatedMinutes(), entity.getFileUrl(), entity.getExternalUrl(),
                entity.getNotesContent(), List.copyOf(entity.getTags()), progressStatus, startedAt, completedAt);
    }
}
