import { apiFetch } from "@/lib/api/client";
import type {
  AdminTestRunRequest,
  AdminTestRunResult,
  ExecutionJobSummary,
  MyPracticalAttemptSummary,
  PageResponse,
  PracticalAssessmentListParams,
  PracticalAssessmentSummary,
  PracticalAttempt,
  PracticalAttemptResult,
  PracticalEvidence,
  RunResult,
  SaveAttemptRequest,
  StudentPracticalAssessment,
} from "@/lib/api/practicalTypes";

export function listPracticalAssessments(params: PracticalAssessmentListParams) {
  const query = new URLSearchParams();
  if (params.skillId) query.set("skillId", params.skillId);
  if (params.practicalType) query.set("practicalType", params.practicalType);
  if (params.difficulty) query.set("difficulty", params.difficulty);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<PracticalAssessmentSummary>>(`/practical-assessments${qs ? `?${qs}` : ""}`);
}

export function getPracticalAssessment(id: string) {
  return apiFetch<StudentPracticalAssessment>(`/practical-assessments/${id}`);
}

export function startPracticalAttempt(assessmentId: string) {
  return apiFetch<PracticalAttempt>(`/practical-assessments/${assessmentId}/attempts`, { method: "POST" });
}

export function getPracticalAttempt(attemptId: string) {
  return apiFetch<PracticalAttempt | PracticalAttemptResult>(`/practical-attempts/${attemptId}`);
}

export function savePracticalAttempt(attemptId: string, request: SaveAttemptRequest) {
  return apiFetch<PracticalAttempt>(`/practical-attempts/${attemptId}`, { method: "PATCH", body: request });
}

export function submitPracticalAttempt(attemptId: string) {
  return apiFetch<PracticalAttemptResult>(`/practical-attempts/${attemptId}/submit`, { method: "POST" });
}

export function runPracticalAttempt(attemptId: string) {
  return apiFetch<RunResult>(`/practical-attempts/${attemptId}/run`, { method: "POST" });
}

export function getExecutionHistory(attemptId: string) {
  return apiFetch<ExecutionJobSummary[]>(`/practical-attempts/${attemptId}/executions`);
}

// Admin-only "Test Question" tool — runs every test case (public + hidden) for a given
// solution/query, no PracticalAttempt involved.
export function adminTestRunPracticalAssessment(assessmentId: string, request: AdminTestRunRequest) {
  return apiFetch<AdminTestRunResult>(`/admin/practical-assessments/${assessmentId}/test-run`, {
    method: "POST",
    body: request,
  });
}

export function listMyPracticalAttempts(page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return apiFetch<PageResponse<MyPracticalAttemptSummary>>(`/my-practical-assessments?${query.toString()}`);
}

export async function getPracticalEvidence(skillId: string) {
  const result = await apiFetch<PracticalEvidence | undefined>(`/skills/${skillId}/practical-evidence`);
  return result ?? null;
}
