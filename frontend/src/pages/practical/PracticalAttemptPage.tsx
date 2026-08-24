import { ChevronLeft, ChevronRight, Clock, Maximize, Send } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { getPracticalAttempt, savePracticalAttempt, submitPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { PracticalAttempt } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { QuestionNavigator } from "@/pages/practical/QuestionNavigator";
import { attemptStatusLabel, PRACTICAL_TYPE_LABEL } from "@/pages/practical/practicalDisplay";
import { useIntegrityMonitor } from "@/pages/practical/useIntegrityMonitor";
import { WORKSPACE_REGISTRY } from "@/pages/practical/workspaces/registry";

function formatTime(totalSeconds: number) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function PracticalAttemptPage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const { data, error, loading, refetch } = useAsync(() => getPracticalAttempt(id), [id]);

  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);

  const attempt = data && "questions" in data ? (data as PracticalAttempt) : null;
  const activeQuestion = attempt?.questions[activeIndex] ?? null;

  const { isFullscreen, requestFullscreen } = useIntegrityMonitor({
    attemptId: attempt?.id ?? null,
    active: attempt?.status === "IN_PROGRESS",
    policy: attempt?.integrityPolicy ?? null,
    currentQuestionId: activeQuestion?.practicalQuestionId ?? null,
  });

  // Same discipline as AssessmentTakingPage: once terminal, stop rendering the workspace and hand
  // off to the result page — the deadline itself is enforced server-side, this effect never
  // decides expiry on its own, it just reacts to what the backend already reports.
  useEffect(() => {
    if (!data) return;
    if (!("questions" in data)) {
      navigate(ROUTES.practicalAttemptResult(id), { replace: true });
      return;
    }
    setRemainingSeconds(data.remainingSeconds);
    setActiveIndex((prev) => Math.min(prev, Math.max(0, data.questions.length - 1)));
  }, [data, id, navigate]);

  useEffect(() => {
    if (!attempt || remainingSeconds == null) return;
    const timer = window.setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev === null) return prev;
        if (prev <= 1) {
          window.clearInterval(timer);
          refetch();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => window.clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attempt?.id]);

  async function handleSave(patch: Parameters<typeof savePracticalAttempt>[2]) {
    if (!attempt || !activeQuestion) return;
    setSaving(true);
    try {
      await savePracticalAttempt(attempt.id, activeQuestion.id, patch);
    } finally {
      setSaving(false);
    }
  }

  async function handleSubmitAssessment() {
    if (!attempt) return;
    setSubmitting(true);
    try {
      await submitPracticalAttempt(attempt.id);
      navigate(ROUTES.practicalAttemptResult(id), { replace: true });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading assessment..." />;
  }
  if (error || !attempt || !activeQuestion) {
    return <ErrorState title="Attempt not found" message="This attempt isn't available." onRetry={refetch} />;
  }

  const Workspace = WORKSPACE_REGISTRY[attempt.workspaceType];
  const fullscreenRequired = attempt.integrityPolicy.requireFullscreen;
  const questionCount = attempt.questions.length;

  // A required-fullscreen assessment blocks interaction behind an explicit-gesture overlay
  // (browsers refuse requestFullscreen() without one) rather than silently trying and failing —
  // and never becomes permanently unusable if the browser refuses fullscreen outright (goal #8).
  if (fullscreenRequired && !isFullscreen) {
    return (
      <div className="flex min-h-[50vh] flex-col items-center justify-center gap-4 rounded-xl border border-border bg-card p-8 text-center">
        <Maximize className="size-8 text-muted-foreground" />
        <div>
          <h2 className="font-semibold text-foreground">Fullscreen required</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            This assessment requires fullscreen mode. Your progress is saved — enter fullscreen to continue.
          </p>
        </div>
        <Button onClick={requestFullscreen}>
          <Maximize className="size-4" /> Enter Fullscreen
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-4">
        <div>
          <h1 className="text-lg font-bold text-foreground">{attempt.title}</h1>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <Badge variant="outline">{PRACTICAL_TYPE_LABEL[attempt.practicalType]}</Badge>
            <Badge variant="secondary">{attemptStatusLabel(attempt.status)}</Badge>
            {questionCount > 1 ? (
              <span className="text-xs font-medium text-muted-foreground">
                Question {activeIndex + 1} of {questionCount}
              </span>
            ) : null}
          </div>
        </div>
        {remainingSeconds !== null ? (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-3 py-1.5 text-sm font-medium text-foreground">
            <Clock className="size-4" /> {formatTime(remainingSeconds)}
          </span>
        ) : null}
      </div>

      {questionCount > 1 ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-3">
          <QuestionNavigator questions={attempt.questions} activeIndex={activeIndex} onSelect={setActiveIndex} />
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setActiveIndex((i) => Math.max(0, i - 1))}
              disabled={activeIndex === 0}
            >
              <ChevronLeft className="size-4" /> Previous
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setActiveIndex((i) => Math.min(questionCount - 1, i + 1))}
              disabled={activeIndex === questionCount - 1}
            >
              Next <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      ) : null}

      <Workspace
        key={activeQuestion.id}
        assessment={activeQuestion}
        attemptId={attempt.id}
        attempt={activeQuestion}
        mode="attempt"
        onSave={handleSave}
        saving={saving}
      />

      <div className="flex justify-end rounded-xl border border-border bg-card p-4">
        <Button onClick={handleSubmitAssessment} disabled={submitting}>
          <Send className="size-4" /> {submitting ? "Submitting..." : "Submit Assessment"}
        </Button>
      </div>
    </div>
  );
}
