import { ArrowLeft, BookOpen, Check, Code2, ExternalLink, Loader2, X } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  answerPlacementPrepModuleItem,
  getPlacementPrepModuleItem,
  startPlacementPrepPractical,
} from "@/lib/api/endpoints/placement";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { AssessmentOptionList } from "@/pages/assessment/AssessmentOptionList";

const PRACTICAL_TERMINAL_STATUSES = new Set(["SUBMITTED", "EVALUATED", "EXPIRED"]);

const PRACTICAL_STATUS_LABEL: Record<string, string> = {
  IN_PROGRESS: "In Progress",
  SUBMITTED: "Submitted",
  UNDER_REVIEW: "Awaiting Evaluation",
  EVALUATED: "Evaluated",
  EXPIRED: "Time's Up",
  CANCELLED: "Cancelled",
};

/** One placement module item — the shape branches on itemType exactly like the response does.
 * QUESTION is answered inline (Phase 5's one genuinely new practice surface); PRACTICAL_ASSESSMENT
 * deep-links into the existing /practical-attempts/:id workspace; RESOURCE deep-links into the
 * existing My Learning resource viewer — neither duplicates an existing system. */
export function PlacementPrepModuleItemPage() {
  const { itemId = "" } = useParams<{ itemId: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const detail = useAsync(() => getPlacementPrepModuleItem(itemId), [itemId]);

  const [selected, setSelected] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [answerResult, setAnswerResult] = useState<{ correct: boolean; correctOptionIds: string[]; explanation: string | null } | null>(null);
  const [startingPractical, setStartingPractical] = useState(false);

  const item = detail.data;

  async function handleSubmitAnswer() {
    if (!item || selected.length === 0) return;
    setSubmitting(true);
    try {
      const response = await answerPlacementPrepModuleItem(item.id, selected);
      setAnswerResult({ correct: response.correct, correctOptionIds: response.correctOptionIds, explanation: response.explanation });
      toast.success(response.correct ? "Correct!" : "Recorded — check the explanation below.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't submit your answer. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleStartPractical() {
    if (!item) return;
    setStartingPractical(true);
    try {
      const refreshed = await startPlacementPrepPractical(item.id);
      if (refreshed.practicalAttemptId) {
        navigate(ROUTES.practicalAttempt(refreshed.practicalAttemptId));
      } else {
        detail.refetch();
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't start this practical task. Please try again.");
      setStartingPractical(false);
    }
  }

  if (detail.loading) {
    return <LoadingState label="Loading..." />;
  }
  if (detail.error || !item) {
    return (
      <ErrorState title="Content unavailable" message={detail.error?.message ?? "This item isn't available."} onRetry={detail.refetch} />
    );
  }

  const alreadyCompleted = item.status === "COMPLETED";
  const revealed = answerResult !== null || (alreadyCompleted && item.correctOptionIds !== null);
  const correctIds = new Set(answerResult?.correctOptionIds ?? item.correctOptionIds ?? []);

  return (
    <div className="mx-auto max-w-2xl space-y-5">
      <Link
        to={ROUTES.placementPrepSeriesDetail(item.seriesId)}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Series
      </Link>

      <Card>
        <CardContent className="space-y-5 pt-5">
          <p className="text-xs font-medium tracking-wide text-primary uppercase">{item.skillName}</p>

          {item.itemType === "QUESTION" ? (
            <>
              <QuestionContent text={item.questionText ?? ""} textClassName="text-base leading-relaxed font-medium text-foreground" />
              {item.questionType === "MCQ_MULTIPLE" ? (
                <p className="-mt-3 text-xs text-muted-foreground">Select all that apply.</p>
              ) : null}
              <AssessmentOptionList
                options={item.options}
                questionType={item.questionType ?? "MCQ_SINGLE"}
                selectedOptionIds={revealed ? Array.from(correctIds) : selected}
                onChange={setSelected}
                disabled={revealed}
              />
              {revealed ? (
                <div
                  className={cn(
                    "rounded-lg border p-3 text-sm",
                    answerResult?.correct
                      ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700"
                      : answerResult
                        ? "border-destructive/30 bg-destructive/10 text-destructive"
                        : "border-border bg-muted/40 text-foreground"
                  )}
                >
                  {answerResult ? (
                    <p className="flex items-center gap-1.5 font-semibold">
                      {answerResult.correct ? <Check className="size-4" /> : <X className="size-4" />}
                      {answerResult.correct ? "Correct" : "Not quite"}
                    </p>
                  ) : (
                    <p className="font-semibold">Already completed</p>
                  )}
                  {(answerResult?.explanation ?? item.explanation) ? (
                    <p className="mt-1 text-muted-foreground">{answerResult?.explanation ?? item.explanation}</p>
                  ) : null}
                </div>
              ) : (
                <Button className="w-full" disabled={selected.length === 0 || submitting} onClick={handleSubmitAnswer}>
                  {submitting ? <Loader2 className="size-4 animate-spin" /> : null}
                  Submit Answer
                </Button>
              )}
            </>
          ) : item.itemType === "PRACTICAL_ASSESSMENT" ? (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                  <Code2 className="size-5 text-primary" />
                </span>
                <div>
                  <h2 className="text-base font-semibold text-foreground">{item.practicalAssessmentTitle}</h2>
                  <p className="text-sm text-muted-foreground">
                    This is a practical/coding task — write and run your solution in its own workspace.
                  </p>
                </div>
              </div>
              {item.practicalAttemptStatus ? (
                <span
                  className={cn(
                    "inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold",
                    PRACTICAL_TERMINAL_STATUSES.has(item.practicalAttemptStatus)
                      ? "bg-emerald-500/10 text-emerald-600"
                      : "bg-amber-500/10 text-amber-600"
                  )}
                >
                  {PRACTICAL_STATUS_LABEL[item.practicalAttemptStatus] ?? item.practicalAttemptStatus}
                </span>
              ) : null}
              {item.practicalAttemptId ? (
                <Button render={<Link to={ROUTES.practicalAttempt(item.practicalAttemptId)} />}>
                  {item.practicalAttemptStatus && PRACTICAL_TERMINAL_STATUSES.has(item.practicalAttemptStatus)
                    ? "View Submission"
                    : "Open Coding Task"}
                  <ExternalLink className="size-4" />
                </Button>
              ) : (
                <Button onClick={handleStartPractical} disabled={startingPractical}>
                  {startingPractical ? <Loader2 className="size-4 animate-spin" /> : null}
                  Start Coding Task
                </Button>
              )}
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                  <BookOpen className="size-5 text-primary" />
                </span>
                <div>
                  <h2 className="text-base font-semibold text-foreground">{item.resourceTitle}</h2>
                  <p className="text-sm text-muted-foreground">
                    {item.status === "COMPLETED" ? "You've completed this resource." : "Open this resource to study it."}
                  </p>
                </div>
              </div>
              <Button render={<Link to={ROUTES.myLearningResourceDetail(item.resourceId ?? "")} />}>
                {item.status === "NOT_STARTED" ? "Open Resource" : item.status === "COMPLETED" ? "Review Again" : "Continue"}
                <ExternalLink className="size-4" />
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
