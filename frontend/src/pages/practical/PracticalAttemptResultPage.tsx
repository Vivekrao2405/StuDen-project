import { ArrowLeft } from "lucide-react";
import { Link, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { getPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { PracticalAttemptResult } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { attemptStatusBadgeVariant, attemptStatusLabel, PRACTICAL_TYPE_LABEL } from "@/pages/practical/practicalDisplay";

export function PracticalAttemptResultPage() {
  const { id = "" } = useParams<{ id: string }>();
  const { data, error, loading, refetch } = useAsync(() => getPracticalAttempt(id), [id]);
  const result = data && "rubricScores" in data ? (data as PracticalAttemptResult) : null;

  if (loading) {
    return <LoadingState label="Loading result..." />;
  }
  if (error || !result) {
    return <ErrorState title="Result not found" message="This result isn't available." onRetry={refetch} />;
  }

  const isPending = result.status === "UNDER_REVIEW" || result.status === "SUBMITTED";

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.skillAssessments}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Skill Assessments
      </Link>

      <Card>
        <CardContent className="space-y-6 pt-6">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{PRACTICAL_TYPE_LABEL[result.practicalType]}</Badge>
              <Badge variant={attemptStatusBadgeVariant(result.status)}>{attemptStatusLabel(result.status)}</Badge>
            </div>
            <h1 className="mt-2 text-xl font-bold text-foreground">{result.title}</h1>
          </div>

          {isPending ? (
            <p className="rounded-lg border border-dashed border-border bg-muted/30 px-4 py-6 text-center text-sm text-muted-foreground">
              Your submission has been saved and is awaiting review. You'll be notified once it's evaluated.
            </p>
          ) : result.status === "EXPIRED" ? (
            <p className="rounded-lg border border-dashed border-border bg-muted/30 px-4 py-6 text-center text-sm text-muted-foreground">
              Time ran out before you submitted this attempt.
            </p>
          ) : (
            <>
              <div className="flex items-baseline gap-2">
                <span className="text-4xl font-bold text-foreground">{result.score ?? "—"}</span>
                <span className="text-lg text-muted-foreground">/ {result.maxScore ?? 100}</span>
              </div>

              {result.rubricScores.length > 0 ? (
                <div className="space-y-2">
                  <h2 className="text-sm font-semibold text-foreground">Criteria</h2>
                  {result.rubricScores.map((rs) => (
                    <div key={rs.criterionId} className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">{rs.criterion}</span>
                      <span className="font-medium text-foreground">
                        {rs.pointsAwarded} / {rs.maxPoints}
                      </span>
                    </div>
                  ))}
                </div>
              ) : null}

              {result.feedback ? (
                <div className="space-y-1.5">
                  <h2 className="text-sm font-semibold text-foreground">Feedback</h2>
                  <p className="whitespace-pre-wrap text-sm text-muted-foreground">{result.feedback}</p>
                </div>
              ) : null}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
