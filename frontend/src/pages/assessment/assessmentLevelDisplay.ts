import type { AssessmentLevel, TopicPerformanceTier } from "@/lib/api/types";

// Simple lookup tables, same pattern as questionBankOptions.ts's statusBadgeVariant/
// difficultyBadgeVariant — every place a level/tier needs a label or color reads from here rather
// than re-deriving it. Label text always says "Assessment Level", never "Verified Skill" (spec §20).
const LEVEL_LABELS: Record<AssessmentLevel, string> = {
  BEGINNER: "Beginner",
  DEVELOPING: "Developing",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
  EXPERT: "Expert",
};

const LEVEL_COLOR_CLASSES: Record<AssessmentLevel, string> = {
  BEGINNER: "bg-slate-500/10 text-slate-600",
  DEVELOPING: "bg-amber-500/10 text-amber-600",
  INTERMEDIATE: "bg-sky-500/10 text-sky-600",
  ADVANCED: "bg-emerald-500/10 text-emerald-600",
  EXPERT: "bg-violet-500/10 text-violet-600",
};

const TIER_BAR_CLASSES: Record<TopicPerformanceTier, string> = {
  NEEDS_IMPROVEMENT: "bg-destructive",
  DEVELOPING: "bg-amber-500",
  STRONG: "bg-emerald-500",
};

export function levelLabel(level: AssessmentLevel) {
  return LEVEL_LABELS[level];
}

export function levelColorClasses(level: AssessmentLevel) {
  return LEVEL_COLOR_CLASSES[level];
}

export function tierBarClasses(tier: TopicPerformanceTier) {
  return TIER_BAR_CLASSES[tier];
}
