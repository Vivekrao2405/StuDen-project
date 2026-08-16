package com.studen.assessment;

import java.util.UUID;

// topicId is null for questions with no topic assigned — grouped together under the "General"
// bucket (topicName) rather than being dropped, per spec §24's "missing topic" edge case.
public record TopicPerformanceView(UUID topicId, String topicName, int correctCount, int totalQuestions,
        int percentage, TopicPerformanceTier tier) {
}
