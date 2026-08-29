import { BookOpen, Briefcase, ClipboardCheck, FlaskConical } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import type { LearningSessionCategory } from "@/lib/api/calendarTypes";

export const SESSION_CATEGORY_LABEL: Record<LearningSessionCategory, string> = {
  LEARNING: "Learning",
  PRACTICE: "Practice",
  PROJECT: "Project",
  ASSESSMENT: "Assessment",
};

export const SESSION_CATEGORY_OPTIONS: { value: LearningSessionCategory; label: string }[] = (
  Object.keys(SESSION_CATEGORY_LABEL) as LearningSessionCategory[]
).map((value) => ({ value, label: SESSION_CATEGORY_LABEL[value] }));

export function sessionCategoryIcon(category: LearningSessionCategory): LucideIcon {
  switch (category) {
    case "LEARNING":
      return BookOpen;
    case "PRACTICE":
      return FlaskConical;
    case "PROJECT":
      return Briefcase;
    case "ASSESSMENT":
      return ClipboardCheck;
  }
}

// One pastel accent per category (dot on the Month grid, badge on schedule rows, icon chip on the
// day panel) — reuses the same amber/primary/emerald token family already established elsewhere
// (FocusAreaCard, LearningOverviewCard) plus violet/sky for the two categories that needed a
// distinct fourth/fifth hue.
interface CategoryAccent {
  dot: string;
  badgeBg: string;
  badgeText: string;
  iconBg: string;
  iconText: string;
}

const CATEGORY_ACCENT: Record<LearningSessionCategory, CategoryAccent> = {
  LEARNING: {
    dot: "bg-primary",
    badgeBg: "bg-primary/10",
    badgeText: "text-primary",
    iconBg: "bg-primary/10",
    iconText: "text-primary",
  },
  PRACTICE: {
    dot: "bg-emerald-500",
    badgeBg: "bg-emerald-500/10",
    badgeText: "text-emerald-600 dark:text-emerald-400",
    iconBg: "bg-emerald-500/10",
    iconText: "text-emerald-600 dark:text-emerald-400",
  },
  PROJECT: {
    dot: "bg-violet-500",
    badgeBg: "bg-violet-500/10",
    badgeText: "text-violet-600 dark:text-violet-400",
    iconBg: "bg-violet-500/10",
    iconText: "text-violet-600 dark:text-violet-400",
  },
  ASSESSMENT: {
    dot: "bg-sky-500",
    badgeBg: "bg-sky-500/10",
    badgeText: "text-sky-600 dark:text-sky-400",
    iconBg: "bg-sky-500/10",
    iconText: "text-sky-600 dark:text-sky-400",
  },
};

export function sessionCategoryAccent(category: LearningSessionCategory): CategoryAccent {
  return CATEGORY_ACCENT[category];
}
