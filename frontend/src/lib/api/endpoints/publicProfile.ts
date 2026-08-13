import { apiFetch } from "@/lib/api/client";
import type { PublicProfileResponse, PublicProjectDetail, PublicServiceDetail } from "@/lib/api/types";

export function getPublicProfile(slug: string) {
  return apiFetch<PublicProfileResponse>(`/public/profiles/${encodeURIComponent(slug)}`, { auth: false });
}

export function getPublicProject(projectId: string) {
  return apiFetch<PublicProjectDetail>(`/public/projects/${encodeURIComponent(projectId)}`, { auth: false });
}

export function getPublicService(serviceId: string) {
  return apiFetch<PublicServiceDetail>(`/public/services/${encodeURIComponent(serviceId)}`, { auth: false });
}
