package com.studen.messaging;

import java.time.Instant;
import java.util.UUID;

// "mine" is a server-computed boolean (sender.id == the requesting principal), not a raw
// senderId — the frontend needs to know which side to align a bubble on, but never needs (and
// should never receive) either participant's internal user id.
public record MessageResponse(UUID id, String content, boolean mine, Instant createdAt, Instant readAt) {

    public static MessageResponse from(Message message, UUID viewerId) {
        return new MessageResponse(
                message.getId(),
                message.getContent(),
                message.getSender().getId().equals(viewerId),
                message.getCreatedAt(),
                message.getReadAt());
    }
}
