import { useEffect, useState } from "react";

interface TypingOptions {
  charDelayMs?: number;
  lineDelayMs?: number;
}

interface TypingState {
  displayedText: string;
  lineIndex: number;
  done: boolean;
}

function getFullText(lines: string[]) {
  return lines.join("\n");
}

export function useTypingEffect(lines: string[], options: TypingOptions = {}): TypingState {
  const { charDelayMs = 35, lineDelayMs = 400 } = options;
  const fullText = getFullText(lines);

  const [state, setState] = useState<TypingState>(() => {
    const reducedMotion =
      typeof window !== "undefined" &&
      window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    return reducedMotion
      ? { displayedText: fullText, lineIndex: lines.length - 1, done: true }
      : { displayedText: "", lineIndex: 0, done: false };
  });

  useEffect(() => {
    if (state.done) return;

    let charIndex = 0;
    let timeoutId: ReturnType<typeof setTimeout>;

    const tick = () => {
      charIndex += 1;
      const nextText = fullText.slice(0, charIndex);
      const linesSoFar = nextText.split("\n");
      const isDone = charIndex >= fullText.length;

      setState({
        displayedText: nextText,
        lineIndex: Math.max(0, linesSoFar.length - 1),
        done: isDone,
      });

      if (isDone) return;

      const justCrossedLine = fullText[charIndex - 1] === "\n";
      timeoutId = setTimeout(tick, justCrossedLine ? lineDelayMs : charDelayMs);
    };

    timeoutId = setTimeout(tick, charDelayMs);

    return () => clearTimeout(timeoutId);
    // Mount-only effect; the cleanup cancels any pending timer so React StrictMode's
    // dev double-invoke (mount -> cleanup -> mount) still types exactly once.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return state;
}
