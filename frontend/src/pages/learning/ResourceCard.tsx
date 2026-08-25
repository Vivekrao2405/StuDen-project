import { Clock, Play } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { Difficulty } from "@/lib/api/types";
import type { ResourceCard as ResourceCardData } from "@/lib/api/resourceTypes";
import { primaryTopicForResource, topicLabel } from "@/lib/learningTags";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { resourceTypeIcon } from "@/pages/learning/resourceDisplay";

const DIFFICULTY_LABEL: Record<Difficulty, string> = { EASY: "Easy", MEDIUM: "Medium", HARD: "Hard" };
const DIFFICULTY_DOT: Record<Difficulty, string> = {
  EASY: "bg-emerald-500",
  MEDIUM: "bg-amber-500",
  HARD: "bg-destructive",
};

// Resource-level progress is intentionally never a fabricated fraction — this app only ever tracks
// NOT_STARTED/IN_PROGRESS/COMPLETED for a single resource (no page/scroll-position tracking exists).
// NOT_STARTED and COMPLETED show their real, true value (0%/100%); IN_PROGRESS shows the bar at the
// conventional "partway there, exact amount unknown" midpoint without a numeric label, rather than
// inventing a specific percentage the app has no way to know.
function progressFill(status: ResourceCardData["progressStatus"]): number {
  if (status === "COMPLETED") return 100;
  if (status === "IN_PROGRESS") return 50;
  return 0;
}

export function ResourceCard({ resource, weakTopics }: { resource: ResourceCardData; weakTopics?: Set<string> }) {
  const navigate = useNavigate();
  const Icon = resourceTypeIcon(resource.resourceType);
  const topic = primaryTopicForResource(resource.tags, weakTopics ?? new Set());
  const fill = progressFill(resource.progressStatus);

  return (
    <Card className="flex h-full flex-col">
      <CardContent className="flex flex-1 flex-col gap-3 pt-1">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
          <Icon className="size-5 text-primary" />
        </span>

        <div className="min-w-0">
          <h3 className="line-clamp-2 text-sm leading-snug font-semibold text-foreground">{resource.title}</h3>
          {topic ? (
            <span className="mt-1.5 inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
              {topicLabel(topic)}
            </span>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          {resource.difficulty ? (
            <span className="inline-flex items-center gap-1.5">
              <span className={cn("size-1.5 rounded-full", DIFFICULTY_DOT[resource.difficulty])} />
              {DIFFICULTY_LABEL[resource.difficulty]}
            </span>
          ) : null}
          {resource.estimatedMinutes ? (
            <span className="inline-flex items-center gap-1">
              <Clock className="size-3.5" /> {resource.estimatedMinutes} min
            </span>
          ) : null}
        </div>

        <div className="mt-auto space-y-2 pt-1">
          <div className="flex items-center gap-2">
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${fill}%` }} />
            </div>
            <span className="shrink-0 text-xs font-medium text-muted-foreground">
              {resource.progressStatus === "IN_PROGRESS" ? "In Progress" : `${fill}%`}
            </span>
          </div>
          <Button size="sm" className="w-full" onClick={() => navigate(ROUTES.myLearningResourceDetail(resource.id))}>
            <Play className="size-3.5" />
            {resource.progressStatus === "NOT_STARTED"
              ? "Start Learning"
              : resource.progressStatus === "COMPLETED"
                ? "Review Again"
                : "Continue"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
