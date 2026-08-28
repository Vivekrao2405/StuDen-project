import { Briefcase, Check, Circle, ListPlus, PartyPopper, Sparkles, Timer } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { ScheduleSessionDialog } from "@/components/shared/ScheduleSessionDialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { getRoadmap } from "@/lib/api/endpoints/roadmap";
import type { EligibilityState, RoadmapItem } from "@/lib/api/resourceTypes";
import { topicLabel } from "@/lib/learningTags";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { resourceTypeIcon } from "@/pages/learning/resourceDisplay";

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
        title="Create your portfolio to unlock your roadmap"
        description="Your roadmap is built from the skills in your portfolio and your assessment performance."
        action={<Button onClick={onGoToPortfolio}>Create Portfolio</Button>}
      />
    );
  }
  return (
    <EmptyState
      icon={ListPlus}
      title="Add your skills to unlock your roadmap"
      description="Add skills to your portfolio and we'll build a roadmap once you've taken an assessment."
      action={<Button onClick={onGoToPortfolio}>Update Portfolio</Button>}
    />
  );
}

function statusIcon(item: RoadmapItem) {
  if (item.status === "COMPLETED") return <Check className="size-4 text-emerald-600 dark:text-emerald-400" />;
  if (item.status === "IN_PROGRESS") return <Timer className="size-4 text-primary" />;
  return <Circle className="size-4 text-muted-foreground" />;
}

function statusLabel(item: RoadmapItem): string {
  if (item.status === "COMPLETED") return "Completed";
  if (item.status === "IN_PROGRESS") return "In Progress";
  return "Not Started";
}

interface RoadmapItemRowProps {
  item: RoadmapItem;
  position: number;
  onSchedule: (item: RoadmapItem) => void;
}

function RoadmapItemRow({ item, position, onSchedule }: RoadmapItemRowProps) {
  const navigate = useNavigate();
  const ResourceIcon = item.resource ? resourceTypeIcon(item.resource.resourceType) : null;

  return (
    <div className="flex items-start gap-3 py-3">
      <span className="flex size-7 shrink-0 items-center justify-center rounded-full border border-border text-xs font-semibold text-muted-foreground">
        {position}
      </span>
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="text-sm font-semibold text-foreground">{topicLabel(item.topic)}</h3>
          <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            {statusIcon(item)} {statusLabel(item)}
          </span>
          <span className="text-xs text-muted-foreground">· {item.percentage}% on assessment</span>
        </div>
        {item.resource ? (
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            {ResourceIcon ? <ResourceIcon className="size-3.5" /> : null}
            <span className="truncate">{item.resource.title}</span>
          </div>
        ) : (
          <p className="text-xs text-muted-foreground">No matching resource published yet.</p>
        )}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {item.resource ? (
          <>
            <Button size="sm" variant="outline" onClick={() => onSchedule(item)}>
              Schedule
            </Button>
            <Button size="sm" onClick={() => navigate(ROUTES.myLearningResourceDetail(item.resource!.id))}>
              {item.status === "NOT_STARTED" ? "Start" : item.status === "COMPLETED" ? "Review" : "Continue"}
            </Button>
          </>
        ) : null}
      </div>
    </div>
  );
}

function priorityBadgeVariant(priority: RoadmapItem["priority"]): "default" | "secondary" | "outline" {
  if (priority === "HIGH") return "default";
  if (priority === "MEDIUM") return "secondary";
  return "outline";
}

function NextUpCard({ item, onSchedule }: { item: RoadmapItem; onSchedule: (item: RoadmapItem) => void }) {
  const navigate = useNavigate();
  return (
    <Card className="border-primary/30 bg-primary/5">
      <CardContent className="space-y-3 pt-1">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={priorityBadgeVariant(item.priority)}>Next Up</Badge>
          <span className="text-xs font-medium text-muted-foreground">{item.skillName}</span>
        </div>
        <h2 className="text-xl font-bold text-foreground">{topicLabel(item.topic)}</h2>
        <p className="text-sm text-muted-foreground">{item.reason}</p>
        {item.resource ? (
          <div className="flex flex-wrap gap-2 pt-1">
            <Button onClick={() => navigate(ROUTES.myLearningResourceDetail(item.resource!.id))}>
              <Sparkles className="size-4" /> Start Learning
            </Button>
            <Button variant="outline" onClick={() => onSchedule(item)}>
              Schedule
            </Button>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

export function RoadmapPage() {
  const navigate = useNavigate();
  const { data, error, loading, refetch } = useAsync(getRoadmap, []);
  const [scheduleTarget, setScheduleTarget] = useState<RoadmapItem | null>(null);

  if (loading) {
    return <LoadingState label="Building your roadmap..." />;
  }
  if (error || !data) {
    return <ErrorState message={error?.message ?? "Couldn't load your roadmap."} onRetry={refetch} />;
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Learning Roadmap</h1>
        <p className="text-sm text-muted-foreground">Your personalized, ordered path through your weak areas.</p>
      </div>

      {data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
        <EligibilityEmptyState state={data.state} onGoToPortfolio={() => navigate(ROUTES.profile)} />
      ) : data.groups.length === 0 ? (
        <EmptyState
          icon={Sparkles}
          title="No weak areas found yet"
          description="Take a skill assessment and we'll build a personalized roadmap from anything that needs improvement."
          action={
            <Button onClick={() => navigate(ROUTES.skillAssessments)}>
              <Sparkles className="size-4" /> Take an Assessment
            </Button>
          }
        />
      ) : (
        <>
          {data.nextUp ? (
            <NextUpCard item={data.nextUp} onSchedule={setScheduleTarget} />
          ) : data.allCaughtUp ? (
            <Card className="border-emerald-500/30 bg-emerald-500/5">
              <CardContent className="flex items-center gap-3 pt-1">
                <PartyPopper className="size-6 text-emerald-600 dark:text-emerald-400" />
                <div>
                  <h2 className="text-base font-semibold text-foreground">Roadmap complete!</h2>
                  <p className="text-sm text-muted-foreground">
                    You've completed every resource matched to your identified weak areas. Take a new assessment to
                    keep growing.
                  </p>
                </div>
              </CardContent>
            </Card>
          ) : null}

          <div
            className={cn(
              "grid gap-3",
              data.overview.topicsTotal > 0 ? "grid-cols-1 sm:grid-cols-3" : "hidden"
            )}
          >
            <Card size="sm">
              <CardContent className="pt-1">
                <p className="text-xs text-muted-foreground">Roadmap Progress</p>
                <p className="text-lg font-bold text-foreground">{data.overview.percentage}%</p>
              </CardContent>
            </Card>
            <Card size="sm">
              <CardContent className="pt-1">
                <p className="text-xs text-muted-foreground">Topics Completed</p>
                <p className="text-lg font-bold text-foreground">
                  {data.overview.topicsCompleted} / {data.overview.topicsTotal}
                </p>
              </CardContent>
            </Card>
            <Card size="sm">
              <CardContent className="pt-1">
                <p className="text-xs text-muted-foreground">Weak Skills</p>
                <p className="text-lg font-bold text-foreground">{data.groups.length}</p>
              </CardContent>
            </Card>
          </div>

          {data.groups.map((group) => (
            <section key={group.skillId} className="space-y-1">
              <h2 className="text-lg font-semibold text-foreground">{group.skillName} Roadmap</h2>
              <Card>
                <CardContent className="divide-y divide-border">
                  {group.items.map((item, index) => (
                    <RoadmapItemRow
                      key={`${group.skillId}-${item.topic}`}
                      item={item}
                      position={index + 1}
                      onSchedule={setScheduleTarget}
                    />
                  ))}
                </CardContent>
              </Card>
            </section>
          ))}
        </>
      )}

      {scheduleTarget ? (
        <ScheduleSessionDialog
          open
          onOpenChange={(open) => !open && setScheduleTarget(null)}
          resourceId={scheduleTarget.resource?.id ?? null}
          resourceTitle={scheduleTarget.resource?.title ?? topicLabel(scheduleTarget.topic)}
          topic={scheduleTarget.topic}
          defaultDurationMinutes={scheduleTarget.resource?.estimatedMinutes ?? 60}
          onScheduled={() => setScheduleTarget(null)}
        />
      ) : null}
    </div>
  );
}
