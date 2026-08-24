import type { PracticalAttemptQuestion, PracticalWorkspaceContent, SaveAttemptRequest } from "@/lib/api/practicalTypes";

// One workspace instance always renders exactly one question — the host page (PracticalAttemptPage
// for a real attempt, the admin editor's preview dialog otherwise) owns which question is active
// and re-mounts/re-props this component when the student navigates between questions.
export interface WorkspaceProps {
  assessment: PracticalWorkspaceContent;
  /** The parent attempt's id — needed for Run/executions API calls. Absent in "preview" mode. */
  attemptId?: string;
  /** This question's attempt-scoped state. Absent in "preview" mode (admin previewing an unsaved/unpublished question). */
  attempt?: PracticalAttemptQuestion;
  /** "attempt": a real student attempt — autosave/Run are live.
   * "preview": admin preview dialog — read-only, no network calls. */
  mode: "attempt" | "preview";
  onSave?: (patch: SaveAttemptRequest) => void;
  saving?: boolean;
}
