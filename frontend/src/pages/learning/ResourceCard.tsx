import { Check, Clock } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { ResourceCard as ResourceCardData } from "@/lib/api/resourceTypes";
import { primaryTopicForResource, topicLabel } from "@/lib/learningTags";
import { ROUTES } from "@/lib/routes";
import { difficultyBadgeVariant } from "@/pages/practical/practicalDisplay";
import {
  progressStatusBadgeVariant,
  progressStatusLabel,
  RESOURCE_TYPE_LABEL,
  resourceTypeIcon,
} from "@/pages/learning/resourceDisplay";

export function ResourceCard({ resource, weakTopics }: { resource: ResourceCardData; weakTopics?: Set<string> }) {
  const navigate = useNavigate();
  const Icon = resourceTypeIcon(resource.resourceType);
  const topic = primaryTopicForResource(resource.tags, weakTopics ?? new Set());
  const completed = resource.progressStatus === "COMPLETED";

  return (
    <Card className="flex flex-col">
      <CardContent className="flex flex-1 flex-col gap-3 pt-1">
        <div className="flex items-start gap-2.5">
          <span className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
            <Icon className="size-4 text-primary" />
          </span>
          <div className="min-w-0 flex-1">
            <h3 className="line-clamp-2 text-sm font-semibold text-foreground">{resource.title}</h3>
            {topic ? <p className="truncate text-xs font-medium text-primary">{topicLabel(topic)}</p> : null}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variant="outline">{RESOURCE_TYPE_LABEL[resource.resourceType]}</Badge>
          {resource.difficulty ? <Badge variant={difficultyBadgeVariant(resource.difficulty)}>{resource.difficulty}</Badge> : null}
          {resource.estimatedMinutes ? (
            <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
              <Clock className="size-3.5" /> {resource.estimatedMinutes} min
            </span>
          ) : null}
        </div>

        <div className="mt-auto space-y-2 pt-1">
          {resource.progressStatus !== "NOT_STARTED" ? (
            <Badge variant={progressStatusBadgeVariant(resource.progressStatus)} className="gap-1">
              {completed ? <Check className="size-3" /> : null}
              {progressStatusLabel(resource.progressStatus)}
            </Badge>
          ) : null}
          <Button size="sm" className="w-full" onClick={() => navigate(ROUTES.myLearningResourceDetail(resource.id))}>
            {resource.progressStatus === "NOT_STARTED" ? "Start Learning" : completed ? "Review Again" : "Continue"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
