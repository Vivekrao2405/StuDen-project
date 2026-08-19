import { CodeBlock } from "@/components/shared/CodeBlock";
import { parseQuestionContent } from "@/lib/questionContent";
import { cn } from "@/lib/utils";

interface QuestionContentProps {
  /** Raw question/option/explanation text — may contain ```lang fenced code blocks. */
  text: string;
  className?: string;
  /** Applied to each plain-text segment's <p> (e.g. to match the surrounding heading size). */
  textClassName?: string;
}

// Renders Question Bank text (question stem, option text, explanation) with fenced code blocks
// (```python ... ```) pulled out into a dedicated CodeBlock, everything else as plain escaped text.
// Text segments use whitespace-pre-wrap so authored line breaks/blank lines survive too — this is
// the same convention already used elsewhere in the app for multi-line free text (order
// requirements, project descriptions, chat messages).
export function QuestionContent({ text, className, textClassName }: QuestionContentProps) {
  const segments = parseQuestionContent(text);
  if (segments.length === 0) return null;

  return (
    <div className={cn("space-y-2.5", className)}>
      {segments.map((segment, i) =>
        segment.type === "code" ? (
          <CodeBlock key={i} code={segment.value} language={segment.language} />
        ) : (
          <p key={i} className={cn("whitespace-pre-wrap", textClassName)}>
            {segment.value.trim()}
          </p>
        )
      )}
    </div>
  );
}
