package com.studen.questionbank;

import java.util.UUID;

public record TopicResponse(UUID id, UUID skillId, String name, int displayOrder) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getSkill().getId(), topic.getName(), topic.getDisplayOrder());
    }
}
