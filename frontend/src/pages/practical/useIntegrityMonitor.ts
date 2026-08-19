import { useEffect, useRef, useState } from "react";

import { useToast } from "@/hooks/useToast";
import { sendHeartbeat, sendIntegrityEvents } from "@/lib/api/endpoints/practicalAssessments";
import type { IntegrityEventInput, IntegrityEventType, IntegrityPolicy } from "@/lib/api/practicalTypes";

const FLUSH_INTERVAL_MS = 8000;
const HEARTBEAT_INTERVAL_MS = 20000;
const MAX_QUEUED_EVENTS = 200;
// Two browser APIs (visibilitychange + blur, or visibilitychange + focus) commonly fire for the
// same physical tab switch/return — collapse anything within this window into one report rather
// than sending both (goal #4; the server also has an independent pairing-based safety net).
const CORRELATION_WINDOW_MS = 400;

interface UseIntegrityMonitorOptions {
  attemptId: string | null;
  active: boolean;
  policy: IntegrityPolicy | null;
}

function sessionIdFor(attemptId: string): string {
  const key = `studen.integritySession.${attemptId}`;
  let id = sessionStorage.getItem(key);
  if (!id) {
    id = crypto.randomUUID();
    sessionStorage.setItem(key, id);
  }
  return id;
}

/**
 * Mounted once per attempt from PracticalAttemptPage — a single wiring point that covers every
 * WorkspaceType, not duplicated per workspace component. Reports behavioral signals to the
 * backend (never decides severity/score itself — see com.studen.integrity), and blocks
 * copy/paste/cut in the workspace when the resolved policy disallows it. Never blocks the editor
 * on a network failure — failed batches are re-queued for the next flush, capped so a long outage
 * can't grow memory unbounded (goal #29).
 */
export function useIntegrityMonitor({ attemptId, active, policy }: UseIntegrityMonitorOptions) {
  const toast = useToast();
  const queueRef = useRef<IntegrityEventInput[]>([]);
  const lastAwayEdgeRef = useRef<{ kind: "away" | "back"; at: number } | null>(null);
  const policyRef = useRef<IntegrityPolicy | null>(policy);
  const [isFullscreen, setIsFullscreen] = useState(() => Boolean(document.fullscreenElement));
  policyRef.current = policy;

  useEffect(() => {
    if (!attemptId || !active) {
      return;
    }
    const sessionId = sessionIdFor(attemptId);

    function enqueue(eventType: IntegrityEventType) {
      queueRef.current.push({
        clientEventId: crypto.randomUUID(),
        eventType,
        occurredAt: new Date().toISOString(),
        sessionId,
      });
      if (queueRef.current.length > MAX_QUEUED_EVENTS) {
        queueRef.current.splice(0, queueRef.current.length - MAX_QUEUED_EVENTS);
      }
    }

    function flush() {
      if (queueRef.current.length === 0 || !attemptId) {
        return;
      }
      const batch = queueRef.current;
      queueRef.current = [];
      sendIntegrityEvents(attemptId, batch).catch(() => {
        queueRef.current = [...batch, ...queueRef.current].slice(-MAX_QUEUED_EVENTS);
      });
    }

    // true if this edge duplicates the one just reported through the other browser API.
    function isCorrelatedDuplicate(kind: "away" | "back") {
      const last = lastAwayEdgeRef.current;
      const now = Date.now();
      const duplicate = Boolean(last && last.kind === kind && now - last.at < CORRELATION_WINDOW_MS);
      lastAwayEdgeRef.current = { kind, at: now };
      return duplicate;
    }

    function onVisibilityChange() {
      if (document.visibilityState === "hidden") {
        if (!isCorrelatedDuplicate("away")) enqueue("TAB_HIDDEN");
        flush(); // flush immediately so nothing is lost if the tab is closed while away
      } else if (!isCorrelatedDuplicate("back")) {
        enqueue("TAB_VISIBLE");
      }
    }
    function onBlur() {
      if (!isCorrelatedDuplicate("away")) enqueue("WINDOW_BLUR");
    }
    function onFocus() {
      if (!isCorrelatedDuplicate("back")) enqueue("WINDOW_FOCUS");
    }

    // preventDefault() alone only suppresses the browser's native paste-into-textarea behavior —
    // Monaco (and any other editor) reads clipboardData and inserts it via its own JS-level paste
    // handler regardless, so the block only actually holds if the event never reaches that
    // handler at all. stopImmediatePropagation() on this capture-phase listener does that (live
    // browser testing caught this: preventDefault()-only left the toast showing while the pasted
    // text was still inserted).
    function onCopy(e: ClipboardEvent) {
      enqueue("COPY_ATTEMPT");
      if (!(policyRef.current?.allowCopy ?? true)) {
        e.preventDefault();
        e.stopImmediatePropagation();
        toast.info("Copying is disabled during this assessment.");
      }
    }
    function onPaste(e: ClipboardEvent) {
      enqueue("PASTE_ATTEMPT");
      if (!(policyRef.current?.allowPaste ?? true)) {
        e.preventDefault();
        e.stopImmediatePropagation();
        toast.info("Pasting is disabled during this assessment.");
      }
    }
    function onCut(e: ClipboardEvent) {
      enqueue("CUT_ATTEMPT");
      if (!(policyRef.current?.allowCut ?? true)) {
        e.preventDefault();
        e.stopImmediatePropagation();
        toast.info("Cutting is disabled during this assessment.");
      }
    }

    function onFullscreenChange() {
      const fs = Boolean(document.fullscreenElement);
      setIsFullscreen(fs);
      enqueue(fs ? "FULLSCREEN_ENTERED" : "FULLSCREEN_EXITED");
    }

    function onPopState() {
      enqueue("NAVIGATION_VIOLATION");
    }

    // Capture phase so this runs before Monaco's own editor DOM node handles the same event.
    document.addEventListener("visibilitychange", onVisibilityChange);
    window.addEventListener("blur", onBlur);
    window.addEventListener("focus", onFocus);
    document.addEventListener("copy", onCopy, true);
    document.addEventListener("paste", onPaste, true);
    document.addEventListener("cut", onCut, true);
    document.addEventListener("fullscreenchange", onFullscreenChange);
    window.addEventListener("popstate", onPopState);

    const flushTimer = window.setInterval(flush, FLUSH_INTERVAL_MS);
    const heartbeatTimer = window.setInterval(() => {
      sendHeartbeat(attemptId, sessionId).catch(() => {});
    }, HEARTBEAT_INTERVAL_MS);
    sendHeartbeat(attemptId, sessionId).catch(() => {});

    return () => {
      document.removeEventListener("visibilitychange", onVisibilityChange);
      window.removeEventListener("blur", onBlur);
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("copy", onCopy, true);
      document.removeEventListener("paste", onPaste, true);
      document.removeEventListener("cut", onCut, true);
      document.removeEventListener("fullscreenchange", onFullscreenChange);
      window.removeEventListener("popstate", onPopState);
      window.clearInterval(flushTimer);
      window.clearInterval(heartbeatTimer);
      flush(); // final flush so a submit/navigate-away doesn't lose the last batch (goal #29)
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [attemptId, active]);

  return {
    isFullscreen,
    requestFullscreen: () => {
      document.documentElement.requestFullscreen?.().catch(() => {
        // Some contexts (e.g. an iframe without allow="fullscreen") refuse this outright —
        // never make the assessment unusable because of it (goal #8).
      });
    },
  };
}
