import { Badge } from "@/components/ui/badge";
import { getExecutionHistory } from "@/lib/api/endpoints/practicalAssessments";
import { useAsync } from "@/lib/hooks/useAsync";
import { executionStatusBadgeVariant, executionStatusLabel } from "@/pages/practical/practicalDisplay";

// "Run #1 Compilation Error, Run #2 3/10, Run #3 10/10... Final Submission 10/10" — scoped to one
// question; pass refreshKey a new value after every Run to refetch.
export function RunHistoryPanel({
  attemptId,
  questionId,
  refreshKey,
}: {
  attemptId: string;
  questionId: string;
  refreshKey: number;
}) {
  const { data } = useAsync(() => getExecutionHistory(attemptId, questionId), [attemptId, questionId, refreshKey]);

  if (!data || data.length === 0) {
    return null;
  }

  let runCounter = 0;

  return (
    <div className="space-y-1.5">
      <h3 className="text-xs font-medium text-muted-foreground">Run History</h3>
      <div className="space-y-1">
        {data.map((job) => {
          const label = job.kind === "SUBMIT" ? "Final Submission" : `Run #${++runCounter}`;
          return (
            <div key={job.id} className="flex items-center justify-between rounded-md border border-border px-2 py-1 text-xs">
              <span className="text-muted-foreground">{label}</span>
              <div className="flex items-center gap-2">
                {job.testsTotal != null ? (
                  <span className="text-muted-foreground">
                    {job.testsPassed}/{job.testsTotal}
                  </span>
                ) : null}
                <Badge variant={executionStatusBadgeVariant(job.status)}>{executionStatusLabel(job.status)}</Badge>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
