import { CheckCircle2, Play, XCircle } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { adminTestRunPracticalAssessment } from "@/lib/api/endpoints/practicalAssessments";
import type { AdminTestRunResult, CodingLanguage, PracticalType } from "@/lib/api/practicalTypes";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { CODING_LANGUAGE_LABEL, executionStatusBadgeVariant, executionStatusLabel } from "@/pages/practical/practicalDisplay";

const ALL_CODING_LANGUAGES: CodingLanguage[] = ["JAVA", "PYTHON", "C", "CPP"];

/**
 * Admin-only "Test Question" tool (spec: admins need to validate questions before publishing).
 * Runs a supplied solution/query against every test case — public AND hidden — directly against
 * the saved assessment; nothing here creates a PracticalAttempt or touches student data.
 */
export function AdminTestQuestionDialog({
  open,
  onOpenChange,
  assessmentId,
  practicalType,
  availableLanguages,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  assessmentId: string;
  practicalType: PracticalType;
  availableLanguages: CodingLanguage[];
}) {
  const toast = useToast();
  const isSql = practicalType === "SQL";
  const [language, setLanguage] = useState<CodingLanguage>(availableLanguages[0] ?? "JAVA");
  const [source, setSource] = useState("");
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<AdminTestRunResult | null>(null);

  async function handleRun() {
    if (!source.trim()) {
      toast.error(isSql ? "Enter a query first." : "Enter a solution first.");
      return;
    }
    setRunning(true);
    setResult(null);
    try {
      const response = await adminTestRunPracticalAssessment(assessmentId, {
        language: isSql ? null : language,
        sourceCode: source,
      });
      setResult(response);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Test run failed. Please try again.");
    } finally {
      setRunning(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Test Question</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          {!isSql ? (
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value as CodingLanguage)}
              className={QB_SELECT_CLASS}
            >
              {(availableLanguages.length > 0 ? availableLanguages : ALL_CODING_LANGUAGES).map((lang) => (
                <option key={lang} value={lang}>
                  {CODING_LANGUAGE_LABEL[lang]}
                </option>
              ))}
            </select>
          ) : null}

          <Textarea
            rows={8}
            className="font-mono text-xs"
            placeholder={isSql ? "Reference-correct SELECT query..." : "A known-correct solution..."}
            value={source}
            onChange={(e) => setSource(e.target.value)}
          />

          <Button onClick={handleRun} disabled={running}>
            <Play className="size-4" /> {running ? "Running..." : "Run Test"}
          </Button>

          {result ? (
            <div className="space-y-3 rounded-lg border border-border bg-muted/30 p-3 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={executionStatusBadgeVariant(result.status)}>{executionStatusLabel(result.status)}</Badge>
                {result.testsTotal != null ? (
                  <span className="text-xs text-muted-foreground">
                    {result.testsPassed}/{result.testsTotal} passed
                  </span>
                ) : null}
                {result.durationMs != null ? <span className="text-xs text-muted-foreground">{result.durationMs} ms</span> : null}
              </div>
              <p className="text-muted-foreground">{result.message}</p>
              {result.compileError ? (
                <pre className="overflow-x-auto rounded-md border border-destructive/30 bg-destructive/5 p-2 text-xs whitespace-pre-wrap text-destructive">
                  {result.compileError}
                </pre>
              ) : null}
              <div className="space-y-2">
                {result.testResults.map((tc, i) => (
                  <div
                    key={tc.testCaseId}
                    className={`rounded-md border p-2 text-xs ${tc.passed ? "border-border" : "border-destructive/30 bg-destructive/5"}`}
                  >
                    <div className="flex items-center gap-1.5 font-medium text-foreground">
                      {tc.passed ? (
                        <CheckCircle2 className="size-3.5 shrink-0 text-green-600" />
                      ) : (
                        <XCircle className="size-3.5 shrink-0 text-destructive" />
                      )}
                      Test {i + 1}
                      {tc.hidden ? (
                        <Badge variant="outline" className="ml-1">
                          Hidden
                        </Badge>
                      ) : null}
                      {tc.executionTimeMs != null ? (
                        <span className="ml-auto font-normal text-muted-foreground">{tc.executionTimeMs} ms</span>
                      ) : null}
                    </div>
                    {tc.input ? (
                      <p className="mt-1 text-muted-foreground">
                        Input: <code className="whitespace-pre-wrap">{tc.input}</code>
                      </p>
                    ) : null}
                    {tc.expectedOutput ? (
                      <p className="text-muted-foreground">
                        Expected: <code className="whitespace-pre-wrap">{tc.expectedOutput}</code>
                      </p>
                    ) : null}
                    {tc.actualOutput ? (
                      <p className="text-muted-foreground">
                        Actual: <code className="whitespace-pre-wrap">{tc.actualOutput}</code>
                      </p>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      </DialogContent>
    </Dialog>
  );
}
