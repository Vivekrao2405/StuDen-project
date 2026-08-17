import { Send } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { submitPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import { useDebouncedCallback } from "@/lib/hooks/useDebouncedCallback";
import type { WorkspaceProps } from "@/pages/practical/workspaces/types";

/**
 * The honest fallback for every workspaceType without a dedicated component yet (REACT_EDITOR,
 * MERN_WORKSPACE, DATA_ANALYSIS_WORKSPACE, EXCEL_WORKSPACE, POWER_BI_WORKSPACE, FILE_SUBMISSION,
 * LINK_SUBMISSION) — instructions plus a link and/or free-text submission. This is a real, working
 * submission path, not a placeholder; it's what the workspace registry (registry.ts) routes to
 * until a dedicated live environment is built for one of these types (spec §63/§66).
 */
export function GenericSubmissionWorkspace({ assessment, attempt, mode, onSave, saving, onSubmitted }: WorkspaceProps) {
  const isPreview = mode === "preview";
  const [link, setLink] = useState(attempt?.submissionLinkUrl ?? "");
  const [notes, setNotes] = useState(attempt?.submissionContent ?? "");
  const [submitting, setSubmitting] = useState(false);

  const debouncedSave = useDebouncedCallback((nextLink: string, nextNotes: string) => {
    onSave?.({ submissionLinkUrl: nextLink, submissionContent: nextNotes });
  }, 800);

  function handleLinkChange(value: string) {
    setLink(value);
    if (!isPreview) debouncedSave(value, notes);
  }

  function handleNotesChange(value: string) {
    setNotes(value);
    if (!isPreview) debouncedSave(link, value);
  }

  async function handleSubmit() {
    if (!attempt) return;
    setSubmitting(true);
    try {
      await submitPracticalAttempt(attempt.id);
      onSubmitted?.();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4">
      <div>
        <h2 className="font-semibold text-foreground">Task</h2>
        <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{assessment.instructions}</p>
        {assessment.requirements ? (
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">
            <span className="font-medium text-foreground">Requirements: </span>
            {assessment.requirements}
          </p>
        ) : null}
      </div>

      <div className="space-y-1.5">
        <label htmlFor="generic-link" className="text-sm font-medium text-foreground">
          Link (repo, deployed project, notebook, file share, etc.)
        </label>
        <Input id="generic-link" value={link} onChange={(e) => handleLinkChange(e.target.value)} placeholder="https://..." disabled={isPreview} />
      </div>
      <div className="space-y-1.5">
        <label htmlFor="generic-notes" className="text-sm font-medium text-foreground">
          Explanation
        </label>
        <Textarea
          id="generic-notes"
          value={notes}
          onChange={(e) => handleNotesChange(e.target.value)}
          rows={6}
          placeholder="Describe your approach and results..."
          disabled={isPreview}
        />
      </div>
      {saving ? <p className="text-xs text-muted-foreground">Saving...</p> : null}
      {!isPreview ? (
        <Button size="sm" onClick={handleSubmit} disabled={submitting || !attempt}>
          <Send className="size-4" /> {submitting ? "Submitting..." : "Submit"}
        </Button>
      ) : null}
    </div>
  );
}
