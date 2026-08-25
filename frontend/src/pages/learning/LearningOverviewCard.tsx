import { AlertTriangle, ClipboardCheck, Library } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import type { LearningOverview } from "@/lib/api/resourceTypes";
import { cn } from "@/lib/utils";
import { ProgressRing } from "@/pages/learning/ProgressRing";

function StatBlock({ icon: Icon, iconClassName, value, label }: { icon: LucideIcon; iconClassName: string; value: number; label: string }) {
  return (
    <div className="flex items-center gap-2.5">
      <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg", iconClassName)}>
        <Icon className="size-4" />
      </span>
      <div>
        <p className="text-lg font-bold leading-tight text-foreground">{value}</p>
        <p className="text-xs text-muted-foreground">{label}</p>
      </div>
    </div>
  );
}

export function LearningOverviewCard({ overview }: { overview: LearningOverview }) {
  const percent = overview.totalResourceCount === 0
    ? 0
    : Math.round((overview.completedResourceCount / overview.totalResourceCount) * 100);

  return (
    <Card>
      <CardContent className="flex flex-col gap-6 pt-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex items-center gap-5">
          <ProgressRing percent={percent} />
          <div className="space-y-1">
            <p className="text-sm font-semibold text-foreground">Overall Progress</p>
            <p className="text-sm text-muted-foreground">
              {overview.completedResourceCount} / {overview.totalResourceCount} resources completed
            </p>
            {overview.totalResourceCount > 0 ? (
              <div className="h-1.5 w-40 overflow-hidden rounded-full bg-muted">
                <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${percent}%` }} />
              </div>
            ) : null}
          </div>
        </div>

        <div className="grid grid-cols-3 gap-4 sm:gap-6">
          <StatBlock icon={AlertTriangle} iconClassName="bg-amber-500/10 text-amber-600 dark:text-amber-400" value={overview.weakSkillsCount} label="Weak Skills" />
          <StatBlock icon={Library} iconClassName="bg-accent text-accent-foreground" value={overview.resourcesCount} label="Resources" />
          <StatBlock icon={ClipboardCheck} iconClassName="bg-emerald-500/10 text-emerald-600 dark:text-emerald-400" value={overview.assessmentsCompletedCount} label="Assessments" />
        </div>
      </CardContent>
    </Card>
  );
}
