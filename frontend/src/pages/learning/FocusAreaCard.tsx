import { List } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import type { FocusAreaTopic } from "@/lib/api/resourceTypes";
import { topicLabel } from "@/lib/learningTags";
import { cn } from "@/lib/utils";

// Severity purely drives the percentage's color accent — the bar/card itself stays neutral
// (StuDen's existing palette, no new colors), matching the reference's restrained use of color.
function severityClass(percentage: number): string {
  if (percentage < 40) return "text-destructive";
  if (percentage < 70) return "text-amber-600 dark:text-amber-400";
  return "text-emerald-600 dark:text-emerald-400";
}

export function FocusAreaCard({ topic }: { topic: FocusAreaTopic }) {
  const progressPercent = topic.totalCount === 0 ? 0 : Math.round((topic.completedCount / topic.totalCount) * 100);

  return (
    <Card size="sm">
      <CardContent className="space-y-3 pt-1">
        <div className="flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent">
              <List className="size-4 text-accent-foreground" />
            </span>
            <h3 className="truncate text-sm font-semibold text-foreground">{topicLabel(topic.topic)}</h3>
          </div>
          <span className={cn("shrink-0 text-sm font-semibold", severityClass(topic.percentage))}>
            {topic.percentage}%
          </span>
        </div>

        <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
          <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${progressPercent}%` }} />
        </div>

        <p className="text-xs text-muted-foreground">
          {topic.completedCount} / {topic.totalCount} resources completed
        </p>
      </CardContent>
    </Card>
  );
}
