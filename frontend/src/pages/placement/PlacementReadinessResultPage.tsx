import { AlertTriangle, ArrowLeft } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { getPlacementReadinessResult } from "@/lib/api/endpoints/placement";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { CircularScore } from "@/pages/home/CircularScore";
import {
  skillReadinessStatusBarClasses,
  skillReadinessStatusColorClasses,
  skillReadinessStatusLabel,
} from "@/pages/placement/placementReadinessDisplay";

/** The Placement Readiness result: overall score, per-skill breakdown, and ranked priority gaps.
 * Server-authoritative throughout — every number here is read straight off
 * GET /placement/readiness/attempts/{id}/result, never recomputed client-side. */
export function PlacementReadinessResultPage() {
  const { attemptId = "" } = useParams<{ attemptId: string }>();
  const navigate = useNavigate();

  const { data, error, loading, refetch } = useAsync(() => getPlacementReadinessResult(attemptId), [attemptId]);

  if (loading) {
    return <LoadingState label="Loading your readiness result..." />;
  }

  if (error || !data) {
    return <ErrorState title="Result unavailable" message={error?.message ?? "This result isn't available."} onRetry={refetch} />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.placementReadiness}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Placement Readiness
      </Link>

      <Card>
        <CardContent className="space-y-3 py-8 text-center">
          <p className="text-xs font-semibold tracking-wide text-primary uppercase">Placement Readiness</p>
          <p className="text-sm font-medium text-muted-foreground">Target Role: {data.roleName}</p>
          <div className="relative mx-auto flex w-fit items-center justify-center py-2">
            <CircularScore value={data.scorePercentage} size={120} strokeWidth={10} />
            <div className="absolute flex flex-col items-center leading-none">
              <span className="text-3xl font-bold text-foreground">{data.scorePercentage}%</span>
            </div>
          </div>
          <h1 className="text-lg font-bold text-foreground">
            {data.status === "EXPIRED" ? "Time's Up" : "Overall Readiness"}
          </h1>
          <p className="text-sm text-muted-foreground">
            {data.correctCount} / {data.totalQuestions} correct
          </p>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="space-y-4 pt-4">
          <h2 className="text-sm font-semibold text-foreground">Skill Breakdown</h2>
          <div className="space-y-4">
            {data.skillBreakdown.map((skill) => (
              <div key={skill.skillId} className="space-y-1.5">
                <div className="flex items-center justify-between gap-2 text-sm">
                  <span className="min-w-0 truncate font-medium text-foreground">{skill.skillName}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className="text-muted-foreground">{skill.scorePercentage}%</span>
                    <span
                      className={cn(
                        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold",
                        skillReadinessStatusColorClasses(skill.status)
                      )}
                    >
                      {skillReadinessStatusLabel(skill.status)}
                    </span>
                  </span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className={cn("h-full rounded-full transition-all", skillReadinessStatusBarClasses(skill.status))}
                    style={{ width: `${skill.scorePercentage}%` }}
                  />
                </div>
                {!skill.meetsRequiredProficiency && skill.requiredProficiency ? (
                  <p className="text-xs text-muted-foreground">
                    Role target: {skill.requiredProficiency.charAt(0) + skill.requiredProficiency.slice(1).toLowerCase()}{" "}
                    proficiency — not yet met
                  </p>
                ) : null}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {data.priorityGaps.length > 0 ? (
        <Card>
          <CardContent className="space-y-3 pt-4">
            <h2 className="text-sm font-semibold text-foreground">Priority Skill Gaps</h2>
            <ol className="space-y-2">
              {data.priorityGaps.map((gap) => (
                <li key={gap.skillId} className="flex items-center gap-3 rounded-lg border border-border bg-muted/30 px-3 py-2.5">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-destructive/10 text-xs font-bold text-destructive">
                    {gap.rank}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">{gap.skillName}</p>
                    <p className="text-xs text-muted-foreground">{gap.scorePercentage}% score</p>
                  </div>
                  <span
                    className={cn(
                      "inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-semibold",
                      skillReadinessStatusColorClasses(gap.status)
                    )}
                  >
                    {skillReadinessStatusLabel(gap.status)}
                  </span>
                </li>
              ))}
            </ol>
            <div className="flex items-start gap-2 rounded-lg bg-muted/30 px-3 py-2.5 text-xs text-muted-foreground">
              <AlertTriangle className="mt-0.5 size-3.5 shrink-0" />
              Improving these skills first will move your readiness the most — ranked by how much your target role
              weighs each one, not just the lowest score.
            </div>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="py-6 text-center text-sm text-muted-foreground">
            No priority gaps — every required skill is at or above the Strong threshold for your target role.
          </CardContent>
        </Card>
      )}

      <Button className="w-full" size="lg" onClick={() => navigate(ROUTES.placementReadiness)}>
        Retake Assessment
      </Button>
    </div>
  );
}
