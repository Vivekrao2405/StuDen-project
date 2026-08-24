import Editor from "@monaco-editor/react";
import { Play } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/ApiError";
import { runPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { RunResult } from "@/lib/api/practicalTypes";
import { useDebouncedCallback } from "@/lib/hooks/useDebouncedCallback";
import { ExecutionErrorBanner } from "@/pages/practical/workspaces/ExecutionErrorBanner";
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
export function SqlWorkspace({ assessment, attemptId, attempt, mode, onSave, saving }: WorkspaceProps) {
  const isPreview = mode === "preview";
  const config = parseConfig(assessment.configurationJson);
  const [query, setQuery] = useState(attempt?.submissionContent ?? "SELECT ...");
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
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
    if (!attemptId || !attempt) return;
    setRunning(true);
    setRunError(null);
    try {
      setRunResult(await runPracticalAttempt(attemptId, attempt.id));
      setHistoryKey((k) => k + 1);
    } catch (err) {
      // The execution infrastructure itself is unavailable (HTTP 503) — never fabricated as a
      // pass/fail result. Your query is already saved independently via the debounced autosave.
      setRunResult(null);
      setRunError(
        err instanceof ApiError
          ? err.message
          : "Automated execution is temporarily unavailable right now. Your work is saved — please try again shortly."
      );
    } finally {
      setRunning(false);
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

        {runError ? <ExecutionErrorBanner message={runError} /> : runResult ? <ExecutionResultPanel result={runResult} /> : null}

        {!isPreview ? (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={handleRun} disabled={running || !attempt}>
              <Play className="size-4" /> {running ? "Running..." : "Run"}
            </Button>
          </div>
        ) : null}

        {!isPreview && attemptId && attempt ? (
          <RunHistoryPanel attemptId={attemptId} questionId={attempt.id} refreshKey={historyKey} />
        ) : null}
      </div>
    </div>
  );
}
