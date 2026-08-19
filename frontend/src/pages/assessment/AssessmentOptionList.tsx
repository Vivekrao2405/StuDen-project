import { Check } from "lucide-react";

import { QuestionContent } from "@/components/shared/QuestionContent";
import type { AssessmentOptionView, QuestionType } from "@/lib/api/types";
import { isPlainTextContent } from "@/lib/questionContent";
import { cn } from "@/lib/utils";

interface AssessmentOptionListProps {
  options: AssessmentOptionView[];
  questionType: QuestionType;
  selectedOptionIds: string[];
  onChange: (selectedOptionIds: string[]) => void;
  disabled?: boolean;
}

/** Radio-style single selection for MCQ_SINGLE/TRUE_FALSE, checkbox-style multi-selection for
 * MCQ_MULTIPLE — same option list shape either way, only the toggle behavior differs. Large tap
 * targets throughout for mobile (spec §21). */
export function AssessmentOptionList({
  options,
  questionType,
  selectedOptionIds,
  onChange,
  disabled,
}: AssessmentOptionListProps) {
  const multi = questionType === "MCQ_MULTIPLE";

  function toggle(optionId: string) {
    if (disabled) return;
    if (multi) {
      const next = selectedOptionIds.includes(optionId)
        ? selectedOptionIds.filter((id) => id !== optionId)
        : [...selectedOptionIds, optionId];
      onChange(next);
    } else {
      onChange([optionId]);
    }
  }

  return (
    <div className="space-y-2.5" role={multi ? "group" : "radiogroup"}>
      {options.map((option) => {
        const selected = selectedOptionIds.includes(option.id);
        const plainText = isPlainTextContent(option.optionText);
        return (
          <button
            key={option.id}
            type="button"
            role={multi ? "checkbox" : "radio"}
            aria-checked={selected}
            disabled={disabled}
            onClick={() => toggle(option.id)}
            className={cn(
              "flex w-full gap-3 rounded-xl border px-4 py-3.5 text-left text-sm transition-colors disabled:opacity-60",
              plainText ? "items-center" : "items-start",
              selected
                ? "border-primary bg-primary/5 text-foreground"
                : "border-border bg-card text-foreground hover:bg-muted/50"
            )}
          >
            <span
              className={cn(
                "flex size-5 shrink-0 items-center justify-center border text-primary-foreground",
                multi ? "rounded-md" : "rounded-full",
                !plainText && "mt-0.5",
                selected ? "border-primary bg-primary" : "border-border bg-transparent"
              )}
            >
              {selected ? <Check className="size-3.5" strokeWidth={3} /> : null}
            </span>
            {plainText ? (
              <span className="leading-snug">{option.optionText}</span>
            ) : (
              <QuestionContent text={option.optionText} className="min-w-0 flex-1" textClassName="leading-snug" />
            )}
          </button>
        );
      })}
    </div>
  );
}
