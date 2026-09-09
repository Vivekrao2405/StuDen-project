import { ArrowLeft, ArrowRight, CheckCircle2, ClipboardCheck, Clock, FileQuestion } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { getPlacementReadinessStatus, startPlacementReadinessAttempt } from "@/lib/api/endpoints/placement";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { CircularScore } from "@/pages/home/CircularScore";
import { skillReadinessStatusColorClasses, skillReadinessStatusLabel } from "@/pages/placement/placementReadinessDisplay";

/** Entry point for "Placement Readiness". Reads GET /placement/readiness/status, which is the
 * single source of truth for availability — this page never guesses whether a profile or
 * assessment exists on its own. */
export function PlacementReadinessPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const { data, error, loading, refetch } = useAsync(() => getPlacementReadinessStatus(), []);
  const [starting, setStarting] = useState(false);

  async function handleStartOrResume() {
    setStarting(true);
    try {
      const attempt = await startPlacementReadinessAttempt();
      navigate(ROUTES.placementReadinessAttempt(attempt.id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't start your readiness assessment. Please try again.");
    } finally {
      setStarting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading your placement readiness..." />;
  }
  if (error || !data) {
    return <ErrorState message={error?.message ?? "Couldn't load Placement Readiness."} onRetry={refetch} />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.placement}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Placement
      </Link>

      <div>
        <p className="text-xs font-semibold tracking-wide text-primary uppercase">Placement Readiness</p>
        <h1 className="mt-1 text-2xl font-bold text-foreground">Where do you stand?</h1>
        <p className="mt-1 text-sm text-muted-foreground">
          This assessment measures your readiness for your target role — a role-specific mix of the skills that role
          requires, scored and broken down by skill.
        </p>
      </div>

      {data.state === "NO_PROFILE" ? (
        <Card>
          <CardContent className="space-y-4 py-8 text-center">
            <ClipboardCheck className="mx-auto size-8 text-muted-foreground" />
            <div>
              <h2 className="text-base font-semibold text-foreground">Complete your Placement Profile first</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                We need your target role before we can generate a role-specific readiness assessment for you.
              </p>
            </div>
            <Button render={<Link to={ROUTES.placement} />}>Set Up Placement Profile</Button>
          </CardContent>
        </Card>
      ) : null}

      {data.state === "NO_ASSESSMENT_AVAILABLE" ? (
        <Card>
          <CardContent className="space-y-2 py-8 text-center">
            <FileQuestion className="mx-auto size-8 text-muted-foreground" />
            <h2 className="text-base font-semibold text-foreground">Not available yet</h2>
            <p className="text-sm text-muted-foreground">
              A readiness assessment isn't configured for {data.targetRoleName ?? "your target role"} yet. Check back
              soon.
            </p>
          </CardContent>
        </Card>
      ) : null}

      {data.state === "ASSESSMENT_AVAILABLE" ? (
        <Card>
          <CardContent className="space-y-5 pt-6">
            <div>
              <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                Target Role: {data.targetRoleName}
              </p>
              <h2 className="mt-1 text-lg font-bold text-foreground">{data.assessmentTitle}</h2>
            </div>
            <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <FileQuestion className="size-4" /> {data.questionCount} Questions
              </span>
              {data.durationMinutes != null ? (
                <span className="inline-flex items-center gap-1.5">
                  <Clock className="size-4" /> {data.durationMinutes} min
                </span>
              ) : null}
            </div>
            <Button className="w-full" size="lg" onClick={handleStartOrResume} disabled={starting}>
              {starting ? "Starting..." : data.inProgressAttemptId ? "Resume Assessment" : "Start Assessment"}
              {!starting ? <ArrowRight className="size-4" /> : null}
            </Button>
          </CardContent>
        </Card>
      ) : null}

      {data.latestResult ? (
        <Card>
          <CardContent className="flex flex-wrap items-center gap-5 py-5">
            <div className="relative flex shrink-0 items-center justify-center">
              <CircularScore value={data.latestResult.scorePercentage} size={72} strokeWidth={6} />
              <div className="absolute flex flex-col items-center leading-none">
                <span className="text-lg font-bold text-foreground">{data.latestResult.scorePercentage}</span>
                <span className="text-[10px] text-muted-foreground">/100</span>
              </div>
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-foreground">Your Latest Readiness Result</p>
              <p className="text-xs text-muted-foreground">
                {data.latestResult.correctCount}/{data.latestResult.totalQuestions} correct
              </p>
              <div className="mt-2 flex flex-wrap gap-1.5">
                {data.latestResult.skillBreakdown.slice(0, 4).map((skill) => (
                  <span
                    key={skill.skillId}
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${skillReadinessStatusColorClasses(skill.status)}`}
                  >
                    {skill.skillName} · {skillReadinessStatusLabel(skill.status)}
                  </span>
                ))}
              </div>
            </div>
            <Button
              variant="outline"
              size="sm"
              render={<Link to={ROUTES.placementReadinessResult(data.latestResult.attemptId)} />}
            >
              <CheckCircle2 className="size-4" /> View Result
            </Button>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
