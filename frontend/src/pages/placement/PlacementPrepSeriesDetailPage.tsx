import { ArrowLeft, ArrowRight, BookOpen, Briefcase, Building2, Check, Circle, Clock, Code2, FileQuestion, Loader2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  getPlacementPrepContinuePointer,
  getPlacementPrepSeries,
  startPlacementPrepSeries,
} from "@/lib/api/endpoints/placement";
import type { ModuleItemType, StudentPlacementModuleResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { skillReadinessStatusColorClasses, skillReadinessStatusLabel } from "@/pages/placement/placementReadinessDisplay";
import {
  MODULE_ITEM_TYPE_LABEL,
  PREPARATION_TYPE_LABEL,
  PROGRESS_STATUS_BADGE_CLASSES,
  PROGRESS_STATUS_BAR_CLASSES,
  PROGRESS_STATUS_LABEL,
} from "@/pages/placement/placementPrepDisplay";

const ITEM_ICON: Record<ModuleItemType, typeof FileQuestion> = {
  QUESTION: FileQuestion,
  PRACTICAL_ASSESSMENT: Code2,
  RESOURCE: BookOpen,
};

export function PlacementPrepSeriesDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const detail = useAsync(() => getPlacementPrepSeries(id), [id]);
  const [starting, setStarting] = useState(false);

  const series = detail.data;

  async function handleContinue() {
    if (!series) return;
    setStarting(true);
    try {
      if (series.progressStatus === "NOT_STARTED") {
        await startPlacementPrepSeries(series.id);
      }
      const pointer = await getPlacementPrepContinuePointer(series.id);
      if (pointer.allComplete || !pointer.itemId) {
        toast.success("You've completed every module in this series.");
        detail.refetch();
        return;
      }
      navigate(ROUTES.placementPrepModuleItem(pointer.itemId));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't continue. Please try again.");
    } finally {
      setStarting(false);
    }
  }

  if (detail.loading) {
    return <LoadingState label="Loading series..." />;
  }
  if (detail.error || !series) {
    return (
      <ErrorState
        title="Series unavailable"
        message={detail.error?.message ?? "This preparation series isn't available."}
        onRetry={detail.refetch}
      />
    );
  }

  const started = series.progressStatus !== "NOT_STARTED";
  const complete = series.progressStatus === "COMPLETED";

  return (
    <div className="space-y-6">
      <Link
        to={ROUTES.placementPrep}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Placement Prep
      </Link>

      <div className="rounded-2xl border border-border bg-card p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-bold text-foreground">{series.name}</h1>
            {series.description ? <p className="mt-1 text-sm text-muted-foreground">{series.description}</p> : null}
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm text-muted-foreground">
              <span className="inline-flex items-center gap-1.5">
                <Briefcase className="size-4" /> {series.targetRoleName}
              </span>
              {series.companyName ? (
                <span className="inline-flex items-center gap-1.5">
                  <Building2 className="size-4" /> {series.companyName}
                </span>
              ) : null}
              <span className="capitalize">{series.difficulty.toLowerCase()}</span>
              {series.estimatedDurationHours ? (
                <span className="inline-flex items-center gap-1.5">
                  <Clock className="size-4" /> {series.estimatedDurationHours}h
                </span>
              ) : null}
              {series.preparationType ? <span>{PREPARATION_TYPE_LABEL[series.preparationType]}</span> : null}
            </div>
          </div>
          <Button onClick={handleContinue} disabled={starting || complete}>
            {starting ? <Loader2 className="size-4 animate-spin" /> : null}
            {complete ? "All Modules Completed" : started ? "Continue Preparation" : "Start Preparation"}
            {!complete ? <ArrowRight className="size-4" /> : null}
          </Button>
        </div>

        {series.skillsCovered.length > 0 ? (
          <div className="mt-5 flex flex-wrap gap-2">
            {series.skillsCovered.map((skill) => (
              <span
                key={skill.skillId}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium",
                  skill.readinessStatus ? skillReadinessStatusColorClasses(skill.readinessStatus) : "bg-muted text-muted-foreground"
                )}
              >
                {skill.skillName}
                {skill.readinessStatus ? ` · ${skillReadinessStatusLabel(skill.readinessStatus)}` : ""}
              </span>
            ))}
          </div>
        ) : null}

        <div className="mt-5 flex items-center gap-3">
          <div className="h-2 flex-1 overflow-hidden rounded-full bg-muted">
            <div
              className={cn("h-full rounded-full transition-all", PROGRESS_STATUS_BAR_CLASSES[series.progressStatus])}
              style={{ width: `${series.progressPercentage}%` }}
            />
          </div>
          <span className="shrink-0 text-sm font-semibold text-foreground">{series.progressPercentage}%</span>
        </div>
      </div>

      <div className="space-y-4">
        {series.modules.map((module) => (
          <ModuleCard key={module.id} module={module} />
        ))}
      </div>
    </div>
  );
}

function ModuleCard({ module }: { module: StudentPlacementModuleResponse }) {
  const navigate = useNavigate();
  return (
    <Card>
      <CardContent className="space-y-3 pt-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-semibold text-foreground">{module.name}</p>
            {module.description ? <p className="text-sm text-muted-foreground">{module.description}</p> : null}
          </div>
          <span className={cn("shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold", PROGRESS_STATUS_BADGE_CLASSES[module.status])}>
            {PROGRESS_STATUS_LABEL[module.status]} · {module.completedItemCount}/{module.totalItemCount}
          </span>
        </div>

        {module.items.length === 0 ? (
          <p className="rounded-lg border border-dashed border-border px-3 py-4 text-center text-xs text-muted-foreground">
            Content is being added to this module.
          </p>
        ) : (
          <div className="space-y-1.5">
            {module.items.map((item) => {
              const Icon = ITEM_ICON[item.itemType];
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => navigate(ROUTES.placementPrepModuleItem(item.id))}
                  className="flex w-full items-center gap-3 rounded-lg border border-border bg-card p-2.5 text-left hover:bg-muted/50"
                >
                  {item.status === "COMPLETED" ? (
                    <Check className="size-4 shrink-0 text-emerald-600" />
                  ) : (
                    <Circle className="size-4 shrink-0 text-muted-foreground" />
                  )}
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-accent">
                    <Icon className="size-3.5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm text-foreground">{item.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {MODULE_ITEM_TYPE_LABEL[item.itemType]}
                      {!item.required ? " · Optional" : ""}
                    </p>
                  </div>
                  {item.status === "IN_PROGRESS" ? (
                    <span className="shrink-0 text-xs font-medium text-amber-600">In Progress</span>
                  ) : null}
                </button>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
