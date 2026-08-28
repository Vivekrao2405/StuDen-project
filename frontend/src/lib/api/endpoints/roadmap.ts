import { apiFetch } from "@/lib/api/client";
import type { RecommendationResponse, RoadmapOverview, RoadmapResponse } from "@/lib/api/resourceTypes";

export function getRoadmap() {
  return apiFetch<RoadmapResponse>("/roadmap");
}

export function getRecommendations() {
  return apiFetch<RecommendationResponse>("/roadmap/recommendations");
}

export function getRoadmapProgress() {
  return apiFetch<RoadmapOverview>("/roadmap/progress");
}
