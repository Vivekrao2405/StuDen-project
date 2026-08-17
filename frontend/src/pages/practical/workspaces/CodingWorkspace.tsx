import Editor from "@monaco-editor/react";
import { Play, Send } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { runPracticalAttempt, submitPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { CodingLanguage } from "@/lib/api/practicalTypes";
import { useDebouncedCallback } from "@/lib/hooks/useDebouncedCallback";
import type { WorkspaceProps } from "@/pages/practical/workspaces/types";

const MONACO_LANGUAGE: Record<CodingLanguage, string> = {
  JAVA: "java",
  PYTHON: "python",
  C: "c",
  CPP: "cpp",
};

const LANGUAGE_LABEL: Record<CodingLanguage, string> = {
  JAVA: "Java",
  PYTHON: "Python",
  C: "C",
  CPP: "C++",
};

export function CodingWorkspace({ assessment, attempt, mode, onSave, saving, onSubmitted }: WorkspaceProps) {
  const isPreview = mode === "preview";
  const availableLanguages = assessment.languages;
  const [language, setLanguage] = useState<CodingLanguage>(
    attempt?.selectedLanguage ?? availableLanguages[0]?.language ?? "JAVA"
  );
  const [code, setCode] = useState<string>(
    attempt?.submissionContent ?? availableLanguages.find((l) => l.language === language)?.starterCode ?? ""
  );
  const [runResult, setRunResult] = useState<{ status: string; message: string } | null>(null);
  const [running, setRunning] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const debouncedSave = useDebouncedCallback((nextCode: string, nextLanguage: CodingLanguage) => {
    onSave?.({ submissionContent: nextCode, selectedLanguage: nextLanguage });
  }, 800);

  function handleLanguageChange(next: CodingLanguage) {
    setLanguage(next);
    const starter = availableLanguages.find((l) => l.language === next)?.starterCode ?? "";
    setCode(starter);
    if (!isPreview) {
      debouncedSave(starter, next);
    }
  }

  function handleCodeChange(value: string | undefined) {
    const next = value ?? "";
    setCode(next);
    if (!isPreview) {
      debouncedSave(next, language);
    }
  }

  async function handleRun() {
    if (!attempt) return;
    setRunning(true);
    try {
      const result = await runPracticalAttempt(attempt.id);
      setRunResult(result);
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

  const publicTestCases = assessment.publicTestCases;

  return (
    <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
      <div className="space-y-4 rounded-xl border border-border bg-card p-4">
        <div>
          <h2 className="font-semibold text-foreground">Problem</h2>
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{assessment.instructions}</p>
        </div>
        {assessment.constraints ? (
          <div>
            <h3 className="text-sm font-medium text-foreground">Constraints</h3>
            <p className="mt-1 whitespace-pre-wrap text-xs text-muted-foreground">{assessment.constraints}</p>
          </div>
        ) : null}
        <div>
          <h3 className="text-sm font-medium text-foreground">Examples</h3>
          <div className="mt-2 space-y-2">
            {publicTestCases.map((tc, i) => (
              <div key={tc.id} className="rounded-lg border border-border bg-muted/30 p-3 text-xs">
                <p className="font-medium text-foreground">Example {i + 1}</p>
                <p className="mt-1 text-muted-foreground">
                  Input: <code className="whitespace-pre-wrap">{tc.input}</code>
                </p>
                <p className="text-muted-foreground">
                  Output: <code className="whitespace-pre-wrap">{tc.expectedOutput}</code>
                </p>
              </div>
            ))}
            {publicTestCases.length === 0 ? <p className="text-xs text-muted-foreground">No public examples provided.</p> : null}
          </div>
        </div>
      </div>

      <div className="space-y-3 rounded-xl border border-border bg-card p-4">
        <div className="flex items-center justify-between gap-3">
          <select
            value={language}
            onChange={(e) => handleLanguageChange(e.target.value as CodingLanguage)}
            className="h-9 rounded-lg border border-border bg-background px-3 text-sm"
          >
            {availableLanguages.map((l) => (
              <option key={l.language} value={l.language}>
                {LANGUAGE_LABEL[l.language]}
              </option>
            ))}
          </select>
          {saving ? <span className="text-xs text-muted-foreground">Saving...</span> : null}
        </div>

        <div className="overflow-hidden rounded-lg border border-border">
          <Editor
            height="420px"
            language={MONACO_LANGUAGE[language]}
            value={code}
            onChange={handleCodeChange}
            options={{ readOnly: isPreview, minimap: { enabled: false }, fontSize: 13 }}
            theme="vs-dark"
          />
        </div>

        {runResult ? (
          <div className="rounded-lg border border-border bg-muted/30 p-3 text-sm">
            <Badge variant="outline" className="mb-1">
              {runResult.status}
            </Badge>
            <p className="text-muted-foreground">{runResult.message}</p>
          </div>
        ) : null}

        {!isPreview ? (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={handleRun} disabled={running || !attempt}>
              <Play className="size-4" /> {running ? "Running..." : "Run / Check"}
            </Button>
            <Button size="sm" onClick={handleSubmit} disabled={submitting || !attempt}>
              <Send className="size-4" /> {submitting ? "Submitting..." : "Submit"}
            </Button>
          </div>
        ) : null}
      </div>
    </div>
  );
}
