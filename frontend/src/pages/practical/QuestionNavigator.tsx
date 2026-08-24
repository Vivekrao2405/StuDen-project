import { cn } from "@/lib/utils";
import type { PracticalAttemptQuestion } from "@/lib/api/practicalTypes";

// [1] [2] [3] [4] [5] — visual state per spec §6: not attempted / code entered / successfully
// executed / completed. Never reveals hidden test cases or expected answers — just the question's
// own status, already computed server-side.
export function QuestionNavigator({
  questions,
  activeIndex,
  onSelect,
}: {
  questions: PracticalAttemptQuestion[];
  activeIndex: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5" role="tablist" aria-label="Questions">
      {questions.map((q, i) => {
        const isActive = i === activeIndex;
        const isAttempted = q.status !== "NOT_ATTEMPTED";
        const isDone = q.status === "PASSED" || q.status === "EVALUATED";
        return (
          <button
            key={q.id}
            type="button"
            role="tab"
            aria-selected={isActive}
            aria-label={`Question ${i + 1}${isDone ? " (completed)" : isAttempted ? " (in progress)" : " (not attempted)"}`}
            onClick={() => onSelect(i)}
            className={cn(
              "flex size-8 items-center justify-center rounded-full text-xs font-semibold transition-colors",
              isActive
                ? "bg-primary text-primary-foreground"
                : isDone
                  ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-400"
                  : isAttempted
                    ? "bg-amber-100 text-amber-700 dark:bg-amber-950 dark:text-amber-400"
                    : "bg-muted text-muted-foreground hover:text-foreground"
            )}
          >
            {i + 1}
          </button>
        );
      })}
    </div>
  );
}
