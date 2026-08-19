import type { Difficulty, PageResponse } from "@/lib/api/types";

export type { PageResponse };

export type PracticalType =
  | "CODING"
  | "WEB_DEVELOPMENT"
  | "UI_UX"
  | "SQL"
  | "DATA_ANALYSIS"
  | "EXCEL"
  | "POWER_BI"
  | "OTHER_PRACTICAL";

// Drives which student-facing workspace component renders — see
// pages/practical/workspaces/registry.ts. Only CODE_EDITOR, WEB_EDITOR, SQL_EDITOR, and
// UI_UX_WORKSPACE have a dedicated component; every other value falls back to
// GenericSubmissionWorkspace (a real, working submission form — not a placeholder).
export type WorkspaceType =
  | "CODE_EDITOR"
  | "SQL_EDITOR"
  | "WEB_EDITOR"
  | "REACT_EDITOR"
  | "MERN_WORKSPACE"
  | "UI_UX_WORKSPACE"
  | "DATA_ANALYSIS_WORKSPACE"
  | "EXCEL_WORKSPACE"
  | "POWER_BI_WORKSPACE"
  | "FILE_SUBMISSION"
  | "LINK_SUBMISSION";

export type EvaluationType = "MANUAL" | "AUTOMATED" | "HYBRID";
export type PracticalAssessmentStatus = "DRAFT" | "REVIEW" | "PUBLISHED" | "ARCHIVED";
export type PracticalAttemptStatus = "IN_PROGRESS" | "SUBMITTED" | "UNDER_REVIEW" | "EVALUATED" | "EXPIRED" | "CANCELLED";
export type CodingLanguage = "JAVA" | "PYTHON" | "C" | "CPP";
export type OutputComparisonMode = "EXACT" | "TRIM_WHITESPACE" | "NORMALIZE_NEWLINES";

// Phase 7.5 secure execution sandbox. Mirrors com.studen.practical.execution.ExecutionJobStatus —
// SYSTEM_ERROR/SECURITY_ERROR/CANCELLED are infrastructure-side, never the student's fault.
export type ExecutionJobStatus =
  | "QUEUED"
  | "RUNNING"
  | "COMPLETED"
  | "TIMEOUT"
  | "COMPILATION_ERROR"
  | "RUNTIME_ERROR"
  | "MEMORY_LIMIT"
  | "OUTPUT_LIMIT"
  | "SECURITY_ERROR"
  | "SYSTEM_ERROR"
  | "CANCELLED";

export type ExecutionJobKind = "RUN" | "SUBMIT";

export type TestOutcomeStatus = "PASSED" | "WRONG_ANSWER" | "RUNTIME_ERROR" | "TIMEOUT" | "MEMORY_LIMIT" | "OUTPUT_LIMIT";

// Phase 7.6 Assessment Integrity. Mirrors com.studen.integrity's enums exactly.
export type IntegrityEventType =
  | "TAB_HIDDEN"
  | "TAB_VISIBLE"
  | "WINDOW_BLUR"
  | "WINDOW_FOCUS"
  | "COPY_ATTEMPT"
  | "PASTE_ATTEMPT"
  | "CUT_ATTEMPT"
  | "FULLSCREEN_ENTERED"
  | "FULLSCREEN_EXITED"
  | "NAVIGATION_VIOLATION"
  | "MULTIPLE_SESSION";

export type IntegritySeverity = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

// INVALIDATED is only ever set via an admin manual override — never computed automatically.
export type IntegrityStatus = "CLEAN" | "LOW_CONCERN" | "REVIEW" | "HIGH_CONCERN" | "INVALIDATED";

// Per-assessment config, resolved server-side from configurationJson's `integrityPolicy` key —
// the frontend never parses that JSON blob itself. Defaults are all-permissive.
export interface IntegrityPolicy {
  allowCopy: boolean;
  allowPaste: boolean;
  allowCut: boolean;
  requireFullscreen: boolean;
}

export interface IntegrityEventInput {
  clientEventId: string;
  eventType: IntegrityEventType;
  occurredAt: string;
  sessionId?: string | null;
  metadata?: string | null;
}

// Coarse, neutral — never exposes deduction values/thresholds to the student (goal #20).
export interface IntegritySummary {
  attemptId: string;
  integrityScore: number | null;
  integrityStatus: IntegrityStatus | null;
  effectiveStatus: IntegrityStatus | null;
  overridden: boolean;
  overrideReason: string | null;
  overrideByName: string | null;
  overriddenAt: string | null;
  totalEvents: number;
  suspiciousEvents: number;
  criticalEvents: number;
  tabSwitchCount: number;
  copyAttemptCount: number;
  pasteAttemptCount: number;
  cutAttemptCount: number;
  fullscreenExitCount: number;
  multipleSessionCount: number;
  firstSuccessfulCompilationAt: string | null;
  runCount: number;
  submissionCount: number;
  compilationFailureCount: number;
}

export interface AdminIntegrityTimelineEntry {
  timestamp: string;
  category: "LIFECYCLE" | "EXECUTION" | "INTEGRITY";
  label: string;
  detail: string | null;
  severity: IntegritySeverity | null;
}

export interface IntegrityOverrideRequest {
  status: IntegrityStatus;
  reason: string;
}

export interface PracticalCodingLanguageDto {
  id: string;
  language: CodingLanguage;
  starterCode: string | null;
}

export interface PracticalCodingLanguageInput {
  language: CodingLanguage;
  starterCode: string;
}

export interface PracticalTestCaseDto {
  id: string;
  input: string;
  expectedOutput: string;
  hidden: boolean;
  displayOrder: number;
  comparisonMode: OutputComparisonMode;
}

export interface PracticalTestCaseInput {
  input: string;
  expectedOutput: string;
  hidden: boolean;
  displayOrder: number;
  comparisonMode?: OutputComparisonMode | null;
}

// Never has a `hidden` field — this is the shape a student ever receives (only non-hidden rows).
export interface StudentTestCaseView {
  id: string;
  input: string;
  expectedOutput: string;
  displayOrder: number;
}

export interface PracticalRubricCriterionDto {
  id: string;
  criterion: string;
  maxPoints: number;
  displayOrder: number;
}

export interface PracticalRubricCriterionInput {
  criterion: string;
  maxPoints: number;
  displayOrder: number;
}

export interface PracticalAssessmentSummary {
  id: string;
  title: string;
  skillId: string;
  skillName: string;
  practicalType: PracticalType;
  workspaceType: WorkspaceType;
  difficulty: Difficulty;
  status: PracticalAssessmentStatus;
  timeLimitMinutes: number;
  version: number;
  createdAt: string;
}

export interface PracticalAssessmentDetail {
  id: string;
  title: string;
  skillId: string;
  skillName: string;
  practicalType: PracticalType;
  workspaceType: WorkspaceType;
  difficulty: Difficulty;
  timeLimitMinutes: number;
  instructions: string;
  requirements: string | null;
  constraints: string | null;
  evaluationType: EvaluationType;
  status: PracticalAssessmentStatus;
  version: number;
  previousVersionId: string | null;
  configurationJson: string | null;
  languages: PracticalCodingLanguageDto[];
  testCases: PracticalTestCaseDto[];
  rubricCriteria: PracticalRubricCriterionDto[];
  createdAt: string;
}

export interface PracticalAssessmentRequest {
  title: string;
  skillId: string;
  practicalType: PracticalType;
  workspaceType: WorkspaceType;
  difficulty: Difficulty;
  timeLimitMinutes: number;
  instructions: string;
  requirements?: string | null;
  constraints?: string | null;
  evaluationType: EvaluationType;
  configurationJson?: string | null;
  languages?: PracticalCodingLanguageInput[];
  testCases?: PracticalTestCaseInput[];
  rubricCriteria?: PracticalRubricCriterionInput[];
}

// Pre-start metadata ONLY — shown before a student has started an attempt. Deliberately excludes
// the actual problem (requirements/constraints/test cases/starter code/configurationJson) — that
// content is only ever included in a PracticalAttempt, once a real attempt exists for the caller.
// See backend StudentPracticalAssessmentResponse's javadoc for the full rationale. Do not add
// problem-content fields back here.
export interface StudentPracticalAssessment {
  id: string;
  title: string;
  skillId: string;
  skillName: string;
  practicalType: PracticalType;
  workspaceType: WorkspaceType;
  difficulty: Difficulty;
  timeLimitMinutes: number;
  instructions: string;
  supportedLanguages: CodingLanguage[];
  integrityPolicy: IntegrityPolicy;
}

// The actual problem content — instructions/requirements/constraints/configurationJson/languages
// (with starter code)/publicTestCases. Shared by the real attempt workspace (a subset of
// PracticalAttempt, which extends this) and the admin's unsaved-form preview workspace (built
// directly from form state, never from this API).
export interface PracticalWorkspaceContent {
  instructions: string;
  requirements: string | null;
  constraints: string | null;
  configurationJson: string | null;
  languages: PracticalCodingLanguageDto[];
  publicTestCases: StudentTestCaseView[];
}

// Returned only once a real attempt exists for the caller (start/resume/get-while-in-progress/
// autosave) — see backend PracticalAttemptResponse's javadoc for why the problem content living
// here, and nowhere pre-start, is the actual security boundary.
export interface PracticalAttempt extends PracticalWorkspaceContent {
  id: string;
  practicalAssessmentId: string;
  title: string;
  practicalType: PracticalType;
  workspaceType: WorkspaceType;
  status: PracticalAttemptStatus;
  startedAt: string;
  deadline: string;
  remainingSeconds: number | null;
  submissionContent: string | null;
  selectedLanguage: CodingLanguage | null;
  submissionLinkUrl: string | null;
  submissionFileUrl: string | null;
  integrityPolicy: IntegrityPolicy;
}

export interface RubricScoreView {
  criterionId: string;
  criterion: string;
  maxPoints: number;
  pointsAwarded: number;
}

export interface PracticalAttemptResult {
  id: string;
  practicalAssessmentId: string;
  title: string;
  practicalType: PracticalType;
  difficulty: Difficulty;
  status: PracticalAttemptStatus;
  startedAt: string;
  submittedAt: string | null;
  evaluatedAt: string | null;
  score: number | null;
  maxScore: number | null;
  feedback: string | null;
  rubricScores: RubricScoreView[];
}

export interface MyPracticalAttemptSummary {
  id: string;
  practicalAssessmentId: string;
  title: string;
  practicalType: PracticalType;
  status: PracticalAttemptStatus;
  startedAt: string;
  submittedAt: string | null;
  score: number | null;
  maxScore: number | null;
}

export interface SaveAttemptRequest {
  submissionContent?: string | null;
  selectedLanguage?: CodingLanguage | null;
  submissionLinkUrl?: string | null;
}

// Public test cases only — hidden ones are never sent to a student, even in a Run result.
// expectedOutput is always null for SQL (the reference query is never exposed); actualOutput for
// SQL is a safe row-count summary rather than raw result data.
export interface ExecutionTestResultView {
  testCaseId: string;
  passed: boolean;
  input: string | null;
  expectedOutput: string | null;
  actualOutput: string | null;
  executionTimeMs: number | null;
  status: TestOutcomeStatus;
}

export interface RunResult {
  status: ExecutionJobStatus;
  message: string;
  compileError: string | null;
  testsPassed: number | null;
  testsTotal: number | null;
  hiddenTestsPassed: number | null;
  hiddenTestsTotal: number | null;
  durationMs: number | null;
  publicTestResults: ExecutionTestResultView[];
}

// Run #1, #2, #3... — every execution recorded for an attempt, oldest first.
export interface ExecutionJobSummary {
  id: string;
  kind: ExecutionJobKind;
  status: ExecutionJobStatus;
  testsPassed: number | null;
  testsTotal: number | null;
  durationMs: number | null;
  createdAt: string;
}

// Admin-only "Test Question" tool — includes hidden test cases.
export interface AdminExecutionTestResultView {
  testCaseId: string;
  hidden: boolean;
  passed: boolean;
  input: string | null;
  expectedOutput: string | null;
  actualOutput: string | null;
  executionTimeMs: number | null;
  status: TestOutcomeStatus;
}

export interface AdminTestRunRequest {
  language?: CodingLanguage | null;
  sourceCode: string;
}

export interface AdminTestRunResult {
  status: ExecutionJobStatus;
  message: string;
  compileError: string | null;
  testsPassed: number | null;
  testsTotal: number | null;
  durationMs: number | null;
  testResults: AdminExecutionTestResultView[];
}

export interface AdminPracticalAttemptSummary {
  id: string;
  practicalAssessmentId: string;
  assessmentTitle: string;
  studentId: string;
  studentName: string;
  status: PracticalAttemptStatus;
  startedAt: string;
  submittedAt: string | null;
}

export interface AdminPracticalAttemptDetail {
  id: string;
  practicalAssessmentId: string;
  assessmentTitle: string;
  practicalType: PracticalType;
  studentId: string;
  studentName: string;
  status: PracticalAttemptStatus;
  startedAt: string;
  deadline: string;
  submittedAt: string | null;
  evaluatedAt: string | null;
  score: number | null;
  maxScore: number | null;
  feedback: string | null;
  selectedLanguage: CodingLanguage | null;
  submissionContent: string | null;
  submissionFileUrl: string | null;
  submissionLinkUrl: string | null;
  testCases: PracticalTestCaseDto[];
  rubricCriteria: PracticalRubricCriterionDto[];
  rubricScores: RubricScoreView[];
  integrity: IntegritySummary;
}

export interface EvaluateAttemptRequest {
  rubricScores?: { criterionId: string; points: number }[] | null;
  score?: number | null;
  feedback?: string | null;
}

export interface PracticalEvidence {
  skillId: string;
  skillName: string;
  practicalAssessmentId: string;
  assessmentTitle: string;
  score: number | null;
  maxScore: number | null;
  evaluatedAt: string | null;
}

export interface AdminPracticalAssessmentListParams {
  skillId?: string;
  practicalType?: PracticalType;
  difficulty?: Difficulty;
  status?: PracticalAssessmentStatus;
  search?: string;
  page?: number;
  size?: number;
}

export interface PracticalAssessmentListParams {
  skillId?: string;
  practicalType?: PracticalType;
  difficulty?: Difficulty;
  search?: string;
  page?: number;
  size?: number;
}
