package com.studen.resource;

// "What should I learn next" — a single projection of RoadmapService's roadmap computation.
// `message` is populated only when there is no `nextUp` (no weak areas yet, or every weak-topic
// resource is already completed) so the frontend never has to fabricate copy for an empty state.
public record RecommendationResponse(RoadmapItemResponse nextUp, String message) {

    public static RecommendationResponse none(String message) {
        return new RecommendationResponse(null, message);
    }
}
