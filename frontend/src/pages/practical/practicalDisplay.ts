import type { Difficulty } from "@/lib/api/types";
import type {
  CodingLanguage,
  EvaluationType,
  ExecutionJobStatus,
  PracticalAssessmentStatus,
  PracticalAttemptStatus,
  PracticalType,
  WorkspaceType,
} from "@/lib/api/practicalTypes";

export const WORKSPACE_TYPE_LABEL: Record<WorkspaceType, string> = {
  CODE_EDITOR: "Code Editor",
  SQL_EDITOR: "SQL Editor",
  WEB_EDITOR: "Web Editor (HTML/CSS/JS + Live Preview)",
  REACT_EDITOR: "React Editor",
  MERN_WORKSPACE: "MERN Workspace",
  UI_UX_WORKSPACE: "UI/UX Workspace",
  DATA_ANALYSIS_WORKSPACE: "Data Analysis Workspace",
  EXCEL_WORKSPACE: "Excel Workspace",
  POWER_BI_WORKSPACE: "Power BI Workspace",
  FILE_SUBMISSION: "File Submission",
  LINK_SUBMISSION: "Link Submission",
};

export const WORKSPACE_TYPE_OPTIONS: { value: WorkspaceType; label: string }[] = (
  Object.keys(WORKSPACE_TYPE_LABEL) as WorkspaceType[]
).map((value) => ({ value, label: WORKSPACE_TYPE_LABEL[value] }));

export const EVALUATION_TYPE_OPTIONS: { value: EvaluationType; label: string }[] = [
  { value: "MANUAL", label: "Manual" },
  { value: "AUTOMATED", label: "Automated" },
  { value: "HYBRID", label: "Hybrid" },
];

export const CODING_LANGUAGE_LABEL: Record<CodingLanguage, string> = {
  JAVA: "Java",
  PYTHON: "Python",
  C: "C",
  CPP: "C++",
};

export const PRACTICAL_TYPE_LABEL: Record<PracticalType, string> = {
  CODING: "Coding",
  WEB_DEVELOPMENT: "Web Development",
  UI_UX: "UI/UX",
  SQL: "SQL",
  DATA_ANALYSIS: "Data Analysis",
  EXCEL: "Excel",
  POWER_BI: "Power BI",
  OTHER_PRACTICAL: "Other",
};

export const PRACTICAL_TYPE_OPTIONS: { value: PracticalType; label: string }[] = (
  Object.keys(PRACTICAL_TYPE_LABEL) as PracticalType[]
).map((value) => ({ value, label: PRACTICAL_TYPE_LABEL[value] }));

export const DIFFICULTY_OPTIONS: { value: Difficulty; label: string }[] = [
  { value: "EASY", label: "Easy" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HARD", label: "Hard" },
];

export function difficultyBadgeVariant(difficulty: Difficulty): "default" | "secondary" | "outline" {
  switch (difficulty) {
    case "HARD":
      return "default";
    case "MEDIUM":
      return "secondary";
    default:
      return "outline";
  }
}

export function assessmentStatusBadgeVariant(status: PracticalAssessmentStatus): "default" | "secondary" | "outline" | "destructive" {
  switch (status) {
    case "PUBLISHED":
      return "default";
    case "REVIEW":
      return "secondary";
    case "ARCHIVED":
      return "destructive";
    default:
      return "outline";
  }
}

export function attemptStatusLabel(status: PracticalAttemptStatus): string {
  switch (status) {
    case "IN_PROGRESS":
      return "In Progress";
    case "UNDER_REVIEW":
      return "Under Review";
    case "EVALUATED":
      return "Evaluated";
    case "EXPIRED":
      return "Expired";
    case "SUBMITTED":
      return "Submitted";
    case "CANCELLED":
      return "Cancelled";
  }
}

export function attemptStatusBadgeVariant(status: PracticalAttemptStatus): "default" | "secondary" | "outline" | "destructive" {
  switch (status) {
    case "EVALUATED":
    case "SUBMITTED":
      return "default";
    case "UNDER_REVIEW":
      return "secondary";
    case "EXPIRED":
    case "CANCELLED":
      return "destructive";
    default:
      return "outline";
  }
}

// Phase 7.5 execution sandbox — Run/Submit/Test Question result badges.
export function executionStatusLabel(status: ExecutionJobStatus): string {
  switch (status) {
    case "QUEUED":
      return "Queued";
    case "RUNNING":
      return "Running";
    case "COMPLETED":
      return "Completed";
    case "TIMEOUT":
      return "Timed Out";
    case "COMPILATION_ERROR":
      return "Compilation Error";
    case "RUNTIME_ERROR":
      return "Runtime Error";
    case "MEMORY_LIMIT":
      return "Memory Limit Exceeded";
    case "OUTPUT_LIMIT":
      return "Output Limit Exceeded";
    case "SECURITY_ERROR":
      return "Rejected";
    case "SYSTEM_ERROR":
      return "Temporarily Unavailable";
    case "CANCELLED":
      return "Cancelled";
  }
}

export function executionStatusBadgeVariant(status: ExecutionJobStatus): "default" | "secondary" | "outline" | "destructive" {
  switch (status) {
    case "COMPLETED":
      return "default";
    case "QUEUED":
    case "RUNNING":
      return "outline";
    case "SYSTEM_ERROR":
    case "SECURITY_ERROR":
    case "CANCELLED":
      return "secondary";
    default:
      return "destructive";
  }
}
