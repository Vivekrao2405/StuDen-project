package com.studen.assessment;

import java.util.List;

// Deterministic, rule-based only (spec §22: no AI here). strongTopics/needsImprovementTopics are
// topic names in TopicPerformanceView order; a topic in the DEVELOPING tier appears in neither
// list (visible only via the full topicPerformance array), matching the spec's example summary.
public record PerformanceSummaryView(int overallPercentage, AssessmentLevel level, List<String> strongTopics,
        List<String> needsImprovementTopics) {
}
