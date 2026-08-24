package com.studen.resource;

import java.util.UUID;

// tagScoped=true: tagOrLabel is a real weak MCQ topic tag, matched exactly against Resource.tags.
// tagScoped=false: tagOrLabel is just the skill name for display (practical-sourced — see
// WeakAreaAggregationService's javadoc for why practical carries no tag granularity); it only
// ever drives a same-skill match, never a tag match.
public record WeakAreaView(UUID skillId, String skillName, String tagOrLabel, boolean tagScoped, int percentage) {
}
