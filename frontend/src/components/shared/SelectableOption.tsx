import { Check } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

interface SelectableOptionProps {
  selected: boolean;
  /** true renders a checkbox (multi-select); false (default) renders a radio (single-select). */
  multi?: boolean;
  onClick: () => void;
  title: string;
  subtitle?: string;
  icon?: ReactNode;
  disabled?: boolean;
}

/** Same accessible selectable-button pattern AssessmentOptionList already uses for quiz options —
 * reused here for placement onboarding's single/multi-select steps (target role, company types,
 * current skills, experience level) so onboarding feels like a native part of StuDen. */
export function SelectableOption({ selected, multi = false, onClick, title, subtitle, icon, disabled }: SelectableOptionProps) {
  return (
    <button
      type="button"
      role={multi ? "checkbox" : "radio"}
      aria-checked={selected}
      disabled={disabled}
      onClick={onClick}
      className={cn(
        "flex w-full items-center gap-3 rounded-xl border px-4 py-3.5 text-left text-sm transition-colors disabled:opacity-60",
        selected ? "border-primary bg-primary/5 text-foreground" : "border-border bg-card text-foreground hover:bg-muted/50"
      )}
    >
      {icon}
      <span className="min-w-0 flex-1">
        <span className="block font-medium leading-snug">{title}</span>
        {subtitle ? <span className="block text-xs text-muted-foreground">{subtitle}</span> : null}
      </span>
      <span
        className={cn(
          "flex size-5 shrink-0 items-center justify-center border text-primary-foreground",
          multi ? "rounded-md" : "rounded-full",
          selected ? "border-primary bg-primary" : "border-border bg-transparent"
        )}
      >
        {selected ? <Check className="size-3.5" strokeWidth={3} /> : null}
      </span>
    </button>
  );
}
