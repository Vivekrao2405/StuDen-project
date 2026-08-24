import { apiFetch } from "@/lib/api/client";
import type { MyLearningResponse, ResourceProgress, StudentResource } from "@/lib/api/resourceTypes";

export function getMyLearning() {
  return apiFetch<MyLearningResponse>("/resources/my-learning");
}

export function getResource(id: string) {
  return apiFetch<StudentResource>(`/resources/${id}`);
}

export function startResource(id: string) {
  return apiFetch<ResourceProgress>(`/resources/${id}/start`, { method: "POST" });
}

export function completeResource(id: string) {
  return apiFetch<ResourceProgress>(`/resources/${id}/complete`, { method: "POST" });
}
