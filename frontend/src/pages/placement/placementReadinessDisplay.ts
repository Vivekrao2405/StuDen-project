import type { SkillReadinessStatus } from "@/lib/api/placementTypes";

// Same lookup-table pattern as pages/assessment/assessmentLevelDisplay.ts — every place a skill
// readiness status needs a label or color reads from here rather than re-deriving it. Thresholds
// behind these labels are backend-configured (PlacementScoringProperties); this file only maps the
// already-decided status to display, never re-derives a status from a raw percentage.
const STATUS_LABELS: Record<SkillReadinessStatus, string> = {
  CRITICAL: "Critical",
  IMPROVE: "Improve",
  GOOD: "Good",
  STRONG: "Strong",
};

const STATUS_COLOR_CLASSES: Record<SkillReadinessStatus, string> = {
  CRITICAL: "bg-destructive/10 text-destructive",
  IMPROVE: "bg-amber-500/10 text-amber-600",
  GOOD: "bg-sky-500/10 text-sky-600",
  STRONG: "bg-emerald-500/10 text-emerald-600",
};

const STATUS_BAR_CLASSES: Record<SkillReadinessStatus, string> = {
  CRITICAL: "bg-destructive",
  IMPROVE: "bg-amber-500",
  GOOD: "bg-sky-500",
  STRONG: "bg-emerald-500",
};

export function skillReadinessStatusLabel(status: SkillReadinessStatus) {
  return STATUS_LABELS[status];
}

export function skillReadinessStatusColorClasses(status: SkillReadinessStatus) {
  return STATUS_COLOR_CLASSES[status];
}

export function skillReadinessStatusBarClasses(status: SkillReadinessStatus) {
  return STATUS_BAR_CLASSES[status];
}
