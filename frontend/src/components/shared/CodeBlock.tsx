import { cn } from "@/lib/utils";

const LANGUAGE_LABELS: Record<string, string> = {
  py: "Python",
  python: "Python",
  java: "Java",
  c: "C",
  cpp: "C++",
  "c++": "C++",
  js: "JavaScript",
  javascript: "JavaScript",
  ts: "TypeScript",
  typescript: "TypeScript",
  sql: "SQL",
  html: "HTML",
  css: "CSS",
};

function languageLabel(language: string | null): string {
  if (!language) return "Code";
  return LANGUAGE_LABELS[language.toLowerCase()] ?? language;
}

interface CodeBlockProps {
  code: string;
  language?: string | null;
  className?: string;
}

// Dedicated code renderer for Question Bank content (questions/options can embed fenced code —
// see questionContent.ts). Deliberately plain-text only: no dangerouslySetInnerHTML, no copy
// button (Assessment Integrity work is a later phase), no syntax highlighting library. Horizontal
// scroll is scoped to this block via overflow-x-auto so long lines never widen the page or the
// assessment card itself.
export function CodeBlock({ code, language = null, className }: CodeBlockProps) {
  return (
    <div className={cn("w-full min-w-0 overflow-hidden rounded-lg border border-border bg-muted/30", className)}>
      <div className="border-b border-border bg-muted/60 px-3 py-1.5 text-[11px] font-semibold tracking-wide text-muted-foreground uppercase">
        {languageLabel(language)}
      </div>
      <pre className="overflow-x-auto p-3 text-xs leading-relaxed sm:text-[13px]">
        <code className="font-mono whitespace-pre text-foreground">{code}</code>
      </pre>
    </div>
  );
}
