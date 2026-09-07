import { apiFetch } from "@/lib/api/client";
import type {
  CompanyType,
  PageResponse,
  PlacementCompanyResponse,
  PlacementProfileRequest,
  PlacementProfileResponse,
  PlacementRoleDetailResponse,
  PlacementRoleResponse,
  RoleSkillResponse,
} from "@/lib/api/placementTypes";

const PROFILE_BASE = "/placement/profile";

// Returns undefined when the student has not onboarded yet (backend answers 204 No Content,
// which apiFetch resolves to undefined) — a normal state, not an error.
export function getMyPlacementProfile() {
  return apiFetch<PlacementProfileResponse | undefined>(PROFILE_BASE);
}

// Upsert: creates the profile the first time, updates it on every later call. There is
// deliberately no separate create/update endpoint.
export function saveMyPlacementProfile(request: PlacementProfileRequest) {
  return apiFetch<PlacementProfileResponse>(PROFILE_BASE, { method: "PUT", body: request });
}

export function listPlacementRoles() {
  return apiFetch<PlacementRoleResponse[]>("/placement/roles");
}

export function getPlacementRole(id: string) {
  return apiFetch<PlacementRoleDetailResponse>(`/placement/roles/${id}`);
}

export function listPlacementRoleSkills(roleId: string) {
  return apiFetch<RoleSkillResponse[]>(`/placement/roles/${roleId}/skills`);
}

export interface PlacementCompanyListParams {
  companyType?: CompanyType;
  search?: string;
  page?: number;
  size?: number;
}

export function listPlacementCompanies(params: PlacementCompanyListParams) {
  const query = new URLSearchParams();
  if (params.companyType) query.set("companyType", params.companyType);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<PlacementCompanyResponse>>(`/placement/companies${qs ? `?${qs}` : ""}`);
}
