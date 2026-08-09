import { useTypingEffect } from "@/components/shared/useTypingEffect";

interface Segment {
  text: string;
  accent?: boolean;
}

const LINE_1: Segment[] = [
  { text: "Need something " },
  { text: "done?", accent: true },
];

const LINE_2: Segment[] = [
  { text: "Find a " },
  { text: "student", accent: true },
  { text: " who can do it." },
];

const LINES = [LINE_1, LINE_2];
const PLAIN_LINES = LINES.map((segments) => segments.map((s) => s.text).join(""));

function renderLine(segments: Segment[], typedText: string) {
  let offset = 0;
  return segments.map((segment, i) => {
    const visibleLen = Math.max(0, Math.min(segment.text.length, typedText.length - offset));
    offset += segment.text.length;
    const visible = segment.text.slice(0, visibleLen);
    if (!visible) return null;
    return (
      <span key={i} className={segment.accent ? "text-primary" : undefined}>
        {visible}
      </span>
    );
  });
}

export function TypingHeadline() {
  const { displayedText, done } = useTypingEffect(PLAIN_LINES);
  const typedLines = displayedText.split("\n");

  return (
    <h1 className="text-4xl font-bold tracking-tight text-foreground sm:text-5xl lg:text-6xl">
      {LINES.map((segments, i) => (
        <span key={i} className="block">
          {renderLine(segments, typedLines[i] ?? "")}
          {!done && i === typedLines.length - 1 ? (
            <span className="ml-0.5 inline-block w-[3px] animate-pulse bg-primary align-middle" style={{ height: "0.85em" }} />
          ) : null}
        </span>
      ))}
    </h1>
  );
}
