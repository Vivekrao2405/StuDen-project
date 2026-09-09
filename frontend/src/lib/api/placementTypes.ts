import type { AssessmentLevel, Difficulty, PageResponse, QuestionType, SkillIconType, SkillResponse } from "@/lib/api/types";
import type { PracticalAttemptStatus, PracticalType } from "@/lib/api/practicalTypes";
import type { ResourceCard, ResourceType } from "@/lib/api/resourceTypes";

export type { PageResponse };

// Enable/disable for admin-managed reference data — mirrors the backend's PlacementCatalogStatus.
export type PlacementCatalogStatus = "ACTIVE" | "INACTIVE";

export type CompanyType = "SERVICE_BASED" | "PRODUCT_BASED" | "STARTUP" | "CONSULTING" | "GCC" | "OTHER";

// --- Roles + Role-Skill mapping --------------------------------------------------------------

export interface PlacementRoleRequest {
  name: string;
  description: string | null;
  displayOrder: number | null;
}

export interface PlacementRoleResponse {
  id: string;
  name: string;
  description: string | null;
  status: PlacementCatalogStatus;
  displayOrder: number;
  skillCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface RoleSkillResponse {
  id: string;
  skillId: string;
  skillName: string;
  skillCategory: string;
  skillIconSlug: string | null;
  skillIconType: SkillIconType;
  weight: number;
  requiredProficiency: AssessmentLevel | null;
  priority: number;
}

export interface PlacementRoleDetailResponse {
  id: string;
  name: string;
  description: string | null;
  status: PlacementCatalogStatus;
  displayOrder: number;
  skills: RoleSkillResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface RoleSkillRequest {
  skillId: string;
  weight: number | null;
  requiredProficiency: AssessmentLevel | null;
  priority: number | null;
}

// --- Companies --------------------------------------------------------------------------------

export interface PlacementCompanyRequest {
  name: string;
  companyType: CompanyType;
  description: string | null;
  logoUrl: string | null;
}

export interface PlacementCompanyResponse {
  id: string;
  name: string;
  companyType: CompanyType;
  description: string | null;
  logoUrl: string | null;
  status: PlacementCatalogStatus;
  createdAt: string;
  updatedAt: string;
}

// --- Skill catalog (admin) ----------------------------------------------------------------------

export interface AdminSkillResponse {
  id: string;
  name: string;
  category: string;
  iconSlug: string | null;
  iconType: SkillIconType;
  createdAt: string;
  updatedAt: string;
}

// --- Student Placement Profile (Phase 2) -------------------------------------------------------

// The student's self-declared starting point, captured during onboarding — a whole-person context
// signal, distinct from AssessmentLevel's 5-tier measured scale.
export type ExperienceLevel = "BEGINNER" | "INTERMEDIATE" | "ADVANCED";

export interface PlacementProfileRequest {
  targetRoleId: string;
  experienceLevel: ExperienceLevel;
  companyTypes: CompanyType[];
  targetCompanyIds: string[];
  // Free-text companies the student typed themselves ("Can't find your company?") — never
  // resolved against, or written into, the shared PlacementCompany catalog.
  manualTargetCompanies: string[];
  currentSkillIds: string[];
}

export interface PlacementProfileResponse {
  id: string;
  userId: string;
  targetRoleId: string;
  targetRoleName: string;
  experienceLevel: ExperienceLevel;
  companyTypes: CompanyType[];
  targetCompanies: PlacementCompanyResponse[];
  manualTargetCompanies: string[];
  currentSkills: SkillResponse[];
  createdAt: string;
  updatedAt: string;
}

// --- Readiness Assessment configuration (admin) -------------------------------------------------

export type PlacementContentStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";

// What one configured item points at — mirrors the backend's ModuleItemType (RESOURCE is never
// used for an assessment item, only for prep-series module items).
export type PlacementItemType = "QUESTION" | "PRACTICAL_ASSESSMENT";

export interface PlacementAssessmentRequest {
  title: string;
  description: string | null;
  roleId: string;
  difficulty: Difficulty;
  durationMinutes: number | null;
  passingScore: number | null;
}

export interface PlacementAssessmentResponse {
  id: string;
  title: string;
  roleId: string;
  roleName: string;
  difficulty: Difficulty;
  status: PlacementContentStatus;
  questionCount: number;
  createdAt: string;
  updatedAt: string;
}

// Exactly one of questionId/practicalAssessmentId must be set.
export interface PlacementAssessmentQuestionRequest {
  questionId: string | null;
  practicalAssessmentId: string | null;
  displayOrder: number | null;
  points: number | null;
}

export interface PlacementAssessmentQuestionResponse {
  id: string;
  itemType: PlacementItemType;
  questionId: string | null;
  questionTextPreview: string | null;
  questionType: QuestionType | null;
  practicalAssessmentId: string | null;
  practicalAssessmentTitle: string | null;
  practicalType: string | null;
  difficulty: Difficulty | null;
  skillId: string;
  skillName: string;
  skillMappedToRole: boolean;
  displayOrder: number;
  points: number;
}

export interface PlacementAssessmentDetailResponse {
  id: string;
  title: string;
  description: string | null;
  roleId: string;
  roleName: string;
  difficulty: Difficulty;
  durationMinutes: number | null;
  passingScore: number | null;
  status: PlacementContentStatus;
  questions: PlacementAssessmentQuestionResponse[];
  createdAt: string;
  updatedAt: string;
}

// --- Placement Readiness (Phase 3) --------------------------------------------------------------

export type PlacementAttemptStatus = "IN_PROGRESS" | "SUBMITTED" | "EXPIRED";

export type PlacementReadinessState = "NO_PROFILE" | "NO_ASSESSMENT_AVAILABLE" | "ASSESSMENT_AVAILABLE";

// Phase 3's readiness-category label for a measured skill score — distinct from AssessmentLevel
// (which measures raw knowledge depth, not role-readiness). Backend-configurable thresholds; the
// frontend must never invent its own boundaries for these labels.
export type SkillReadinessStatus = "CRITICAL" | "IMPROVE" | "GOOD" | "STRONG";

export interface PlacementAttemptOptionView {
  id: string;
  optionText: string;
  displayOrder: number;
}

// A QUESTION slot has questionText/questionType/difficulty/options populated and every practical-*
// field null; a PRACTICAL_ASSESSMENT slot is the reverse — the student answers it entirely through
// the existing practical-attempt taking UI at practicalAttemptId, never inline here.
export interface PlacementAttemptQuestionView {
  id: string;
  itemType: PlacementItemType;
  questionText: string | null;
  questionType: QuestionType | null;
  difficulty: Difficulty | null;
  options: PlacementAttemptOptionView[];
  selectedOptionIds: string[];
  practicalAssessmentId: string | null;
  practicalAssessmentTitle: string | null;
  practicalType: PracticalType | null;
  practicalAttemptId: string | null;
  practicalAttemptStatus: PracticalAttemptStatus | null;
  skillId: string;
  skillName: string;
  displayOrder: number;
  points: number;
}

// Returned by POST (start/resume) and GET /attempts/{id} while status is IN_PROGRESS.
export interface PlacementAttemptDetailResponse {
  id: string;
  placementAssessmentId: string;
  assessmentTitle: string;
  roleId: string;
  roleName: string;
  status: PlacementAttemptStatus;
  totalQuestions: number;
  startedAt: string;
  timeLimitSeconds: number | null;
  remainingSeconds: number | null;
  questions: PlacementAttemptQuestionView[];
}

export interface PlacementAttemptResultOptionView {
  id: string;
  optionText: string;
  displayOrder: number;
  correct: boolean;
}

export interface PlacementAttemptResultQuestionView {
  id: string;
  itemType: PlacementItemType;
  questionText: string | null;
  questionType: QuestionType | null;
  difficulty: Difficulty | null;
  options: PlacementAttemptResultOptionView[];
  selectedOptionIds: string[];
  correctOptionIds: string[];
  correct: boolean;
  explanation: string | null;
  practicalAssessmentId: string | null;
  practicalAssessmentTitle: string | null;
  practicalType: PracticalType | null;
  practicalAttemptId: string | null;
  practicalAttemptStatus: PracticalAttemptStatus | null;
  practicalScorePercentage: number | null;
  skillId: string;
  skillName: string;
  displayOrder: number;
  points: number;
}

// Returned by GET /attempts/{id} once status is SUBMITTED or EXPIRED — the per-question review.
export interface PlacementAttemptReviewResponse {
  id: string;
  placementAssessmentId: string;
  assessmentTitle: string;
  roleId: string;
  roleName: string;
  status: PlacementAttemptStatus;
  totalQuestions: number;
  correctCount: number | null;
  scorePercentage: number | null;
  startedAt: string;
  submittedAt: string | null;
  questions: PlacementAttemptResultQuestionView[];
}

export interface PlacementSkillBreakdownView {
  skillId: string;
  skillName: string;
  correctCount: number;
  totalQuestions: number;
  scorePercentage: number;
  status: SkillReadinessStatus;
  requiredProficiency: AssessmentLevel | null;
  meetsRequiredProficiency: boolean;
}

export interface PlacementSkillGapView {
  rank: number;
  skillId: string;
  skillName: string;
  scorePercentage: number;
  status: SkillReadinessStatus;
  roleSkillPriority: number;
  roleSkillWeight: number;
}

// GET /attempts/{id}/result — only ever returned for a terminal attempt; requesting it while
// IN_PROGRESS 409s server-side.
export interface PlacementReadinessResultResponse {
  attemptId: string;
  placementAssessmentId: string;
  assessmentTitle: string;
  roleId: string;
  roleName: string;
  status: PlacementAttemptStatus;
  totalQuestions: number;
  correctCount: number;
  scorePercentage: number;
  skillBreakdown: PlacementSkillBreakdownView[];
  priorityGaps: PlacementSkillGapView[];
  startedAt: string;
  submittedAt: string | null;
}

// Backs the Placement Readiness entry screen.
export interface PlacementReadinessStatusResponse {
  state: PlacementReadinessState;
  targetRoleId: string | null;
  targetRoleName: string | null;
  assessmentId: string | null;
  assessmentTitle: string | null;
  durationMinutes: number | null;
  questionCount: number;
  inProgressAttemptId: string | null;
  latestResult: PlacementReadinessResultResponse | null;
}

export interface PlacementAttemptSummaryResponse {
  id: string;
  placementAssessmentId: string;
  assessmentTitle: string;
  roleId: string;
  roleName: string;
  status: PlacementAttemptStatus;
  scorePercentage: number | null;
  startedAt: string;
  submittedAt: string | null;
}

// --- Phase 4: Placement Learning Plan (Placement -> My Learning integration) --------------------

// Drives the "Your Placement Learning Path" section inside My Learning — see
// com.studen.placement.PlacementLearningPlanState. The frontend must render a specific guidance
// message for each non-terminal state, never a generic empty state, and never fabricate content.
export type PlacementLearningPlanState =
  | "NO_READINESS_ASSESSMENT"
  | "NO_SKILL_GAPS"
  | "GAPS_WITHOUT_RESOURCES"
  | "HAS_RECOMMENDATIONS";

// One priority-gap entry with its matched, real, admin-mapped resources — `resources` may
// legitimately be empty (a real gap with nothing mapped yet); render that as a real empty state.
export interface PlacementSkillPlanResponse {
  rank: number;
  skillId: string;
  skillName: string;
  scorePercentage: number;
  status: SkillReadinessStatus;
  roleSkillPriority: number;
  roleSkillWeight: number;
  resources: ResourceCard[];
}

export interface PlacementLearningPlanResponse {
  state: PlacementLearningPlanState;
  targetRoleId: string | null;
  targetRoleName: string | null;
  latestAttemptId: string | null;
  latestResultAt: string | null;
  prioritySkills: PlacementSkillPlanResponse[];
}

// --- Phase 5: Placement Prep (Series -> Modules -> Items) ---------------------------------------

// What a Placement Series focuses its preparation on (spec §2/§3's "Preparation Type" selector).
// Nullable on a series — one not narrowed to a single focus simply leaves it unset.
export type PreparationType = "TECHNICAL" | "CODING" | "APTITUDE" | "INTERVIEW" | "FULL_PREPARATION";

export type ModuleItemType = "QUESTION" | "PRACTICAL_ASSESSMENT" | "RESOURCE";

export type PlacementProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export interface PlacementSeriesRequest {
  name: string;
  description: string | null;
  companyId: string | null;
  targetRoleId: string;
  companyType: CompanyType | null;
  difficulty: Difficulty;
  estimatedDurationHours: number | null;
  thumbnailUrl: string | null;
  skillIds: string[] | null;
  preparationType: PreparationType | null;
}

export interface PlacementSeriesResponse {
  id: string;
  name: string;
  description: string | null;
  companyId: string | null;
  companyName: string | null;
  targetRoleId: string;
  targetRoleName: string;
  companyType: CompanyType | null;
  preparationType: PreparationType | null;
  difficulty: Difficulty;
  estimatedDurationHours: number | null;
  thumbnailUrl: string | null;
  status: PlacementContentStatus;
  moduleCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlacementSeriesDetailResponse {
  id: string;
  name: string;
  description: string | null;
  companyId: string | null;
  companyName: string | null;
  targetRoleId: string;
  targetRoleName: string;
  companyType: CompanyType | null;
  preparationType: PreparationType | null;
  difficulty: Difficulty;
  estimatedDurationHours: number | null;
  thumbnailUrl: string | null;
  status: PlacementContentStatus;
  skillsCovered: SkillResponse[];
  modules: PlacementModuleResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface PlacementModuleRequest {
  name: string;
  description: string | null;
  displayOrder: number | null;
  requiredItemCount: number | null;
}

export interface PlacementModuleItemRequest {
  itemType: ModuleItemType;
  questionId: string | null;
  practicalAssessmentId: string | null;
  resourceId: string | null;
  displayOrder: number | null;
  required: boolean | null;
}

// Flattened (targetId, title) view — the caller doesn't need to know which of the three content
// tables an item came from to render it.
export interface PlacementModuleItemResponse {
  id: string;
  itemType: ModuleItemType;
  targetId: string;
  title: string;
  displayOrder: number;
  required: boolean;
}

export interface PlacementModuleResponse {
  id: string;
  seriesId: string;
  name: string;
  description: string | null;
  displayOrder: number;
  requiredItemCount: number | null;
  items: PlacementModuleItemResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface ReorderRequest {
  orderedIds: string[];
}

// --- Student-facing Placement Prep -------------------------------------------------------------

export interface StudentPlacementModuleItemResponse {
  id: string;
  itemType: ModuleItemType;
  targetId: string;
  title: string;
  displayOrder: number;
  required: boolean;
  status: PlacementProgressStatus;
}

export interface StudentPlacementModuleResponse {
  id: string;
  name: string;
  description: string | null;
  displayOrder: number;
  requiredItemCount: number | null;
  totalItemCount: number;
  completedItemCount: number;
  progressPercentage: number;
  status: PlacementProgressStatus;
  items: StudentPlacementModuleItemResponse[];
}

export interface StudentPlacementSeriesResponse {
  id: string;
  name: string;
  description: string | null;
  companyId: string | null;
  companyName: string | null;
  targetRoleId: string;
  targetRoleName: string;
  companyType: CompanyType | null;
  preparationType: PreparationType | null;
  difficulty: Difficulty;
  estimatedDurationHours: number | null;
  thumbnailUrl: string | null;
  skillsCovered: SkillResponse[];
  moduleCount: number;
  progressPercentage: number;
  progressStatus: PlacementProgressStatus;
  updatedAt: string;
}

export interface PlacementSkillCoverageView {
  skillId: string;
  skillName: string;
  readinessStatus: SkillReadinessStatus | null;
  scorePercentage: number | null;
}

export interface StudentPlacementSeriesDetailResponse {
  id: string;
  name: string;
  description: string | null;
  companyId: string | null;
  companyName: string | null;
  targetRoleId: string;
  targetRoleName: string;
  companyType: CompanyType | null;
  preparationType: PreparationType | null;
  difficulty: Difficulty;
  estimatedDurationHours: number | null;
  thumbnailUrl: string | null;
  skillsCovered: PlacementSkillCoverageView[];
  progressPercentage: number;
  progressStatus: PlacementProgressStatus;
  modules: StudentPlacementModuleResponse[];
  updatedAt: string;
}

// "Continue Preparation" pointer — allComplete is true once every module is COMPLETED, and every
// other field is then null.
export interface PlacementContinueResponse {
  seriesId: string;
  allComplete: boolean;
  moduleId: string | null;
  moduleName: string | null;
  itemId: string | null;
  itemType: ModuleItemType | null;
  targetId: string | null;
  itemTitle: string | null;
}

// The shape branches on itemType: only the fields for the actual type are populated. QUESTION
// carries the question inline; PRACTICAL_ASSESSMENT/RESOURCE stay thin pointers — the student takes
// the practical through the existing /practical-attempts/:id workspace and views the resource
// through the existing /resources/:id flow.
export interface PlacementModuleItemDetailResponse {
  id: string;
  moduleId: string;
  seriesId: string;
  itemType: ModuleItemType;
  required: boolean;
  status: PlacementProgressStatus;
  skillName: string;
  // QUESTION
  questionText: string | null;
  questionType: QuestionType | null;
  difficulty: Difficulty | null;
  options: PlacementAttemptOptionView[];
  correctOptionIds: string[] | null;
  explanation: string | null;
  // PRACTICAL_ASSESSMENT
  practicalAssessmentId: string | null;
  practicalAssessmentTitle: string | null;
  practicalType: PracticalType | null;
  practicalAttemptId: string | null;
  practicalAttemptStatus: PracticalAttemptStatus | null;
  // RESOURCE
  resourceId: string | null;
  resourceTitle: string | null;
  resourceType: ResourceType | null;
}

export interface PlacementModuleItemAnswerResponse {
  itemId: string;
  correct: boolean;
  correctOptionIds: string[];
  explanation: string | null;
  status: PlacementProgressStatus;
}
