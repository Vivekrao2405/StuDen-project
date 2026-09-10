export type AiCoachActionType = "HINT" | "EXPLAIN_PATTERN" | "DEBUG" | "EXPLAIN_CONCEPT" | "SOLUTION";

export interface AiCoachInteraction {
  id: string;
  actionType: AiCoachActionType;
  hintLevel: number | null;
  message: string;
  createdAt: string;
}

export interface AiCoachRequest {
  actionType: AiCoachActionType;
  topic?: string | null;
}
