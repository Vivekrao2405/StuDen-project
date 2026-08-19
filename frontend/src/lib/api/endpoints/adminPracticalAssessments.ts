import { apiFetch } from "@/lib/api/client";
import type {
  AdminIntegrityTimelineEntry,
  AdminPracticalAssessmentListParams,
  AdminPracticalAttemptDetail,
  AdminPracticalAttemptSummary,
  EvaluateAttemptRequest,
  IntegrityOverrideRequest,
  IntegritySummary,
  PageResponse,
  PracticalAssessmentDetail,
  PracticalAssessmentRequest,
  PracticalAssessmentSummary,
  PracticalAttemptStatus,
} from "@/lib/api/practicalTypes";

export function listAdminPracticalAssessments(params: AdminPracticalAssessmentListParams) {
  const query = new URLSearchParams();
  if (params.skillId) query.set("skillId", params.skillId);
  if (params.practicalType) query.set("practicalType", params.practicalType);
  if (params.difficulty) query.set("difficulty", params.difficulty);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<PracticalAssessmentSummary>>(`/admin/practical-assessments${qs ? `?${qs}` : ""}`);
}

export function getAdminPracticalAssessment(id: string) {
  return apiFetch<PracticalAssessmentDetail>(`/admin/practical-assessments/${id}`);
}

export function createPracticalAssessment(payload: PracticalAssessmentRequest) {
  return apiFetch<PracticalAssessmentDetail>("/admin/practical-assessments", { method: "POST", body: payload });
}

export function updatePracticalAssessment(id: string, payload: PracticalAssessmentRequest) {
  return apiFetch<PracticalAssessmentDetail>(`/admin/practical-assessments/${id}`, { method: "PUT", body: payload });
}

export function deletePracticalAssessment(id: string) {
  return apiFetch<void>(`/admin/practical-assessments/${id}`, { method: "DELETE" });
}

export function submitPracticalAssessmentForReview(id: string) {
  return apiFetch<PracticalAssessmentDetail>(`/admin/practical-assessments/${id}/submit-review`, { method: "POST" });
}

export function publishPracticalAssessment(id: string) {
  return apiFetch<PracticalAssessmentDetail>(`/admin/practical-assessments/${id}/publish`, { method: "POST" });
}

export function archivePracticalAssessment(id: string) {
  return apiFetch<PracticalAssessmentDetail>(`/admin/practical-assessments/${id}/archive`, { method: "POST" });
}

export function createNewPracticalAssessmentVersion(id: string) {
  return apiFetch<PracticalAssessmentDetail>(`/admin/practical-assessments/${id}/new-version`, { method: "POST" });
}

export function listPracticalAttemptQueue(status: PracticalAttemptStatus | undefined, page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) query.set("status", status);
  return apiFetch<PageResponse<AdminPracticalAttemptSummary>>(`/admin/practical-attempts?${query.toString()}`);
}

export function getAdminPracticalAttempt(id: string) {
  return apiFetch<AdminPracticalAttemptDetail>(`/admin/practical-attempts/${id}`);
}

export function evaluatePracticalAttempt(id: string, request: EvaluateAttemptRequest) {
  return apiFetch<AdminPracticalAttemptDetail>(`/admin/practical-attempts/${id}/evaluate`, { method: "POST", body: request });
}

// Phase 7.6 Assessment Integrity — merged chronological timeline for manual review.
export function getIntegrityTimeline(attemptId: string) {
  return apiFetch<AdminIntegrityTimelineEntry[]>(`/admin/practical-attempts/${attemptId}/integrity-timeline`);
}

export function overrideIntegrity(attemptId: string, request: IntegrityOverrideRequest) {
  return apiFetch<IntegritySummary>(`/admin/practical-attempts/${attemptId}/integrity-override`, {
    method: "POST",
    body: request,
  });
}
