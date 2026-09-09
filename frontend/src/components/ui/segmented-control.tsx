import { cn } from "@/lib/utils";

interface SegmentedControlOption<T extends string> {
  value: T;
  label: string;
}

interface SegmentedControlProps<T extends string> {
  value: T;
  onChange: (value: T) => void;
  options: SegmentedControlOption<T>[];
  className?: string;
  /** "neutral" (default): white/shadow active segment, used everywhere today. "primary": active
   *  segment picks up the brand blue tint instead — opt in per page rather than changing the
   *  shared default so every other existing usage stays pixel-identical. */
  variant?: "neutral" | "primary";
}

/** A small pill-style tab switch built from primitives already used elsewhere in the app —
 * there's no dedicated Tabs component in this codebase yet, and this is the only place that
 * currently needs one. */
export function SegmentedControl<T extends string>({
  value,
  onChange,
  options,
  className,
  variant = "neutral",
}: SegmentedControlProps<T>) {
  return (
    <div role="tablist" className={cn("inline-flex w-fit rounded-full border border-border bg-muted p-1", className)}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          role="tab"
          aria-selected={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            "rounded-full px-4 py-1.5 text-sm font-medium whitespace-nowrap transition-colors",
            value === option.value
              ? variant === "primary"
                ? "bg-primary/10 text-primary"
                : "bg-background text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
