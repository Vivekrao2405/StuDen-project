import { ArrowLeft, Check, TriangleAlert, X } from "lucide-react";
import { Link, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { getPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { PracticalAttemptQuestionResult, PracticalAttemptResult } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { attemptStatusBadgeVariant, attemptStatusLabel, PRACTICAL_TYPE_LABEL, questionStatusLabel } from "@/pages/practical/practicalDisplay";

function QuestionResultIcon({ result }: { result: PracticalAttemptQuestionResult }) {
  if (result.status === "NOT_ATTEMPTED") {
    return <span className="text-muted-foreground">—</span>;
  }
  if (result.status === "PASSED" || result.status === "EVALUATED") {
    return <Check className="size-4 text-emerald-600 dark:text-emerald-400" />;
  }
  if (result.status === "PARTIAL" || result.status === "UNDER_REVIEW") {
    return <TriangleAlert className="size-4 text-amber-600 dark:text-amber-400" />;
  }
  return <X className="size-4 text-destructive" />;
}

export function PracticalAttemptResultPage() {
  const { id = "" } = useParams<{ id: string }>();
  const { data, error, loading, refetch } = useAsync(() => getPracticalAttempt(id), [id]);
  const result = data && "questionResults" in data ? (data as PracticalAttemptResult) : null;

  if (loading) {
    return <LoadingState label="Loading result..." />;
  }
  if (error || !result) {
    return <ErrorState title="Result not found" message="This result isn't available." onRetry={refetch} />;
  }

  // SUBMITTED is a terminal, auto-graded outcome (Phase 7.5) — only UNDER_REVIEW is still awaiting
  // a human. Auto-graded results always carry a score, same as EVALUATED.
  const isPending = result.status === "UNDER_REVIEW";
  const multiQuestion = result.questionResults.length > 1;

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
              <div>
                <p className="text-sm font-medium text-muted-foreground">Assessment Score</p>
                <div className="flex items-baseline gap-2">
                  <span className="text-4xl font-bold text-foreground">{result.score ?? "—"}</span>
                  <span className="text-lg text-muted-foreground">/ {result.maxScore ?? 100}</span>
                </div>
              </div>

              {multiQuestion ? (
                <div className="space-y-2">
                  <h2 className="text-sm font-semibold text-foreground">Question Breakdown</h2>
                  <div className="space-y-1.5">
                    {result.questionResults.map((qr, i) => (
                      <div
                        key={qr.practicalQuestionId}
                        className="flex items-center justify-between gap-3 rounded-lg border border-border bg-muted/20 px-3 py-2 text-sm"
                      >
                        <span className="flex items-center gap-2 text-foreground">
                          <QuestionResultIcon result={qr} />
                          Question {i + 1} — {qr.title}
                        </span>
                        <span className="shrink-0 font-medium text-foreground">
                          {qr.pointsEarned ?? "—"} / {qr.pointsPossible}
                          {qr.testsTotal != null ? (
                            <span className="ml-1.5 text-xs font-normal text-muted-foreground">
                              ({qr.testsPassed}/{qr.testsTotal} tests)
                            </span>
                          ) : null}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}

              {result.skillPerformance.length > 1 ? (
                <div className="space-y-2">
                  <h2 className="text-sm font-semibold text-foreground">Skills Evaluated</h2>
                  <div className="space-y-1.5">
                    {result.skillPerformance.map((sp) => (
                      <div key={sp.skillId} className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">{sp.skillName}</span>
                        <span className="font-medium text-foreground">{sp.percentage}%</span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}

              {result.questionResults.some((qr) => qr.rubricScores.length > 0) ? (
                <div className="space-y-2">
                  <h2 className="text-sm font-semibold text-foreground">Criteria</h2>
                  {result.questionResults.flatMap((qr) =>
                    qr.rubricScores.map((rs) => (
                      <div key={rs.criterionId} className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">{rs.criterion}</span>
                        <span className="font-medium text-foreground">
                          {rs.pointsAwarded} / {rs.maxPoints}
                        </span>
                      </div>
                    ))
                  )}
                </div>
              ) : null}

              {result.questionResults.some((qr) => qr.feedback) ? (
                <div className="space-y-2">
                  <h2 className="text-sm font-semibold text-foreground">Question Feedback</h2>
                  {result.questionResults
                    .filter((qr) => qr.feedback)
                    .map((qr) => (
                      <div key={qr.practicalQuestionId} className="space-y-0.5">
                        <p className="text-xs font-medium text-foreground">
                          {qr.title} — {questionStatusLabel(qr.status)}
                        </p>
                        <p className="whitespace-pre-wrap text-sm text-muted-foreground">{qr.feedback}</p>
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
