import { Bug, CheckCircle2, Lightbulb, Loader2, MessageSquareText, Sparkles, Waypoints, X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { AiCoachActionType, AiCoachInteraction } from "@/lib/api/aiCoachTypes";
import { getAiCoachHistory, requestAiCoachAction } from "@/lib/api/endpoints/aiCoach";
import { ApiError } from "@/lib/api/ApiError";
import { cn } from "@/lib/utils";

interface AiCoachPanelProps {
  attemptId: string;
  attemptQuestionId: string;
  contextLabel: string;
}

const ACTIONS: { type: AiCoachActionType; label: string; icon: typeof Lightbulb; variant?: "outline" | "destructive" }[] = [
  { type: "HINT", label: "Hint", icon: Lightbulb },
  { type: "EXPLAIN_PATTERN", label: "Explain Pattern", icon: Waypoints },
  { type: "DEBUG", label: "Debug My Code", icon: Bug },
  { type: "EXPLAIN_CONCEPT", label: "Explain Concept", icon: MessageSquareText },
  { type: "SOLUTION", label: "Show Solution", icon: CheckCircle2, variant: "destructive" },
];

const ACTION_LABEL: Record<AiCoachActionType, string> = {
  HINT: "Hint",
  EXPLAIN_PATTERN: "Explain Pattern",
  DEBUG: "Debug My Code",
  EXPLAIN_CONCEPT: "Explain Concept",
  SOLUTION: "Solution",
};

/**
 * Floating launcher + right-anchored slide-over, modeled on MobileNavDrawer's bespoke pattern but
 * shown at every breakpoint (not just mobile) — one implementation covers both the desktop "clean
 * side panel" and mobile "drawer" requirements. Starts closed so it never dominates the coding
 * experience. Only rendered by the caller when the student arrived via a Placement Prep deep link
 * and the workspace is CODE_EDITOR/SQL_EDITOR — ordinary practical assessments never see this.
 */
export function AiCoachPanel({ attemptId, attemptQuestionId, contextLabel }: AiCoachPanelProps) {
  const [open, setOpen] = useState(false);
  const [historyLoaded, setHistoryLoaded] = useState(false);
  const [messages, setMessages] = useState<AiCoachInteraction[]>([]);
  const [pendingAction, setPendingAction] = useState<AiCoachActionType | null>(null);
  const [topic, setTopic] = useState("");
  const [errorState, setErrorState] = useState<"unavailable" | "rate-limited" | "generic" | null>(null);

  useEffect(() => {
    if (!open) return;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  useEffect(() => {
    if (!open || historyLoaded) return;
    setHistoryLoaded(true);
    getAiCoachHistory(attemptId, attemptQuestionId)
      .then(setMessages)
      .catch(() => {
        // History is a convenience, not core to the action buttons below — fail silently and let
        // the student start fresh rather than blocking the panel on a transient load error.
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, historyLoaded]);

  async function runAction(actionType: AiCoachActionType) {
    if (pendingAction) return;
    setPendingAction(actionType);
    setErrorState(null);
    try {
      const response = await requestAiCoachAction(attemptId, attemptQuestionId, {
        actionType,
        topic: actionType === "EXPLAIN_CONCEPT" ? topic.trim() || null : null,
      });
      setMessages((prev) => [...prev, response]);
      if (actionType === "EXPLAIN_CONCEPT") setTopic("");
    } catch (err) {
      if (err instanceof ApiError && err.status === 503) {
        setErrorState("unavailable");
      } else if (err instanceof ApiError && err.status === 429) {
        setErrorState("rate-limited");
      } else {
        setErrorState("generic");
      }
    } finally {
      setPendingAction(null);
    }
  }

  return (
    <>
      {!open ? (
        <Button
          onClick={() => setOpen(true)}
          className="fixed right-5 bottom-5 z-40 shadow-lg"
        >
          <Sparkles className="size-4" /> AI Coach
        </Button>
      ) : null}

      <div className={cn("fixed inset-0 z-50", open ? "" : "pointer-events-none")} aria-hidden={!open}>
        <div
          className={cn("absolute inset-0 bg-black/40 transition-opacity", open ? "opacity-100" : "opacity-0")}
          onClick={() => setOpen(false)}
        />
        <div
          className={cn(
            "absolute top-0 right-0 flex h-full w-full max-w-full flex-col bg-card shadow-xl transition-transform duration-200 sm:w-[420px]",
            open ? "translate-x-0" : "translate-x-full"
          )}
        >
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <div className="min-w-0">
              <p className="flex items-center gap-1.5 text-sm font-semibold text-foreground">
                <Sparkles className="size-4 text-primary" /> AI Coach
              </p>
              <p className="truncate text-xs text-muted-foreground">{contextLabel}</p>
            </div>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="Close AI Coach"
              className="rounded-full p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              <X className="size-5" />
            </button>
          </div>

          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
            {messages.length === 0 && !pendingAction ? (
              <p className="text-sm text-muted-foreground">
                Ask for a hint, an explanation of the pattern, help debugging, a concept explainer, or the full
                solution — whichever you need right now.
              </p>
            ) : null}
            {messages.map((message) => (
              <div key={message.id} className="rounded-lg border border-border bg-muted/40 p-3 text-sm">
                <p className="mb-1 text-xs font-semibold text-primary">
                  {ACTION_LABEL[message.actionType]}
                  {message.hintLevel ? ` #${message.hintLevel}` : ""}
                </p>
                <p className="whitespace-pre-wrap text-foreground">{message.message}</p>
              </div>
            ))}
            {pendingAction ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="size-4 animate-spin" /> Thinking...
              </div>
            ) : null}
            {errorState === "unavailable" ? (
              <p className="rounded-lg border border-border bg-muted/40 p-3 text-sm text-muted-foreground">
                AI Coach isn't configured yet. Ask an administrator to add an OpenAI API key.
              </p>
            ) : null}
            {errorState === "rate-limited" ? (
              <p className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700">
                You've reached the AI Coach limit for now — try again shortly.
              </p>
            ) : null}
            {errorState === "generic" ? (
              <p className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">
                Something went wrong. Please try again.
              </p>
            ) : null}
          </div>

          <div className="space-y-2 border-t border-border p-4">
            <Input
              placeholder="Optional: topic for Explain Concept (e.g. HashMap)"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              disabled={pendingAction !== null}
            />
            <div className="grid grid-cols-2 gap-2">
              {ACTIONS.map((action) => (
                <Button
                  key={action.type}
                  variant={action.variant ?? "outline"}
                  size="sm"
                  disabled={pendingAction !== null}
                  onClick={() => runAction(action.type)}
                >
                  <action.icon className="size-3.5" /> {action.label}
                </Button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
