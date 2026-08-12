import { X } from "lucide-react";

import { SkillIcon } from "@/components/shared/SkillIcon";
import type { SkillIconType } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface SkillChipProps {
  skill: { id: string; name: string; category: string; iconSlug: string | null; iconType: SkillIconType };
  onRemove?: () => void;
  removeDisabled?: boolean;
  /** "card" (default): icon, name and category — used wherever there's room to show category.
   *  "compact": icon and name only, single line — for tight spaces like the share profile card. */
  variant?: "card" | "compact";
  className?: string;
}

/**
 * The skill chip layout used everywhere a skill is shown as a pill/card. Shared across
 * SkillPicker's selected chips, the public profile and the shareable profile card so they stay
 * visually consistent instead of drifting apart.
 */
export function SkillChip({ skill, onRemove, removeDisabled, variant = "card", className }: SkillChipProps) {
  if (variant === "compact") {
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1.5 rounded-full border border-border bg-card py-1 pr-1.5 pl-2 text-sm text-foreground",
          className
        )}
      >
        <SkillIcon iconSlug={skill.iconSlug} iconType={skill.iconType} className="size-4 shrink-0" />
        {skill.name}
        {onRemove ? (
          <button
            type="button"
            onClick={onRemove}
            disabled={removeDisabled}
            aria-label={`Remove ${skill.name}`}
            className="rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
          >
            <X className="size-3.5" />
          </button>
        ) : null}
      </span>
    );
  }

  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 rounded-lg border border-border bg-card py-1.5 pr-2 pl-2 text-foreground",
        className
      )}
    >
      <SkillIcon iconSlug={skill.iconSlug} iconType={skill.iconType} className="size-5 shrink-0" />
      <span className="flex flex-col leading-tight">
        <span className="text-sm font-medium">{skill.name}</span>
        <span className="text-[11px] text-muted-foreground">{skill.category}</span>
      </span>
      {onRemove ? (
        <button
          type="button"
          onClick={onRemove}
          disabled={removeDisabled}
          aria-label={`Remove ${skill.name}`}
          className="ml-1 shrink-0 rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
        >
          <X className="size-3.5" />
        </button>
      ) : null}
    </span>
  );
}
