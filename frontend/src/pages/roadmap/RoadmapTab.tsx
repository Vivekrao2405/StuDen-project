import { Briefcase, Calendar, Check, Circle, Flame, ListPlus, PartyPopper, Sparkles, Target, Timer } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { ScheduleSessionDialog } from "@/components/shared/ScheduleSessionDialog";
import { StudyPlanDialog } from "@/pages/calendar/StudyPlanDialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { getRoadmap } from "@/lib/api/endpoints/roadmap";
import type { EligibilityState, RoadmapItem, RoadmapSkillGroup } from "@/lib/api/resourceTypes";
import { topicLabel } from "@/lib/learningTags";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { ProgressRing } from "@/pages/learning/ProgressRing";
import { resourceTypeIcon } from "@/pages/learning/resourceDisplay";

const LEARNING_SELECT_CLASS =
  "h-9 min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30";

type RoadmapView = "roadmap" | "skills";
const VIEW_OPTIONS: { value: RoadmapView; label: string }[] = [
  { value: "roadmap", label: "Roadmap" },
  { value: "skills", label: "Skills" },
];

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

function groupStatus(group: RoadmapSkillGroup): "COMPLETED" | "IN_PROGRESS" | "NOT_STARTED" {
  if (group.items.every((i) => i.status === "COMPLETED")) return "COMPLETED";
  if (group.items.some((i) => i.status === "COMPLETED" || i.status === "IN_PROGRESS")) return "IN_PROGRESS";
  return "NOT_STARTED";
}

function priorityBadgeVariant(priority: RoadmapItem["priority"]): "default" | "secondary" | "outline" {
  if (priority === "HIGH") return "default";
  if (priority === "MEDIUM") return "secondary";
  return "outline";
}

interface RoadmapItemRowProps {
  item: RoadmapItem;
  onSchedule: (item: RoadmapItem) => void;
}

function RoadmapItemRow({ item, onSchedule }: RoadmapItemRowProps) {
  const navigate = useNavigate();
  const ResourceIcon = item.resource ? resourceTypeIcon(item.resource.resourceType) : null;

  return (
    <div className="flex items-start gap-3 py-2.5">
      <span className="mt-0.5 shrink-0">{statusIcon(item)}</span>
      <div className="min-w-0 flex-1 space-y-0.5">
        <div className="flex flex-wrap items-center gap-2">
          <h4 className={cn("text-sm font-medium", item.status === "COMPLETED" ? "text-muted-foreground line-through" : "text-foreground")}>
            {topicLabel(item.topic)}
          </h4>
          <span className="text-xs text-muted-foreground">{statusLabel(item)}</span>
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
      {item.resource ? (
        <div className="flex shrink-0 items-center gap-1.5">
          <Button size="sm" variant="outline" onClick={() => onSchedule(item)}>
            Schedule
          </Button>
          <Button size="sm" onClick={() => navigate(ROUTES.myLearningResourceDetail(item.resource!.id))}>
            {item.status === "NOT_STARTED" ? "Start" : item.status === "COMPLETED" ? "Review" : "Continue"}
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function StageCard({
  group,
  position,
  isLast,
  onSchedule,
}: {
  group: RoadmapSkillGroup;
  position: number;
  isLast: boolean;
  onSchedule: (item: RoadmapItem) => void;
}) {
  const completed = group.items.filter((i) => i.status === "COMPLETED").length;
  const status = groupStatus(group);

  return (
    <div className="relative flex gap-3">
      <div className="flex flex-col items-center">
        <span
          className={cn(
            "z-10 flex size-7 shrink-0 items-center justify-center rounded-full border-2 text-xs font-semibold",
            status === "COMPLETED" && "border-emerald-500 bg-emerald-500 text-white",
            status === "IN_PROGRESS" && "border-primary bg-primary text-primary-foreground",
            status === "NOT_STARTED" && "border-border bg-background text-muted-foreground"
          )}
        >
          {status === "COMPLETED" ? <Check className="size-4" /> : position}
        </span>
        {isLast ? null : <span className="w-px flex-1 bg-border" />}
      </div>
      <Card className="mb-4 flex-1">
        <CardContent className="space-y-1 pt-1">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <h3 className="text-sm font-semibold text-foreground">{group.skillName}</h3>
              {status === "IN_PROGRESS" ? <Badge variant="secondary">In Progress</Badge> : null}
              {status === "COMPLETED" ? <Badge>Completed</Badge> : null}
            </div>
            <span className="text-xs font-medium text-muted-foreground">
              {completed} / {group.items.length} Completed
            </span>
          </div>
          <div className="divide-y divide-border">
            {group.items.map((item) => (
              <RoadmapItemRow key={`${group.skillId}-${item.topic}`} item={item} onSchedule={onSchedule} />
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function SkillsList({ groups }: { groups: RoadmapSkillGroup[] }) {
  const rows = groups
    .map((group) => {
      const completed = group.items.filter((i) => i.status === "COMPLETED").length;
      const total = group.items.length;
      const percentage = total === 0 ? 0 : Math.round((completed / total) * 100);
      return { skillId: group.skillId, skillName: group.skillName, completed, total, percentage };
    })
    .sort((a, b) => a.percentage - b.percentage);

  return (
    <Card>
      <CardContent className="divide-y divide-border">
        {rows.map((row) => (
          <div key={row.skillId} className="space-y-1.5 py-3">
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium text-foreground">{row.skillName}</span>
              <span className="text-xs text-muted-foreground">
                {row.completed} / {row.total} · {row.percentage}%
              </span>
            </div>
            <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${row.percentage}%` }} />
            </div>
          </div>
        ))}
      </CardContent>
    </Card>
  );
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

function OverviewStatsCard({
  percentage,
  completed,
  total,
  streakDays,
}: {
  percentage: number;
  completed: number;
  total: number;
  streakDays: number;
}) {
  const remaining = Math.max(total - completed, 0);
  return (
    <Card>
      <CardContent className="grid grid-cols-2 gap-4 pt-1 sm:grid-cols-4 sm:divide-x sm:divide-border">
        <div className="flex items-center gap-3">
          <ProgressRing percent={percentage} size={56} stroke={6} />
          <div>
            <p className="text-xs text-muted-foreground">Overall Progress</p>
          </div>
        </div>
        <div className="flex items-center gap-2.5 sm:pl-4">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600 dark:text-emerald-400">
            <Check className="size-4" />
          </span>
          <div>
            <p className="text-lg font-bold leading-tight text-foreground">
              {completed} / {total}
            </p>
            <p className="text-xs text-muted-foreground">Topics Completed</p>
          </div>
        </div>
        <div className="flex items-center gap-2.5 sm:pl-4">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-amber-500/10 text-amber-600 dark:text-amber-400">
            <Flame className="size-4" />
          </span>
          <div>
            <p className="text-lg font-bold leading-tight text-foreground">{streakDays} days</p>
            <p className="text-xs text-muted-foreground">Current Streak</p>
          </div>
        </div>
        <div className="flex items-center gap-2.5 sm:pl-4">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <Target className="size-4" />
          </span>
          <div>
            <p className="text-lg font-bold leading-tight text-foreground">{remaining}</p>
            <p className="text-xs text-muted-foreground">Topics Remaining</p>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function SmartPlannerCard({ onEnable }: { onEnable: () => void }) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 pt-1 text-center sm:flex-row sm:items-center sm:justify-between sm:text-left">
        <div className="flex items-center gap-3">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <Calendar className="size-5" />
          </span>
          <div>
            <p className="text-sm font-semibold text-foreground">Need help staying on track?</p>
            <p className="text-sm text-muted-foreground">Get personalized study reminders and smart recommendations.</p>
          </div>
        </div>
        <Button onClick={onEnable}>
          <Sparkles className="size-4" /> Enable Planner
        </Button>
      </CardContent>
    </Card>
  );
}

export function RoadmapTab() {
  const navigate = useNavigate();
  const { data, error, loading, refetch } = useAsync(getRoadmap, []);
  const [scheduleTarget, setScheduleTarget] = useState<RoadmapItem | null>(null);
  const [view, setView] = useState<RoadmapView>("roadmap");
  const [selectedSkillId, setSelectedSkillId] = useState<string>("all");
  const [studyPlanOpen, setStudyPlanOpen] = useState(false);

  if (loading) {
    return <LoadingState label="Building your roadmap..." />;
  }
  if (error || !data) {
    return <ErrorState message={error?.message ?? "Couldn't load your roadmap."} onRetry={refetch} />;
  }

  if (data.state !== "HAS_AVAILABLE_ASSESSMENTS") {
    return <EligibilityEmptyState state={data.state} onGoToPortfolio={() => navigate(ROUTES.profile)} />;
  }

  if (data.groups.length === 0) {
    return (
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
    );
  }

  const visibleGroups = selectedSkillId === "all" ? data.groups : data.groups.filter((g) => g.skillId === selectedSkillId);

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-foreground">My Learning Roadmap</h2>
        <p className="text-sm text-muted-foreground">Your personalized path to mastering each skill.</p>
      </div>

      {data.nextUp ? (
        <NextUpCard item={data.nextUp} onSchedule={setScheduleTarget} />
      ) : data.allCaughtUp ? (
        <Card className="border-emerald-500/30 bg-emerald-500/5">
          <CardContent className="flex items-center gap-3 pt-1">
            <PartyPopper className="size-6 text-emerald-600 dark:text-emerald-400" />
            <div>
              <h2 className="text-base font-semibold text-foreground">Roadmap complete!</h2>
              <p className="text-sm text-muted-foreground">
                You've completed every resource matched to your identified weak areas. Take a new assessment to keep growing.
              </p>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <OverviewStatsCard
        percentage={data.overview.percentage}
        completed={data.overview.topicsCompleted}
        total={data.overview.topicsTotal}
        streakDays={data.overview.currentStreakDays}
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <SegmentedControl value={view} onChange={setView} options={VIEW_OPTIONS} />
        {data.groups.length > 1 ? (
          <select
            value={selectedSkillId}
            onChange={(e) => setSelectedSkillId(e.target.value)}
            aria-label="Filter by skill"
            className={LEARNING_SELECT_CLASS}
          >
            <option value="all">All</option>
            {data.groups.map((group) => (
              <option key={group.skillId} value={group.skillId}>
                {group.skillName}
              </option>
            ))}
          </select>
        ) : null}
      </div>

      {view === "roadmap" ? (
        <div>
          {visibleGroups.map((group, index) => (
            <StageCard
              key={group.skillId}
              group={group}
              position={index + 1}
              isLast={index === visibleGroups.length - 1}
              onSchedule={setScheduleTarget}
            />
          ))}
        </div>
      ) : (
        <SkillsList groups={visibleGroups} />
      )}

      <SmartPlannerCard onEnable={() => setStudyPlanOpen(true)} />

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

      <StudyPlanDialog open={studyPlanOpen} onOpenChange={setStudyPlanOpen} onSaved={() => setStudyPlanOpen(false)} />
    </div>
  );
}
