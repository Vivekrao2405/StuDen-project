import { apiFetch } from "@/lib/api/client";
import type {
  LearningSession,
  SaveStudyPlanRequest,
  SaveStudyPlanResponse,
  ScheduleSessionRequest,
  StudyPlanRequest,
  StudyPlanSuggestionResponse,
  UpdateSessionRequest,
} from "@/lib/api/calendarTypes";

export function getSessions(from: string, to: string) {
  const params = new URLSearchParams({ from, to });
  return apiFetch<LearningSession[]>(`/calendar/sessions?${params.toString()}`);
}

export function scheduleSession(request: ScheduleSessionRequest) {
  return apiFetch<LearningSession>("/calendar/sessions", { method: "POST", body: request });
}

export function updateSession(id: string, request: UpdateSessionRequest) {
  return apiFetch<LearningSession>(`/calendar/sessions/${id}`, { method: "PATCH", body: request });
}

export function deleteSession(id: string) {
  return apiFetch<void>(`/calendar/sessions/${id}`, { method: "DELETE" });
}

export function completeSession(id: string) {
  return apiFetch<LearningSession>(`/calendar/sessions/${id}/complete`, { method: "POST" });
}

export function previewStudyPlan(request: StudyPlanRequest) {
  return apiFetch<StudyPlanSuggestionResponse>("/calendar/study-plan/preview", { method: "POST", body: request });
}

export function saveStudyPlan(request: SaveStudyPlanRequest) {
  return apiFetch<SaveStudyPlanResponse>("/calendar/study-plan/save", { method: "POST", body: request });
}
