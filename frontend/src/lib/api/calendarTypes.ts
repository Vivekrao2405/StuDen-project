import type { ResourceCard } from "@/lib/api/resourceTypes";

export type LearningSessionStatus = "SCHEDULED" | "COMPLETED" | "CANCELLED";

// `resource` is null only for a resource-less "Practice / Revision" study-plan slot.
export interface LearningSession {
  id: string;
  topic: string | null;
  resource: ResourceCard | null;
  scheduledStart: string;
  durationMinutes: number;
  status: LearningSessionStatus;
  completedAt: string | null;
}

export interface ScheduleSessionRequest {
  resourceId: string | null;
  topic: string | null;
  scheduledStart: string;
  durationMinutes: number;
}

export interface UpdateSessionRequest {
  scheduledStart: string;
  durationMinutes: number;
}

// DayOfWeek names, e.g. "MONDAY".
export interface StudyPlanRequest {
  startDate: string;
  availableDays: string[];
  durationMinutesPerDay: number;
}

export interface StudyPlanSessionSuggestion {
  date: string;
  dayOfWeek: string;
  skillId: string | null;
  skillName: string | null;
  topic: string;
  resource: ResourceCard | null;
  durationMinutes: number;
}

export interface StudyPlanSuggestionResponse {
  sessions: StudyPlanSessionSuggestion[];
}

export interface StudyPlanSessionToSave {
  resourceId: string | null;
  topic: string | null;
  scheduledStart: string;
  durationMinutes: number;
}

export interface SaveStudyPlanRequest {
  sessions: StudyPlanSessionToSave[];
}

export interface SaveStudyPlanResponse {
  created: LearningSession[];
  skipped: number[];
}
