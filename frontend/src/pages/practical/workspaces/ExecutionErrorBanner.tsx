import { AlertTriangle } from "lucide-react";

// Shown when a Run/Check call itself fails at the infrastructure level (HTTP 503
// EXECUTION_SERVICE_UNAVAILABLE) rather than returning a real pass/fail result — distinct from
// ExecutionResultPanel, which renders an actual RunResult. The student's code is never lost: it's
// already autosaved independently of this call.
export function ExecutionErrorBanner({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
      <AlertTriangle className="mt-0.5 size-4 shrink-0" />
      <p>{message}</p>
    </div>
  );
}
