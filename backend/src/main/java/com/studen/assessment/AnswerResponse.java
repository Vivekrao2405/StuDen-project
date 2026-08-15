package com.studen.assessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnswerResponse(UUID assessmentQuestionId, List<UUID> selectedOptionIds, Instant answeredAt) {
}
