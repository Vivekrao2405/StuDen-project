import { apiFetch } from "@/lib/api/client";
import type { SkillResponse } from "@/lib/api/types";

export function searchSkills(query: string) {
  return apiFetch<SkillResponse[]>(`/skills/search?q=${encodeURIComponent(query)}`);
}
