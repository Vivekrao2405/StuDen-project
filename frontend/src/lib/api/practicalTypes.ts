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
export type CodeJudgeStatus = "UNAVAILABLE" | "ACCEPTED" | "WRONG_ANSWER" | "COMPILATION_ERROR" | "RUNTIME_ERROR" | "TIME_LIMIT_EXCEEDED";

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
}

export interface PracticalTestCaseInput {
  input: string;
  expectedOutput: string;
  hidden: boolean;
  displayOrder: number;
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

// Student-facing shape — no admin-only fields, hidden test cases structurally excluded (see
// StudentTestCaseView).
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
  requirements: string | null;
  constraints: string | null;
  configurationJson: string | null;
  languages: PracticalCodingLanguageDto[];
  publicTestCases: StudentTestCaseView[];
  rubricCriteria: PracticalRubricCriterionDto[];
}

export interface PracticalAttempt {
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

export interface RunResult {
  status: CodeJudgeStatus;
  message: string;
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
