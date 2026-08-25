import { ClipboardList, List, RotateCw } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import type { FocusAreaTopic } from "@/lib/api/resourceTypes";
import { topicLabel } from "@/lib/learningTags";
import { cn } from "@/lib/utils";

// Each Focus Area card gets its own accent (icon + percentage + bar all one color), cycling through
// StuDen's existing accent palette (already used for icon chips elsewhere, e.g. ContinueYourJourney)
// rather than a single severity-based color — matches the reference's per-card accent treatment.
const ACCENTS: { icon: LucideIcon; iconBg: string; iconColor: string; text: string; bar: string }[] = [
  { icon: List, iconBg: "bg-amber-500/10", iconColor: "text-amber-600 dark:text-amber-400", text: "text-amber-600 dark:text-amber-400", bar: "bg-amber-500" },
  { icon: RotateCw, iconBg: "bg-primary/10", iconColor: "text-primary", text: "text-primary", bar: "bg-primary" },
  { icon: ClipboardList, iconBg: "bg-emerald-500/10", iconColor: "text-emerald-600 dark:text-emerald-400", text: "text-emerald-600 dark:text-emerald-400", bar: "bg-emerald-500" },
];

export function FocusAreaCard({ topic, index = 0 }: { topic: FocusAreaTopic; index?: number }) {
  const progressPercent = topic.totalCount === 0 ? 0 : Math.round((topic.completedCount / topic.totalCount) * 100);
  const accent = ACCENTS[index % ACCENTS.length];

  return (
    <Card size="sm">
      <CardContent className="space-y-3 pt-1">
        <div className="flex items-center justify-between gap-2">
          <div className="flex min-w-0 items-center gap-2">
            <span className={cn("flex size-8 shrink-0 items-center justify-center rounded-lg", accent.iconBg)}>
              <accent.icon className={cn("size-4", accent.iconColor)} />
            </span>
            <h3 className="truncate text-sm font-semibold text-foreground">{topicLabel(topic.topic)}</h3>
          </div>
          <span className={cn("shrink-0 text-sm font-semibold", accent.text)}>{topic.percentage}%</span>
        </div>

        <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
          <div className={cn("h-full rounded-full transition-all", accent.bar)} style={{ width: `${progressPercent}%` }} />
        </div>

        <p className="text-xs text-muted-foreground">
          {topic.completedCount} / {topic.totalCount} resources completed
        </p>
      </CardContent>
    </Card>
  );
}
