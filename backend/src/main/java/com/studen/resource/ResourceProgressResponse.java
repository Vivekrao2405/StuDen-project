package com.studen.resource;

import java.time.Instant;
import java.util.UUID;

public record ResourceProgressResponse(UUID resourceId, ResourceProgressStatus status, Instant startedAt,
        Instant completedAt) {

    public static ResourceProgressResponse from(StudentResourceProgress progress) {
        return new ResourceProgressResponse(progress.getResource().getId(), progress.getStatus(),
                progress.getStartedAt(), progress.getCompletedAt());
    }
}
