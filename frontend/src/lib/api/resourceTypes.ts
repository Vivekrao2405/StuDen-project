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
// progress inline.
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
}

// The card shape used inside a WeakAreaGroup's resource list on My Learning.
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
}

export interface ResourceProgress {
  resourceId: string;
  status: ResourceProgressStatus;
  startedAt: string | null;
  completedAt: string | null;
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
}

// Mirrors com.studen.resource.MyLearningResponse — reuses EligibilityState as-is; a portfolio
// skill with no weak area is simply omitted from `groups` under HAS_AVAILABLE_ASSESSMENTS rather
// than a distinct state.
export interface MyLearningResponse {
  state: EligibilityState;
  groups: WeakAreaGroup[];
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
