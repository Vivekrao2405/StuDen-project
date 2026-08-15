import { cn } from "@/lib/utils";

interface AssessmentQuestionNavProps {
  total: number;
  currentIndex: number;
  answeredIndexes: Set<number>;
  onSelect: (index: number) => void;
}

/** Horizontal-scrolling dot navigator: filled+checked = answered, outlined = unanswered, ring on
 * the current question. Clicking jumps directly to that question without losing any answers. */
export function AssessmentQuestionNav({ total, currentIndex, answeredIndexes, onSelect }: AssessmentQuestionNavProps) {
  return (
    <div className="-mx-4 flex gap-1.5 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
      {Array.from({ length: total }, (_, i) => {
        const answered = answeredIndexes.has(i);
        const active = i === currentIndex;
        return (
          <button
            key={i}
            type="button"
            onClick={() => onSelect(i)}
            aria-current={active}
            aria-label={`Question ${i + 1}${answered ? ", answered" : ", unanswered"}`}
            className={cn(
              "flex size-8 shrink-0 items-center justify-center rounded-full border text-xs font-medium transition-colors",
              answered ? "border-primary bg-primary text-primary-foreground" : "border-border bg-card text-muted-foreground",
              active ? "ring-2 ring-primary ring-offset-2 ring-offset-background" : null
            )}
          >
            {i + 1}
          </button>
        );
      })}
    </div>
  );
}
