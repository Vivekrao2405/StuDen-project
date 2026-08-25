import type { Difficulty, EligibilityState, PageResponse } from "@/lib/api/types";

export type { PageResponse };
export type { EligibilityState };

export type ResourceType = "PDF" | "EXTERNAL_LINK" | "VIDEO" | "DOCUMENT" | "NOTES";
export type ResourceStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type ResourceProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

// No `status` field, deliberately — mirrors the backend request DTO: status only changes via the
// dedicated publish/unpublish/archive endpoints.
export interface ResourceRequest {
  title: string;
  description: string | null;
  resourceType: ResourceType;
  skillId: string;
  difficulty: Difficulty | null;
  estimatedMinutes: number | null;
  externalUrl: string | null;
  notesContent: string | null;
  tags: string[];
}

// Admin list row shape.
export interface ResourceSummary {
  id: string;
  title: string;
  resourceType: ResourceType;
  skillId: string;
  skillName: string;
  difficulty: Difficulty | null;
  estimatedMinutes: number | null;
  status: ResourceStatus;
  tags: string[];
  createdAt: string;
}

// Admin detail shape — includes filePublicId/status, never returned to students.
export interface ResourceDetail {
  id: string;
  title: string;
  description: string | null;
  resourceType: ResourceType;
  skillId: string;
  skillName: string;
  difficulty: Difficulty | null;
  estimatedMinutes: number | null;
  fileUrl: string | null;
  filePublicId: string | null;
  externalUrl: string | null;
  notesContent: string | null;
  tags: string[];
  status: ResourceStatus;
  createdAt: string;
}

// Student-facing single-resource shape — no filePublicId/status, carries the caller's own
// progress (including startedAt/completedAt) inline.
export interface StudentResource {
  id: string;
  title: string;
  description: string | null;
  resourceType: ResourceType;
  skillId: string;
  skillName: string;
  difficulty: Difficulty | null;
  estimatedMinutes: number | null;
  fileUrl: string | null;
  externalUrl: string | null;
  notesContent: string | null;
  tags: string[];
  progressStatus: ResourceProgressStatus;
  startedAt: string | null;
  completedAt: string | null;
}

// The card shape used inside a WeakAreaGroup's resource list on My Learning. createdAt drives
// "Latest" sort; startedAt/completedAt are null unless the caller has a progress row and drive the
// Continue Learning / Recently Completed sections.
export interface ResourceCard {
  id: string;
  title: string;
  description: string | null;
  resourceType: ResourceType;
  skillId: string;
  skillName: string;
  difficulty: Difficulty | null;
  estimatedMinutes: number | null;
  tags: string[];
  progressStatus: ResourceProgressStatus;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface ResourceProgress {
  resourceId: string;
  status: ResourceProgressStatus;
  startedAt: string | null;
  completedAt: string | null;
}

// One row per weak topic parsed out of a group's weakTags (see lib/learningTags.ts's frontend
// mirror of the backend TagParser). completedCount/totalCount are computed against the full
// published-resource set for the skill, not the capped `resources` list below — a real fraction,
// not an artifact of the "don't flood the student" cap.
export interface FocusAreaTopic {
  topic: string;
  percentage: number;
  completedCount: number;
  totalCount: number;
}

// weakTags is empty when the weak signal came only from a practical assessment (PracticalQuestion
// carries no tags) — that group's resources are matched by skill alone.
export interface WeakAreaGroup {
  skillId: string;
  skillName: string;
  weakTags: string[];
  percentage: number;
  resources: ResourceCard[];
  completedCount: number;
  totalCount: number;
  topics: FocusAreaTopic[];
}

// Real aggregates for the My Learning overview card — see com.studen.resource.LearningOverviewResponse.
export interface LearningOverview {
  weakSkillsCount: number;
  resourcesCount: number;
  assessmentsCompletedCount: number;
  completedResourceCount: number;
  totalResourceCount: number;
}

// Mirrors com.studen.resource.MyLearningResponse — reuses EligibilityState as-is; a portfolio
// skill with no weak area is simply omitted from `groups` under HAS_AVAILABLE_ASSESSMENTS rather
// than a distinct state.
export interface MyLearningResponse {
  state: EligibilityState;
  groups: WeakAreaGroup[];
  overview: LearningOverview;
}

export interface AdminResourceListParams {
  skillId?: string;
  resourceType?: ResourceType;
  difficulty?: Difficulty;
  status?: ResourceStatus;
  search?: string;
  page?: number;
  size?: number;
}
