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

// --- Personalized Learning Roadmap + Smart Recommendations (see com.studen.resource.RoadmapService) ---

export type RecommendationPriority = "HIGH" | "MEDIUM" | "LOW";

// One roadmap entry per weak topic (or, for a skill-scoped-only weak area, one entry for the whole
// skill — mirrors FocusAreaTopic's identical fallback). `status` reuses ResourceProgressStatus, not
// a parallel enum: it's derived entirely from the real progress of `resource` (and any sibling
// resources matching the same topic), never a second source of truth. `resource` is null only when
// zero published resources currently match this topic.
export interface RoadmapItem {
  skillId: string;
  skillName: string;
  topic: string;
  percentage: number;
  status: ResourceProgressStatus;
  priority: RecommendationPriority;
  reason: string;
  resource: ResourceCard | null;
  completedCount: number;
  totalCount: number;
}

export interface RoadmapSkillGroup {
  skillId: string;
  skillName: string;
  items: RoadmapItem[];
}

export interface RoadmapOverview {
  topicsCompleted: number;
  topicsTotal: number;
  percentage: number;
}

// `allCaughtUp` is true only when the student has (or had) real weak areas and every matched
// resource for every one of them is now completed — the honest "100% learning path" state. It is
// false, not true, when the student simply has no weak areas at all (a different reason for
// `groups` to be empty — nothing to be "caught up" on).
export interface RoadmapResponse {
  state: EligibilityState;
  groups: RoadmapSkillGroup[];
  overview: RoadmapOverview;
  allCaughtUp: boolean;
  nextUp: RoadmapItem | null;
}

// "What should I learn next" — `message` is populated only when there's no `nextUp`.
export interface RecommendationResponse {
  nextUp: RoadmapItem | null;
  message: string | null;
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
