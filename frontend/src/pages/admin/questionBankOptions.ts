import type { Difficulty, QuestionStatus, QuestionType } from "@/lib/api/types";

export const DIFFICULTY_OPTIONS: { value: Difficulty; label: string }[] = [
  { value: "EASY", label: "Easy" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HARD", label: "Hard" },
];

export const QUESTION_TYPE_OPTIONS: { value: QuestionType; label: string }[] = [
  { value: "MCQ_SINGLE", label: "Single choice" },
  { value: "MCQ_MULTIPLE", label: "Multiple choice" },
  { value: "TRUE_FALSE", label: "True / False" },
];

export const QUESTION_STATUS_OPTIONS: { value: QuestionStatus; label: string }[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "REVIEW", label: "In Review" },
  { value: "PUBLISHED", label: "Published" },
  { value: "ARCHIVED", label: "Archived" },
];

export function questionTypeLabel(type: QuestionType): string {
  return QUESTION_TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type;
}

export function difficultyLabel(difficulty: Difficulty): string {
  return DIFFICULTY_OPTIONS.find((o) => o.value === difficulty)?.label ?? difficulty;
}

export function statusLabel(status: QuestionStatus): string {
  return QUESTION_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? status;
}

// Badge `variant` prop per status/difficulty — kept as simple lookup tables so every place a
// status/difficulty is shown (list rows, detail page, preview) renders identically.
export function statusBadgeVariant(status: QuestionStatus): "default" | "secondary" | "outline" | "destructive" {
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
