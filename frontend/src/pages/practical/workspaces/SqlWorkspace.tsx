import Editor from "@monaco-editor/react";
import { Play, Send } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { runPracticalAttempt, submitPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { RunResult } from "@/lib/api/practicalTypes";
import { useDebouncedCallback } from "@/lib/hooks/useDebouncedCallback";
import { ExecutionResultPanel } from "@/pages/practical/workspaces/ExecutionResultPanel";
import { RunHistoryPanel } from "@/pages/practical/workspaces/RunHistoryPanel";
import type { WorkspaceProps } from "@/pages/practical/workspaces/types";

interface SqlConfig {
  schemaDescription?: string;
}

function parseConfig(raw: string | null | undefined): SqlConfig {
  if (!raw) return {};
  try {
    return JSON.parse(raw) as SqlConfig;
  } catch {
    return {};
  }
}

/**
 * Schema description + query editor. "Run" executes the student's query against a throwaway,
 * seeded, network-isolated Postgres sandbox (Phase 7.5) — never against the real StuDen database.
 */
export function SqlWorkspace({ assessment, attempt, mode, onSave, saving, onSubmitted }: WorkspaceProps) {
  const isPreview = mode === "preview";
  const config = parseConfig(assessment.configurationJson);
  const [query, setQuery] = useState(attempt?.submissionContent ?? "SELECT ...");
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [running, setRunning] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [historyKey, setHistoryKey] = useState(0);

  const debouncedSave = useDebouncedCallback((next: string) => {
    onSave?.({ submissionContent: next });
  }, 800);

  function handleChange(value: string | undefined) {
    const next = value ?? "";
    setQuery(next);
    if (!isPreview) debouncedSave(next);
  }

  async function handleRun() {
    if (!attempt) return;
    setRunning(true);
    try {
      setRunResult(await runPracticalAttempt(attempt.id));
      setHistoryKey((k) => k + 1);
    } finally {
      setRunning(false);
    }
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
    <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)] lg:items-start">
      <div className="space-y-4 rounded-xl border border-border bg-card p-4">
        <div>
          <h2 className="font-semibold text-foreground">Problem</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{assessment.instructions}</p>
        </div>
        <div>
          <h3 className="text-sm font-medium text-foreground">Schema</h3>
          <pre className="mt-1 whitespace-pre-wrap rounded-lg border border-border bg-muted/30 p-3 text-xs text-muted-foreground">
            {config.schemaDescription ?? "No schema description configured."}
          </pre>
        </div>
      </div>

      <div className="space-y-3 rounded-xl border border-border bg-card p-4">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-foreground">SQL Editor</p>
          {saving ? <span className="text-xs text-muted-foreground">Saving...</span> : null}
        </div>
        <div className="overflow-hidden rounded-lg border border-border">
          <Editor
            height="320px"
            language="sql"
            value={query}
            onChange={handleChange}
            options={{ readOnly: isPreview, minimap: { enabled: false }, fontSize: 13 }}
            theme="vs-dark"
          />
        </div>

        {runResult ? <ExecutionResultPanel result={runResult} /> : null}

        {!isPreview ? (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={handleRun} disabled={running || !attempt}>
              <Play className="size-4" /> {running ? "Running..." : "Run"}
            </Button>
            <Button size="sm" onClick={handleSubmit} disabled={submitting || !attempt}>
              <Send className="size-4" /> {submitting ? "Submitting..." : "Submit"}
            </Button>
          </div>
        ) : null}

        {!isPreview && attempt ? <RunHistoryPanel attemptId={attempt.id} refreshKey={historyKey} /> : null}
      </div>
    </div>
  );
}
