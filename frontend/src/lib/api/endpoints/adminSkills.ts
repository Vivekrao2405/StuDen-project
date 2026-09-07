import { apiFetch } from "@/lib/api/client";
import type { AdminSkillResponse, PageResponse } from "@/lib/api/placementTypes";

const BASE = "/admin/skills";

export interface AdminSkillListParams {
  search?: string;
  category?: string;
  page?: number;
  size?: number;
}

export function listAdminSkills(params: AdminSkillListParams) {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.category) query.set("category", params.category);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<AdminSkillResponse>>(`${BASE}${qs ? `?${qs}` : ""}`);
}

export function getAdminSkill(id: string) {
  return apiFetch<AdminSkillResponse>(`${BASE}/${id}`);
}

export function createAdminSkill(name: string, category: string) {
  return apiFetch<AdminSkillResponse>(BASE, { method: "POST", body: { name, category } });
}

export function updateAdminSkill(id: string, name: string, category: string) {
  return apiFetch<AdminSkillResponse>(`${BASE}/${id}`, { method: "PUT", body: { name, category } });
}

export function deleteAdminSkill(id: string) {
  return apiFetch<void>(`${BASE}/${id}`, { method: "DELETE" });
}
