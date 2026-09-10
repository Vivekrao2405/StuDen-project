import { apiFetch } from "@/lib/api/client";
import type { AiCoachInteraction, AiCoachRequest } from "@/lib/api/aiCoachTypes";

// questionId here is a PracticalAttemptQuestion id, already scoped to this attempt — same shape as
// runPracticalAttempt/savePracticalAttempt in endpoints/practicalAssessments.ts.
export function requestAiCoachAction(attemptId: string, questionId: string, request: AiCoachRequest) {
  return apiFetch<AiCoachInteraction>(`/practical-attempts/${attemptId}/questions/${questionId}/ai-coach`, {
    method: "POST",
    body: request,
  });
}

export function getAiCoachHistory(attemptId: string, questionId: string) {
  return apiFetch<AiCoachInteraction[]>(`/practical-attempts/${attemptId}/questions/${questionId}/ai-coach/history`);
}
