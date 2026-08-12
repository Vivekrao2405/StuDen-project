import { apiFetch } from "@/lib/api/client";
import type { ProjectRequest, ProjectResponse } from "@/lib/api/types";

export function listProjects() {
  return apiFetch<ProjectResponse[]>("/users/me/projects");
}

export function createProject(payload: ProjectRequest) {
  return apiFetch<ProjectResponse>("/users/me/projects", { method: "POST", body: payload });
}

export function updateProject(id: string, payload: ProjectRequest) {
  return apiFetch<ProjectResponse>(`/users/me/projects/${id}`, { method: "PUT", body: payload });
}

export function deleteProject(id: string) {
  return apiFetch<void>(`/users/me/projects/${id}`, { method: "DELETE" });
}

export function uploadProjectMedia(projectId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<ProjectResponse>(`/users/me/projects/${projectId}/media`, { method: "POST", body: formData });
}

export function removeProjectMedia(projectId: string, mediaId: string) {
  return apiFetch<ProjectResponse>(`/users/me/projects/${projectId}/media/${mediaId}`, { method: "DELETE" });
}

export function reorderProjectMedia(projectId: string, mediaIds: string[]) {
  return apiFetch<ProjectResponse>(`/users/me/projects/${projectId}/media/order`, {
    method: "PUT",
    body: { mediaIds },
  });
}

export function setProjectCoverMedia(projectId: string, mediaId: string) {
  return apiFetch<ProjectResponse>(`/users/me/projects/${projectId}/media/${mediaId}/cover`, { method: "PUT" });
}
