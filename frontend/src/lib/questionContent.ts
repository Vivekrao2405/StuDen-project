// Smallest-safe-extension content model for Question Bank text (question/option/explanation).
// No markdown pipeline exists in this codebase (see repo audit) — rather than pulling in a full
// markdown renderer (and the sanitization surface that comes with it), this recognizes exactly one
// convention: fenced code blocks using the same ```lang ... ``` syntax GitHub/Slack/etc. already use.
// Everything else is rendered as plain escaped text, so ordinary questions are completely unaffected.

export interface QuestionTextSegment {
  type: "text";
  value: string;
}

export interface QuestionCodeSegment {
  type: "code";
  language: string | null;
  value: string;
}

export type QuestionContentSegment = QuestionTextSegment | QuestionCodeSegment;

const FENCE_RE = /```([^\n`]*)\n([\s\S]*?)```/g;

/** Splits raw question/option/explanation text into alternating text and fenced-code segments.
 * Blank text segments (the newlines immediately around a fence) are dropped; everything inside a
 * fence is preserved byte-for-byte except a single trailing newline before the closing fence. */
export function parseQuestionContent(raw: string): QuestionContentSegment[] {
  if (!raw) return [];

  const segments: QuestionContentSegment[] = [];
  let lastIndex = 0;
  FENCE_RE.lastIndex = 0;

  let match: RegExpExecArray | null;
  while ((match = FENCE_RE.exec(raw)) !== null) {
    if (match.index > lastIndex) {
      const textValue = raw.slice(lastIndex, match.index);
      if (textValue.trim()) segments.push({ type: "text", value: textValue });
    }
    const language = match[1].trim();
    const code = match[2].replace(/\n$/, "");
    segments.push({ type: "code", language: language || null, value: code });
    lastIndex = FENCE_RE.lastIndex;
  }

  if (lastIndex < raw.length) {
    const textValue = raw.slice(lastIndex);
    if (textValue.trim() || segments.length === 0) segments.push({ type: "text", value: textValue });
  }

  return segments;
}

/** True when the content is plain text with no code fence — lets callers keep their existing
 * single-line rendering untouched (e.g. inside a flex row) instead of switching to the block layout
 * that fenced code needs. */
export function isPlainTextContent(raw: string): boolean {
  const segments = parseQuestionContent(raw);
  return segments.length <= 1 && (segments.length === 0 || segments[0].type === "text");
}
