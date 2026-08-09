import { apiFetch } from "@/lib/api/client";
import type { EducationRequest, EducationResponse } from "@/lib/api/types";

export function listEducation() {
  return apiFetch<EducationResponse[]>("/users/me/education");
}

export function createEducation(payload: EducationRequest) {
  return apiFetch<EducationResponse>("/users/me/education", { method: "POST", body: payload });
}

export function updateEducation(id: string, payload: EducationRequest) {
  return apiFetch<EducationResponse>(`/users/me/education/${id}`, { method: "PUT", body: payload });
}

export function deleteEducation(id: string) {
  return apiFetch<void>(`/users/me/education/${id}`, { method: "DELETE" });
}
