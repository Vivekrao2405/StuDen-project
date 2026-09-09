import { ClipboardCheck, Target, ThumbsUp } from "lucide-react";
import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { getPlacementLearningPlan } from "@/lib/api/endpoints/placement";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import {
  skillReadinessStatusBarClasses,
  skillReadinessStatusColorClasses,
  skillReadinessStatusLabel,
} from "@/pages/placement/placementReadinessDisplay";
import { ResourceCard } from "@/pages/learning/ResourceCard";

// Same DOM-id convention the Placement Readiness result page's "Improve this skill" link targets
// (see ROUTES usage there) -- lets that link both navigate to My Learning and scroll straight to
// the relevant priority-skill card, without a second dedicated route.
function skillAnchorId(skillId: string) {
  return `placement-skill-${skillId}`;
}

/** "Your Placement Learning Path" -- Phase 4's entry point for Placement recommendations inside
 * the existing My Learning page (never a second learning dashboard). Every number/resource here is
 * read straight off GET /placement/learning-plan, itself a direct projection of the student's own
 * latest Phase 3 readiness result -- nothing is fabricated or re-derived client-side. Renders
 * nothing (fails soft) if the plan can't load, so a Placement outage never blocks the rest of My
 * Learning from rendering. */
export function PlacementLearningSection() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const focusSkillId = searchParams.get("focusSkill");
  const { data, error, loading } = useAsync(getPlacementLearningPlan, []);

  useEffect(() => {
    if (!focusSkillId || !data) return;
    const el = document.getElementById(skillAnchorId(focusSkillId));
    el?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [focusSkillId, data]);

  if (loading) {
    return <Skeleton className="h-40 w-full rounded-xl" aria-hidden="true" />;
  }
  if (error || !data) {
    return null;
  }

  return (
    <section className="space-y-3">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Your Placement Learning Path</h2>
        <p className="text-sm text-muted-foreground">
          {data.targetRoleName ? `Target Role: ${data.targetRoleName}` : "Personalized recommendations from your Placement Readiness results."}
        </p>
      </div>

      {data.state === "NO_READINESS_ASSESSMENT" ? (
        <EmptyState
          icon={ClipboardCheck}
          title="Complete your Placement Readiness assessment to get personalized learning recommendations."
          action={<Button onClick={() => navigate(ROUTES.placementReadiness)}>Check Readiness</Button>}
        />
      ) : null}

      {data.state === "NO_SKILL_GAPS" ? (
        <EmptyState
          icon={ThumbsUp}
          title="You're performing strongly across your current role requirements."
        />
      ) : null}

      {data.state === "GAPS_WITHOUT_RESOURCES" || data.state === "HAS_RECOMMENDATIONS" ? (
        <>
          <div className="flex flex-wrap gap-2">
            {data.prioritySkills.map((skill) => (
              <span
                key={skill.skillId}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold",
                  skillReadinessStatusColorClasses(skill.status)
                )}
              >
                <Target className="size-3" />
                {skill.skillName} · {skillReadinessStatusLabel(skill.status)} · {skill.scorePercentage}%
              </span>
            ))}
          </div>

          {data.state === "GAPS_WITHOUT_RESOURCES" ? (
            <EmptyState
              title="You've identified areas to improve, but learning resources haven't been configured for these skills yet."
            />
          ) : (
            <div className="space-y-4">
              {data.prioritySkills
                .filter((skill) => skill.resources.length > 0)
                .map((skill) => (
                  <Card key={skill.skillId} id={skillAnchorId(skill.skillId)}>
                    <CardContent className="space-y-3 pt-4">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div>
                          <p className="text-xs font-medium text-muted-foreground">Priority {skill.rank}</p>
                          <p className="text-sm font-semibold text-foreground">{skill.skillName}</p>
                        </div>
                        <div className="flex items-center gap-2">
                          <div className="h-1.5 w-16 overflow-hidden rounded-full bg-muted">
                            <div
                              className={cn("h-full rounded-full", skillReadinessStatusBarClasses(skill.status))}
                              style={{ width: `${skill.scorePercentage}%` }}
                            />
                          </div>
                          <span
                            className={cn(
                              "inline-flex shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-semibold",
                              skillReadinessStatusColorClasses(skill.status)
                            )}
                          >
                            {skillReadinessStatusLabel(skill.status)}
                          </span>
                        </div>
                      </div>
                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                        {skill.resources.map((resource) => (
                          <ResourceCard key={resource.id} resource={resource} />
                        ))}
                      </div>
                    </CardContent>
                  </Card>
                ))}
            </div>
          )}
        </>
      ) : null}
    </section>
  );
}
