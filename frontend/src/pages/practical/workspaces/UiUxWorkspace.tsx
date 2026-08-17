import { Send } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { submitPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import { useDebouncedCallback } from "@/lib/hooks/useDebouncedCallback";
import type { WorkspaceProps } from "@/pages/practical/workspaces/types";

interface UiUxConfig {
  referenceImageUrl?: string;
}

function parseConfig(raw: string | null | undefined): UiUxConfig {
  if (!raw) return {};
  try {
    return JSON.parse(raw) as UiUxConfig;
  } catch {
    return {};
  }
}

/**
 * Instructions + link/text submission — a design task's deliverable in this phase is a
 * Figma/prototype link plus an optional written explanation (spec §17). Image-file upload isn't
 * wired in this phase (would need a new backend upload endpoint); noted as a limitation rather
 * than half-built.
 */
export function UiUxWorkspace({ assessment, attempt, mode, onSave, saving, onSubmitted }: WorkspaceProps) {
  const isPreview = mode === "preview";
  const config = parseConfig(assessment.configurationJson);
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
    <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
      <div className="space-y-4 rounded-xl border border-border bg-card p-4">
        <div>
          <h2 className="font-semibold text-foreground">Task</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{assessment.instructions}</p>
        </div>
        {assessment.requirements ? (
          <div>
            <h3 className="text-sm font-medium text-foreground">Requirements</h3>
            <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{assessment.requirements}</p>
          </div>
        ) : null}
        {config.referenceImageUrl ? (
          <div>
            <h3 className="text-sm font-medium text-foreground">Reference</h3>
            <img src={config.referenceImageUrl} alt="Reference" className="mt-2 max-h-64 rounded-lg border border-border object-contain" />
          </div>
        ) : null}
      </div>

      <div className="space-y-4 rounded-xl border border-border bg-card p-4">
        <div className="space-y-1.5">
          <label htmlFor="uiux-link" className="text-sm font-medium text-foreground">
            Prototype / Figma link
          </label>
          <Input
            id="uiux-link"
            value={link}
            onChange={(e) => handleLinkChange(e.target.value)}
            placeholder="https://figma.com/..."
            disabled={isPreview}
          />
        </div>
        <div className="space-y-1.5">
          <label htmlFor="uiux-notes" className="text-sm font-medium text-foreground">
            Notes (optional)
          </label>
          <Textarea
            id="uiux-notes"
            value={notes}
            onChange={(e) => handleNotesChange(e.target.value)}
            rows={6}
            placeholder="Explain your design decisions..."
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
    </div>
  );
}
