import type { PracticalAttempt, PracticalWorkspaceContent, SaveAttemptRequest } from "@/lib/api/practicalTypes";

export interface WorkspaceProps {
  assessment: PracticalWorkspaceContent;
  /** Absent in "preview" mode (admin previewing an unsaved/unpublished assessment). */
  attempt?: PracticalAttempt;
  /** "attempt": a real student attempt — autosave/Run/Submit are live.
   * "preview": admin preview dialog — read-only, no network calls. */
  mode: "attempt" | "preview";
  onSave?: (patch: SaveAttemptRequest) => void;
  saving?: boolean;
  /** Called after a successful submit — the host page owns navigation to the result page. */
  onSubmitted?: () => void;
}
