import { Clock, Maximize } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getPracticalAttempt, savePracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import type { PracticalAttempt } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
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
  const { data, error, loading, refetch } = useAsync(() => getPracticalAttempt(id), [id]);

  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  const attempt = data && "remainingSeconds" in data ? (data as PracticalAttempt) : null;

  const { isFullscreen, requestFullscreen } = useIntegrityMonitor({
    attemptId: attempt?.id ?? null,
    active: attempt?.status === "IN_PROGRESS",
    policy: attempt?.integrityPolicy ?? null,
  });

  // Same discipline as AssessmentTakingPage: once terminal, stop rendering the workspace and hand
  // off to the result page — the deadline itself is enforced server-side, this effect never
  // decides expiry on its own, it just reacts to what the backend already reports.
  useEffect(() => {
    if (!data) return;
    if (!("remainingSeconds" in data)) {
      navigate(ROUTES.practicalAttemptResult(id), { replace: true });
      return;
    }
    setRemainingSeconds(data.remainingSeconds);
  }, [data, id, navigate]);

  useEffect(() => {
    if (!attempt || attempt.remainingSeconds == null) return;
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

  async function handleSave(patch: Parameters<typeof savePracticalAttempt>[1]) {
    if (!attempt) return;
    setSaving(true);
    try {
      await savePracticalAttempt(attempt.id, patch);
    } finally {
      setSaving(false);
    }
  }

  function handleSubmitted() {
    navigate(ROUTES.practicalAttemptResult(id), { replace: true });
  }

  if (loading) {
    return <LoadingState label="Loading assessment..." />;
  }
  if (error || !attempt) {
    return <ErrorState title="Attempt not found" message="This attempt isn't available." onRetry={refetch} />;
  }

  const Workspace = WORKSPACE_REGISTRY[attempt.workspaceType];
  const fullscreenRequired = attempt.integrityPolicy.requireFullscreen;

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
          </div>
        </div>
        {remainingSeconds !== null ? (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-3 py-1.5 text-sm font-medium text-foreground">
            <Clock className="size-4" /> {formatTime(remainingSeconds)}
          </span>
        ) : null}
      </div>

      <Workspace assessment={attempt} attempt={attempt} mode="attempt" onSave={handleSave} saving={saving} onSubmitted={handleSubmitted} />
    </div>
  );
}
