package com.studen.communication;

import java.time.Instant;
import java.util.UUID;

public record SegmentResponse(
        UUID id, String name, String description, String filterJson, String createdByName, Instant createdAt) {

    public static SegmentResponse from(CommunicationSegment s) {
        return new SegmentResponse(s.getId(), s.getName(), s.getDescription(), s.getFilterJson(),
                s.getCreatedBy().getFullName(), s.getCreatedAt());
    }
}
