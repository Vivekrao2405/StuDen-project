import { Clock } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { ResourceCard as ResourceCardData } from "@/lib/api/resourceTypes";
import { ROUTES } from "@/lib/routes";
import { difficultyBadgeVariant } from "@/pages/practical/practicalDisplay";
import {
  progressStatusBadgeVariant,
  progressStatusLabel,
  RESOURCE_TYPE_LABEL,
  resourceTypeIcon,
} from "@/pages/learning/resourceDisplay";

export function ResourceCard({ resource }: { resource: ResourceCardData }) {
  const navigate = useNavigate();
  const Icon = resourceTypeIcon(resource.resourceType);

  return (
    <Card className="flex flex-col">
      <CardContent className="flex flex-1 flex-col gap-3 pt-4">
        <div className="flex items-start gap-2.5">
          <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent">
            <Icon className="size-4 text-accent-foreground" />
          </span>
          <div className="min-w-0">
            <h3 className="truncate text-base font-semibold text-foreground">{resource.title}</h3>
            <p className="truncate text-xs text-muted-foreground">{resource.skillName}</p>
          </div>
        </div>

        {resource.description ? (
          <p className="line-clamp-2 text-sm text-muted-foreground">{resource.description}</p>
        ) : null}

        <div className="flex flex-wrap items-center gap-1.5">
          <Badge variant="outline">{RESOURCE_TYPE_LABEL[resource.resourceType]}</Badge>
          {resource.difficulty ? <Badge variant={difficultyBadgeVariant(resource.difficulty)}>{resource.difficulty}</Badge> : null}
          {resource.progressStatus !== "NOT_STARTED" ? (
            <Badge variant={progressStatusBadgeVariant(resource.progressStatus)}>
              {progressStatusLabel(resource.progressStatus)}
            </Badge>
          ) : null}
        </div>

        <div className="mt-auto flex items-center justify-between gap-3 pt-2">
          <span className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground">
            {resource.estimatedMinutes ? (
              <>
                <Clock className="size-3.5" /> {resource.estimatedMinutes} min
              </>
            ) : null}
          </span>
          <Button size="sm" onClick={() => navigate(ROUTES.myLearningResourceDetail(resource.id))}>
            {resource.progressStatus === "NOT_STARTED" ? "View" : "Continue"}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
