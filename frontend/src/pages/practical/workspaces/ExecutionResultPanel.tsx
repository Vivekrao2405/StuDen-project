import { CheckCircle2, XCircle } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import type { RunResult } from "@/lib/api/practicalTypes";
import { executionStatusBadgeVariant, executionStatusLabel } from "@/pages/practical/practicalDisplay";

// Shared Run-result rendering for CodingWorkspace/SqlWorkspace — public test cases only (hidden
// ones are structurally absent from RunResult, never sent to the student).
export function ExecutionResultPanel({ result }: { result: RunResult }) {
  return (
    <div className="space-y-3 rounded-lg border border-border bg-muted/30 p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <Badge variant={executionStatusBadgeVariant(result.status)}>{executionStatusLabel(result.status)}</Badge>
        {result.durationMs != null ? <span className="text-xs text-muted-foreground">{result.durationMs} ms</span> : null}
      </div>
      <p className="text-muted-foreground">{result.message}</p>

      {result.compileError ? (
        <pre className="overflow-x-auto rounded-md border border-destructive/30 bg-destructive/5 p-2 text-xs whitespace-pre-wrap text-destructive">
          {result.compileError}
        </pre>
      ) : null}

      {result.publicTestResults.length > 0 ? (
        <div className="space-y-2">
          {result.publicTestResults.map((tc, i) => (
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
                {tc.executionTimeMs != null ? (
                  <span className="ml-auto font-normal text-muted-foreground">{tc.executionTimeMs} ms</span>
                ) : null}
              </div>
              {tc.input != null ? (
                <p className="mt-1 text-muted-foreground">
                  Input: <code className="whitespace-pre-wrap">{tc.input}</code>
                </p>
              ) : null}
              {tc.expectedOutput != null ? (
                <p className="text-muted-foreground">
                  Expected: <code className="whitespace-pre-wrap">{tc.expectedOutput}</code>
                </p>
              ) : null}
              {tc.actualOutput != null ? (
                <p className="text-muted-foreground">
                  {tc.expectedOutput != null ? "Actual" : "Result"}: <code className="whitespace-pre-wrap">{tc.actualOutput}</code>
                </p>
              ) : null}
            </div>
          ))}
        </div>
      ) : null}

      {result.hiddenTestsTotal ? (
        <p className="text-xs text-muted-foreground">
          {result.hiddenTestsTotal} hidden test{result.hiddenTestsTotal === 1 ? "" : "s"}: {result.hiddenTestsPassed}/
          {result.hiddenTestsTotal} passed
        </p>
      ) : null}
    </div>
  );
}
