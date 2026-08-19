import { apiFetch } from "@/lib/api/client";
import type {
  AnswerResponse,
  AssessableSkillsResponse,
  AssessmentDetailResponse,
  AssessmentResultResponse,
  AssessmentResultSummaryResponse,
} from "@/lib/api/types";

// Scoped to the caller's own portfolio skills — see AssessableSkillsResponse's `state` for why an
// empty `skills` list must never be treated as "no assessments exist".
export function listAssessableSkills() {
  return apiFetch<AssessableSkillsResponse>("/assessments/skills");
}

// Starts a brand-new assessment, or transparently resumes an existing IN_PROGRESS one for this
// skill — the backend decides which; the frontend never tracks that state itself.
export function startAssessment(skillId: string) {
  return apiFetch<AssessmentDetailResponse>("/assessments", { method: "POST", body: { skillId } });
}

// Returns AssessmentDetailResponse while in progress, AssessmentResultResponse once terminal —
// callers branch on `status` to decide which shape they actually got.
export function getAssessment(assessmentId: string) {
  return apiFetch<AssessmentDetailResponse | AssessmentResultResponse>(`/assessments/${assessmentId}`);
}

export function saveAssessmentAnswer(assessmentId: string, assessmentQuestionId: string, selectedOptionIds: string[]) {
  return apiFetch<AnswerResponse>(`/assessments/${assessmentId}/questions/${assessmentQuestionId}/answer`, {
    method: "PATCH",
    body: { selectedOptionIds },
  });
}

export function submitAssessment(assessmentId: string) {
  return apiFetch<AssessmentResultResponse>(`/assessments/${assessmentId}/submit`, { method: "POST" });
}

// Phase 7.3's scored/leveled/topic-broken-down summary — only valid once the assessment is
// terminal (the backend 409s otherwise). Deliberately a separate call from getAssessment, which
// still serves the per-question review shape.
export function getAssessmentResult(assessmentId: string) {
  return apiFetch<AssessmentResultSummaryResponse>(`/assessments/${assessmentId}/result`);
}

// 204/undefined when the student has never completed an assessment yet — a normal empty state.
export function getLatestAssessmentResult() {
  return apiFetch<AssessmentResultSummaryResponse | undefined>("/assessments/latest-result");
}
