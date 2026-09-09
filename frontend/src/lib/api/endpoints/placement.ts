import { apiFetch } from "@/lib/api/client";
import type {
  CompanyType,
  PageResponse,
  PlacementAttemptDetailResponse,
  PlacementAttemptReviewResponse,
  PlacementAttemptSummaryResponse,
  PlacementCompanyResponse,
  PlacementLearningPlanResponse,
  PlacementProfileRequest,
  PlacementProfileResponse,
  PlacementReadinessResultResponse,
  PlacementReadinessStatusResponse,
  PlacementRoleDetailResponse,
  PlacementRoleResponse,
  RoleSkillResponse,
} from "@/lib/api/placementTypes";
import type { ResourceCard } from "@/lib/api/resourceTypes";

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

// --- Placement Readiness (Phase 3) --------------------------------------------------------------

const READINESS_BASE = "/placement/readiness";

// Backs the Placement Readiness entry screen: profile/target-role/assessment availability, any
// resumable in-progress attempt, and the latest completed result.
export function getPlacementReadinessStatus() {
  return apiFetch<PlacementReadinessStatusResponse>(`${READINESS_BASE}/status`);
}

export function listMyPlacementAttempts() {
  return apiFetch<PlacementAttemptSummaryResponse[]>(`${READINESS_BASE}/attempts`);
}

// Starts a brand-new readiness attempt for the caller's own target role, or transparently resumes
// an existing IN_PROGRESS one — the backend decides which.
export function startPlacementReadinessAttempt() {
  return apiFetch<PlacementAttemptDetailResponse>(`${READINESS_BASE}/attempts`, { method: "POST" });
}

// Returns PlacementAttemptDetailResponse while IN_PROGRESS, PlacementAttemptReviewResponse once
// terminal — callers branch on `status` to decide which shape they actually got.
export function getPlacementReadinessAttempt(attemptId: string) {
  return apiFetch<PlacementAttemptDetailResponse | PlacementAttemptReviewResponse>(
    `${READINESS_BASE}/attempts/${attemptId}`
  );
}

export function savePlacementReadinessAnswer(attemptId: string, attemptQuestionId: string, selectedOptionIds: string[]) {
  return apiFetch<{ attemptQuestionId: string; selectedOptionIds: string[]; answeredAt: string }>(
    `${READINESS_BASE}/attempts/${attemptId}/questions/${attemptQuestionId}/answer`,
    { method: "PATCH", body: { selectedOptionIds } }
  );
}

export function submitPlacementReadinessAttempt(attemptId: string) {
  return apiFetch<PlacementAttemptReviewResponse>(`${READINESS_BASE}/attempts/${attemptId}/submit`, { method: "POST" });
}

// The scored, skill-broken-down, gap-ranked summary — only valid once the attempt is terminal (the
// backend 409s otherwise).
export function getPlacementReadinessResult(attemptId: string) {
  return apiFetch<PlacementReadinessResultResponse>(`${READINESS_BASE}/attempts/${attemptId}/result`);
}

// 204/undefined when the student has never completed a readiness attempt yet.
export function getLatestPlacementReadinessResult() {
  return apiFetch<PlacementReadinessResultResponse | undefined>(`${READINESS_BASE}/latest-result`);
}

// --- Phase 4: Placement Learning Plan (Placement -> My Learning integration) --------------------

const LEARNING_PLAN_BASE = "/placement/learning-plan";

// Also serves as "latest placement learning recommendations" — always derived from the student's
// latest terminal readiness attempt.
export function getPlacementLearningPlan() {
  return apiFetch<PlacementLearningPlanResponse>(LEARNING_PLAN_BASE);
}

// Recommendations scoped to one specific current priority-gap skill (404s if that skill isn't one
// of the caller's current gaps) — backs the Placement Readiness result page's "Improve this skill"
// entry point into My Learning.
export function getPlacementLearningPlanForSkill(skillId: string) {
  return apiFetch<ResourceCard[]>(`${LEARNING_PLAN_BASE}/skills/${skillId}/resources`);
}
