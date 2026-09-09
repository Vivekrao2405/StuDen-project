import { apiFetch } from "@/lib/api/client";
import type {
  CompanyType,
  PageResponse,
  PlacementAssessmentDetailResponse,
  PlacementAssessmentQuestionRequest,
  PlacementAssessmentQuestionResponse,
  PlacementAssessmentRequest,
  PlacementAssessmentResponse,
  PlacementCatalogStatus,
  PlacementCompanyRequest,
  PlacementCompanyResponse,
  PlacementContentStatus,
  PlacementRoleDetailResponse,
  PlacementRoleRequest,
  PlacementRoleResponse,
  RoleSkillRequest,
  RoleSkillResponse,
} from "@/lib/api/placementTypes";

const ROLES_BASE = "/admin/placement/roles";
const COMPANIES_BASE = "/admin/placement/companies";
const ASSESSMENTS_BASE = "/admin/placement/assessments";

// --- Roles --------------------------------------------------------------------------------

export function listAdminRoles(status?: PlacementCatalogStatus) {
  const qs = status ? `?status=${status}` : "";
  return apiFetch<PlacementRoleResponse[]>(`${ROLES_BASE}${qs}`);
}

export function getAdminRole(id: string) {
  return apiFetch<PlacementRoleDetailResponse>(`${ROLES_BASE}/${id}`);
}

export function createRole(request: PlacementRoleRequest) {
  return apiFetch<PlacementRoleDetailResponse>(ROLES_BASE, { method: "POST", body: request });
}

export function updateRole(id: string, request: PlacementRoleRequest) {
  return apiFetch<PlacementRoleDetailResponse>(`${ROLES_BASE}/${id}`, { method: "PUT", body: request });
}

export function deleteRole(id: string) {
  return apiFetch<void>(`${ROLES_BASE}/${id}`, { method: "DELETE" });
}

export function activateRole(id: string) {
  return apiFetch<PlacementRoleDetailResponse>(`${ROLES_BASE}/${id}/activate`, { method: "POST" });
}

export function deactivateRole(id: string) {
  return apiFetch<PlacementRoleDetailResponse>(`${ROLES_BASE}/${id}/deactivate`, { method: "POST" });
}

// --- Role -> Skill mapping ------------------------------------------------------------------

export function listRoleSkills(roleId: string) {
  return apiFetch<RoleSkillResponse[]>(`${ROLES_BASE}/${roleId}/skills`);
}

export function replaceRoleSkills(roleId: string, skills: RoleSkillRequest[]) {
  return apiFetch<RoleSkillResponse[]>(`${ROLES_BASE}/${roleId}/skills`, {
    method: "PUT",
    body: { skills },
  });
}

export function addRoleSkill(roleId: string, request: RoleSkillRequest) {
  return apiFetch<RoleSkillResponse>(`${ROLES_BASE}/${roleId}/skills`, { method: "POST", body: request });
}

export function removeRoleSkill(roleId: string, skillId: string) {
  return apiFetch<void>(`${ROLES_BASE}/${roleId}/skills/${skillId}`, { method: "DELETE" });
}

// --- Companies ----------------------------------------------------------------------------

export interface AdminCompanyListParams {
  companyType?: CompanyType;
  status?: PlacementCatalogStatus;
  search?: string;
  page?: number;
  size?: number;
}

export function listAdminCompanies(params: AdminCompanyListParams) {
  const query = new URLSearchParams();
  if (params.companyType) query.set("companyType", params.companyType);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<PlacementCompanyResponse>>(`${COMPANIES_BASE}${qs ? `?${qs}` : ""}`);
}

export function getAdminCompany(id: string) {
  return apiFetch<PlacementCompanyResponse>(`${COMPANIES_BASE}/${id}`);
}

export function createCompany(request: PlacementCompanyRequest) {
  return apiFetch<PlacementCompanyResponse>(COMPANIES_BASE, { method: "POST", body: request });
}

export function updateCompany(id: string, request: PlacementCompanyRequest) {
  return apiFetch<PlacementCompanyResponse>(`${COMPANIES_BASE}/${id}`, { method: "PUT", body: request });
}

export function deleteCompany(id: string) {
  return apiFetch<void>(`${COMPANIES_BASE}/${id}`, { method: "DELETE" });
}

export function activateCompany(id: string) {
  return apiFetch<PlacementCompanyResponse>(`${COMPANIES_BASE}/${id}/activate`, { method: "POST" });
}

export function deactivateCompany(id: string) {
  return apiFetch<PlacementCompanyResponse>(`${COMPANIES_BASE}/${id}/deactivate`, { method: "POST" });
}

// --- Readiness Assessments ------------------------------------------------------------------

export interface AdminAssessmentListParams {
  roleId?: string;
  status?: PlacementContentStatus;
  search?: string;
  page?: number;
  size?: number;
}

export function listAdminAssessments(params: AdminAssessmentListParams) {
  const query = new URLSearchParams();
  if (params.roleId) query.set("roleId", params.roleId);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<PlacementAssessmentResponse>>(`${ASSESSMENTS_BASE}${qs ? `?${qs}` : ""}`);
}

export function getAdminAssessment(id: string) {
  return apiFetch<PlacementAssessmentDetailResponse>(`${ASSESSMENTS_BASE}/${id}`);
}

export function createAssessment(request: PlacementAssessmentRequest) {
  return apiFetch<PlacementAssessmentDetailResponse>(ASSESSMENTS_BASE, { method: "POST", body: request });
}

export function updateAssessment(id: string, request: PlacementAssessmentRequest) {
  return apiFetch<PlacementAssessmentDetailResponse>(`${ASSESSMENTS_BASE}/${id}`, { method: "PUT", body: request });
}

export function deleteAssessment(id: string) {
  return apiFetch<void>(`${ASSESSMENTS_BASE}/${id}`, { method: "DELETE" });
}

export function publishAssessment(id: string) {
  return apiFetch<PlacementAssessmentDetailResponse>(`${ASSESSMENTS_BASE}/${id}/publish`, { method: "POST" });
}

export function unpublishAssessment(id: string) {
  return apiFetch<PlacementAssessmentDetailResponse>(`${ASSESSMENTS_BASE}/${id}/unpublish`, { method: "POST" });
}

export function archiveAssessment(id: string) {
  return apiFetch<PlacementAssessmentDetailResponse>(`${ASSESSMENTS_BASE}/${id}/archive`, { method: "POST" });
}

export function addAssessmentItem(assessmentId: string, request: PlacementAssessmentQuestionRequest) {
  return apiFetch<PlacementAssessmentQuestionResponse>(`${ASSESSMENTS_BASE}/${assessmentId}/questions`, {
    method: "POST",
    body: request,
  });
}

// itemId is the underlying Question or PracticalAssessment id (not the link row's own id).
export function removeAssessmentItem(assessmentId: string, itemId: string) {
  return apiFetch<void>(`${ASSESSMENTS_BASE}/${assessmentId}/questions/${itemId}`, { method: "DELETE" });
}
