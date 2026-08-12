import { apiFetch } from "@/lib/api/client";
import type { PublicProfileResponse, PublicProjectDetail } from "@/lib/api/types";

export function getPublicProfile(slug: string) {
  return apiFetch<PublicProfileResponse>(`/public/profiles/${encodeURIComponent(slug)}`, { auth: false });
}

export function getPublicProject(projectId: string) {
  return apiFetch<PublicProjectDetail>(`/public/projects/${encodeURIComponent(projectId)}`, { auth: false });
}
