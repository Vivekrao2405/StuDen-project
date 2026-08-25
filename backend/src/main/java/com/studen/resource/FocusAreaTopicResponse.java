package com.studen.resource;

// One row per weak topic parsed out of a skill's weak tags (see com.studen.common.tag.TagParser) —
// powers the My Learning "Focus Areas" progress cards. completedCount/totalCount are computed
// against the *uncapped* published-resource set for the skill (unlike WeakAreaGroupResponse.resources,
// which is capped by LearningProperties.maxResourcesPerGroup), so the progress fraction is real, not
// an artifact of the "don't flood the student" cap. A topic with zero matching published resources is
// omitted entirely rather than shown as a fake 0/0 card.
public record FocusAreaTopicResponse(String topic, int percentage, int completedCount, int totalCount) {
}
