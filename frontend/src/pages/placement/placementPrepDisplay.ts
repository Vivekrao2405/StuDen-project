import type { ModuleItemType, PlacementProgressStatus, PreparationType } from "@/lib/api/placementTypes";

// Same lookup-table convention as placementDisplay.ts/placementReadinessDisplay.ts — a label or
// color is always read from here, never re-derived inline at a call site.
export const PREPARATION_TYPE_LABEL: Record<PreparationType, string> = {
  TECHNICAL: "Technical",
  CODING: "Coding",
  APTITUDE: "Aptitude",
  INTERVIEW: "Interview",
  FULL_PREPARATION: "Full Preparation",
};
export const PREPARATION_TYPE_OPTIONS = Object.entries(PREPARATION_TYPE_LABEL) as [PreparationType, string][];

export const MODULE_ITEM_TYPE_LABEL: Record<ModuleItemType, string> = {
  QUESTION: "Question",
  PRACTICAL_ASSESSMENT: "Practical / Coding",
  RESOURCE: "Resource",
};

export const PROGRESS_STATUS_LABEL: Record<PlacementProgressStatus, string> = {
  NOT_STARTED: "Not Started",
  IN_PROGRESS: "In Progress",
  COMPLETED: "Completed",
};

export const PROGRESS_STATUS_BADGE_CLASSES: Record<PlacementProgressStatus, string> = {
  NOT_STARTED: "bg-muted text-muted-foreground",
  IN_PROGRESS: "bg-amber-500/10 text-amber-600",
  COMPLETED: "bg-emerald-500/10 text-emerald-600",
};

export const PROGRESS_STATUS_BAR_CLASSES: Record<PlacementProgressStatus, string> = {
  NOT_STARTED: "bg-muted-foreground/30",
  IN_PROGRESS: "bg-primary",
  COMPLETED: "bg-emerald-500",
};
