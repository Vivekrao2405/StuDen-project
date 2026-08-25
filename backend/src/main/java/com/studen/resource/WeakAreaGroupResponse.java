package com.studen.resource;

import java.util.List;
import java.util.UUID;

// One group per weak skill — weakTags is empty when the weak signal came only from a practical
// assessment (PracticalQuestion carries no tags, see WeakAreaAggregationService). `percentage` is
// the worst (lowest) percentage among this skill's weak areas, used for the group's severity dot.
// `topics` breaks weakTags down into individual topics (see FocusAreaTopicResponse) for the My
// Learning "Focus Areas" cards — empty when every weak area here is skill-scoped only (practical) or
// none of the composite weak tags parsed out any topic segments.
public record WeakAreaGroupResponse(
        UUID skillId,
        String skillName,
        List<String> weakTags,
        int percentage,
        List<ResourceCardResponse> resources,
        int completedCount,
        int totalCount,
        List<FocusAreaTopicResponse> topics) {
}
