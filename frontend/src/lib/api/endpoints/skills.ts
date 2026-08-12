import { apiFetch } from "@/lib/api/client";
import type { SkillResponse } from "@/lib/api/types";

export function searchSkills(query: string) {
  return apiFetch<SkillResponse[]>(`/skills/search?q=${encodeURIComponent(query)}`);
}

export function getSkillCategories() {
  return apiFetch<string[]>("/skills/categories");
}

export function createSkill(name: string, category: string) {
  return apiFetch<SkillResponse>("/skills", { method: "POST", body: { name, category } });
}
