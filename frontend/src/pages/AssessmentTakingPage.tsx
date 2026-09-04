import { Check, Clock } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { getAssessment, saveAssessmentAnswer, submitAssessment } from "@/lib/api/endpoints/assessments";
import type { AssessmentDetailResponse, AssessmentQuestionView } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { AssessmentOptionList } from "@/pages/assessment/AssessmentOptionList";
import { AssessmentProgressBar } from "@/pages/assessment/AssessmentProgressBar";
import { AssessmentQuestionNav } from "@/pages/assessment/AssessmentQuestionNav";

type SaveState = "idle" | "saving" | "saved" | "error";

// Non-blocking countdown warnings (toasts auto-dismiss, never gate interaction). Thresholds already
// behind the initial remainingSeconds on load/resume are pre-marked "warned" below so a student
// resuming with, say, 4 minutes left only ever sees the still-upcoming "1 minute remaining" toast,
// never a stale "10 minutes remaining" for a milestone that already passed.
const TIME_WARNINGS: { thresholdSeconds: number; message: string }[] = [
  { thresholdSeconds: 600, message: "10 minutes remaining" },
  { thresholdSeconds: 300, message: "5 minutes remaining" },
  { thresholdSeconds: 60, message: "1 minute remaining" },
];

function formatTime(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function AssessmentTakingPage() {
  const { assessmentId = "" } = useParams<{ assessmentId: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const { data, error, loading, refetch } = useAsync(() => getAssessment(assessmentId), [assessmentId]);

  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<string, string[]>>({});
  const [saveStates, setSaveStates] = useState<Record<string, SaveState>>({});
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const warnedThresholdsRef = useRef<Set<number>>(new Set());

  const detail = data && data.status === "IN_PROGRESS" ? (data as AssessmentDetailResponse) : null;

  // Once the assessment reaches a terminal status (submitted by us, or expired server-side), stop
  // rendering the taking UI entirely and hand off to the result page — never keep answering.
  useEffect(() => {
    if (!data) return;
    if (data.status !== "IN_PROGRESS" || !("remainingSeconds" in data)) {
      navigate(ROUTES.assessmentResult(assessmentId), { replace: true, state: { result: data } });
      return;
    }
    setAnswers(Object.fromEntries(data.questions.map((q) => [q.id, q.selectedOptionIds])));
    setSaveStates(Object.fromEntries(data.questions.map((q) => [q.id, q.selectedOptionIds.length > 0 ? "saved" : "idle"])));
    setRemainingSeconds(data.remainingSeconds);
    setIndex(0);
    warnedThresholdsRef.current = new Set(
      TIME_WARNINGS.filter((w) => data.remainingSeconds != null && data.remainingSeconds <= w.thresholdSeconds).map(
        (w) => w.thresholdSeconds
      )
    );
  }, [data, assessmentId, navigate]);

  // Local ticking display only — the deadline itself is enforced server-side. Hitting zero just
  // triggers a refetch, which is what actually discovers (and safely finalizes) an expired
  // assessment; this effect never decides expiry on its own. The same tick also fires non-blocking
  // toast warnings the first time the countdown crosses 10/5/1 minutes remaining.
  useEffect(() => {
    if (!detail || detail.timeLimitSeconds == null) return;
    const timer = window.setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev === null) return prev;
        const next = prev <= 1 ? 0 : prev - 1;
        for (const warning of TIME_WARNINGS) {
          if (next <= warning.thresholdSeconds && !warnedThresholdsRef.current.has(warning.thresholdSeconds)) {
            warnedThresholdsRef.current.add(warning.thresholdSeconds);
            toast.info(warning.message);
          }
        }
        if (next === 0) {
          window.clearInterval(timer);
          refetch();
        }
        return next;
      });
    }, 1000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail?.id]);

  function handleSelect(question: AssessmentQuestionView, selectedOptionIds: string[]) {
    setAnswers((prev) => ({ ...prev, [question.id]: selectedOptionIds }));
    setSaveStates((prev) => ({ ...prev, [question.id]: "saving" }));
    saveAssessmentAnswer(assessmentId, question.id, selectedOptionIds)
      .then(() => setSaveStates((prev) => ({ ...prev, [question.id]: "saved" })))
      .catch(() => setSaveStates((prev) => ({ ...prev, [question.id]: "error" })));
  }

  async function handleSubmit() {
    setSubmitting(true);
    try {
      const result = await submitAssessment(assessmentId);
      navigate(ROUTES.assessmentResult(assessmentId), { replace: true, state: { result } });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't submit your assessment. Please try again.");
      setSubmitting(false);
      setConfirmOpen(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading assessment..." />;
  }

  if (error || !data || !detail) {
    return <ErrorState title="Assessment unavailable" message={error?.message ?? "This assessment isn't available."} onRetry={refetch} />;
  }

  const questions = detail.questions;
  const currentQuestion = questions[index];
  const answeredIndexes = new Set(
    questions.flatMap((question, i) => ((answers[question.id]?.length ?? 0) > 0 ? [i] : []))
  );
  const saveState = saveStates[currentQuestion.id] ?? "idle";

  return (
    <div className="mx-auto max-w-2xl space-y-5 px-4 pt-6 pb-[calc(4rem+env(safe-area-inset-bottom)+1.5rem)] sm:px-0 lg:pb-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="truncate text-lg font-bold text-foreground">{detail.skillName} Assessment</h1>
        {remainingSeconds !== null ? (
          <span
            className={cn(
              "flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1 text-sm font-semibold tabular-nums",
              remainingSeconds < 60 ? "bg-destructive/10 text-destructive" : "bg-muted text-foreground"
            )}
          >
            <Clock className="size-4" /> {formatTime(remainingSeconds)}
          </span>
        ) : null}
      </div>

      <AssessmentProgressBar current={index + 1} total={questions.length} />
      <AssessmentQuestionNav
        total={questions.length}
        currentIndex={index}
        answeredIndexes={answeredIndexes}
        onSelect={setIndex}
      />

      <Card>
        <CardContent className="space-y-5 pt-5">
          <QuestionContent
            text={currentQuestion.questionText}
            textClassName="text-base leading-relaxed font-medium text-foreground"
          />
          {currentQuestion.questionType === "MCQ_MULTIPLE" ? (
            <p className="-mt-3 text-xs text-muted-foreground">Select all that apply.</p>
          ) : null}
          <AssessmentOptionList
            options={currentQuestion.options}
            questionType={currentQuestion.questionType}
            selectedOptionIds={answers[currentQuestion.id] ?? []}
            onChange={(ids) => handleSelect(currentQuestion, ids)}
          />
          <div className="min-h-4 text-xs">
            {saveState === "saving" ? (
              <span className="text-muted-foreground">Saving...</span>
            ) : saveState === "saved" ? (
              <span className="inline-flex items-center gap-1 text-primary">
                <Check className="size-3.5" /> Saved
              </span>
            ) : saveState === "error" ? (
              <button
                type="button"
                className="font-medium text-destructive underline underline-offset-2"
                onClick={() => handleSelect(currentQuestion, answers[currentQuestion.id] ?? [])}
              >
                Couldn't save — tap to retry
              </button>
            ) : null}
          </div>
        </CardContent>
      </Card>

      <div className="border-t border-border pt-4 sm:border-0 sm:pt-0">
        <div className="flex items-center gap-2">
          <Button variant="outline" className="flex-1" onClick={() => setIndex((i) => Math.max(i - 1, 0))} disabled={index === 0}>
            Previous
          </Button>
          {index === questions.length - 1 ? (
            <Button className="flex-1" onClick={() => setConfirmOpen(true)}>
              Submit Assessment
            </Button>
          ) : (
            <Button className="flex-1" onClick={() => setIndex((i) => Math.min(i + 1, questions.length - 1))}>
              Next
            </Button>
          )}
        </div>
      </div>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Submit assessment?</DialogTitle>
            <DialogDescription>
              {answeredIndexes.size === questions.length
                ? "Are you sure you want to submit? You won't be able to change your answers afterward."
                : `You've answered ${answeredIndexes.size} of ${questions.length} questions. Unanswered questions will be marked incorrect. Are you sure you want to submit?`}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleSubmit} disabled={submitting}>
              {submitting ? "Submitting..." : "Submit"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
