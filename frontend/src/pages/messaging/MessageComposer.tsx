import { Send } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ApiError } from "@/lib/api/ApiError";

const MAX_LENGTH = 2000;
const WARN_THRESHOLD = 1800;

interface MessageComposerProps {
  onSend: (content: string) => Promise<unknown>;
}

export function MessageComposer({ onSend }: MessageComposerProps) {
  const [value, setValue] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);

  const trimmed = value.trim();
  const canSend = trimmed.length > 0 && trimmed.length <= MAX_LENGTH && !submitting;

  async function handleSend() {
    if (!canSend) return;
    setApiError(null);
    setSubmitting(true);
    try {
      await onSend(trimmed);
      setValue("");
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Couldn't send that message. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  return (
    <div className="border-t border-border bg-background p-3">
      {apiError ? <p className="mb-2 text-xs text-destructive">{apiError}</p> : null}
      <div className="flex items-end gap-2">
        <Textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message..."
          rows={1}
          maxLength={MAX_LENGTH}
          disabled={submitting}
          className="max-h-32 min-h-10 flex-1 resize-none py-2.5"
        />
        <Button type="button" size="icon" onClick={handleSend} disabled={!canSend} aria-label="Send message">
          <Send className="size-4" />
        </Button>
      </div>
      {trimmed.length > WARN_THRESHOLD ? (
        <p className="mt-1 text-right text-[11px] text-muted-foreground">{trimmed.length}/{MAX_LENGTH}</p>
      ) : null}
    </div>
  );
}
