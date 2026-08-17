import { useEffect, useRef } from "react";

/**
 * Returns a function that, when called repeatedly, only invokes `callback` once `delayMs` has
 * passed since the last call — used for practical-assessment autosave (spec §39: "user stops
 * typing -> debounce -> save draft", never a request per keystroke). Plain useEffect/setTimeout,
 * no new dependency.
 */
export function useDebouncedCallback<Args extends unknown[]>(callback: (...args: Args) => void, delayMs: number) {
  const callbackRef = useRef(callback);
  callbackRef.current = callback;
  const timeoutRef = useRef<number | null>(null);

  useEffect(() => {
    return () => {
      if (timeoutRef.current !== null) {
        window.clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  return (...args: Args) => {
    if (timeoutRef.current !== null) {
      window.clearTimeout(timeoutRef.current);
    }
    timeoutRef.current = window.setTimeout(() => {
      callbackRef.current(...args);
    }, delayMs);
  };
}
