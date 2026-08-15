import { apiFetch } from "@/lib/api/client";
import type {
  AnswerResponse,
  AssessableSkillResponse,
  AssessmentDetailResponse,
  AssessmentResultResponse,
} from "@/lib/api/types";

export function listAssessableSkills() {
  return apiFetch<AssessableSkillResponse[]>("/assessments/skills");
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
