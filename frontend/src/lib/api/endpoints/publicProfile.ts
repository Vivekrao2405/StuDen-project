import { apiFetch } from "@/lib/api/client";
import type { PublicProfileResponse } from "@/lib/api/types";

export function getPublicProfile(slug: string) {
  return apiFetch<PublicProfileResponse>(`/public/profiles/${encodeURIComponent(slug)}`, { auth: false });
}
