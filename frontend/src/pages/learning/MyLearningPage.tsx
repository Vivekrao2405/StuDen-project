import { AlertTriangle, Briefcase, ClipboardCheck, ListPlus, Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { getMyLearning } from "@/lib/api/endpoints/resources";
import type { EligibilityState, WeakAreaGroup } from "@/lib/api/resourceTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { ResourceCard } from "@/pages/learning/ResourceCard";

function EligibilityEmptyState({
  state,
  onGoToPortfolio,
}: {
  state: Exclude<EligibilityState, "HAS_AVAILABLE_ASSESSMENTS">;
  onGoToPortfolio: () => void;
}) {
  if (state === "NO_PORTFOLIO") {
    return (
      <EmptyState
        icon={Briefcase}
        title="Create your portfolio to unlock personalized learning"
        description="Your learning recommendations are based on the skills in your portfolio and your assessment performance."
        action={<Button onClick={onGoToPortfolio}>Create Portfolio</Button>}
      />
    );
  }
  return (
    <EmptyState
      icon={ListPlus}
      title="Add your skills to unlock personalized learning"
      description="Add skills to your portfolio and we'll recommend resources once you've taken an assessment."
      action={<Button onClick={onGoToPortfolio}>Update Portfolio</Button>}
    />
  );
}

function WeakAreaGroupSection({ group }: { group: WeakAreaGroup }) {
  const percent = group.totalCount === 0 ? 0 : Math.round((group.completedCount / group.totalCount) * 100);
  const severity = group.percentage < 40 ? "text-destructive" : "text-amber-600 dark:text-amber-400";

  return (
    <section className="space-y-4 rounded-xl border border-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1.5">
          <div className="flex items-center gap-2">
            <AlertTriangle className={cn("size-4 shrink-0", severity)} />
            <h2 className="text-base font-semibold text-foreground">{group.skillName}</h2>
            <span className={cn("text-sm font-medium", severity)}>{group.percentage}% — Needs Improvement</span>
          </div>
          {group.weakTags.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {group.weakTags.map((tag) => (
                <Badge key={tag} variant="outline">
                  {tag}
                </Badge>
              ))}
            </div>
          ) : null}
        </div>
        {group.totalCount > 0 ? (
          <div className="w-full shrink-0 space-y-1 sm:w-40">
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>
                {group.completedCount}/{group.totalCount} completed
              </span>
              <span>{percent}%</span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${percent}%` }} />
            </div>
          </div>
        ) : null}
      </div>

      {group.resources.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {group.resources.map((resource) => (
            <ResourceCard key={resource.id} resource={resource} />
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">No resources are published for this area yet — check back soon.</p>
      )}
    </section>
  );
}

export function MyLearningPage() {
  const navigate = useNavigate();
  const { data, error, loading, refetch } = useAsync(getMyLearning, []);

  if (loading) {
    return <LoadingState label="Loading your learning..." />;
  }
  if (error || !data) {
    return <ErrorState message={error?.message ?? "Couldn't load My Learning."} onRetry={refetch} />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">My Learning</h1>
        <p className="text-sm text-muted-foreground">
          Based on your recent assessment performance, we've identified areas that need improvement.
        </p>
      </div>

      {data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
        <EligibilityEmptyState state={data.state} onGoToPortfolio={() => navigate(ROUTES.profile)} />
      ) : data.groups.length > 0 ? (
        <div className="space-y-6">
          {data.groups.map((group) => (
            <WeakAreaGroupSection key={group.skillId} group={group} />
          ))}
        </div>
      ) : (
        <EmptyState
          icon={ClipboardCheck}
          title="No weak areas found yet"
          description="Take a skill assessment and we'll recommend resources for anything that needs improvement."
          action={
            <Button onClick={() => navigate(ROUTES.skillAssessments)}>
              <Sparkles className="size-4" /> Take an Assessment
            </Button>
          }
        />
      )}
    </div>
  );
}
