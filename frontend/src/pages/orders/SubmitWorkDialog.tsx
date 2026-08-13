import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import { submitWork } from "@/lib/api/endpoints/orders";
import type { OrderResponse } from "@/lib/api/types";
import { isValidUrl } from "@/lib/validation";

const DESCRIPTION_MAX = 2000;

interface SubmitWorkDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderId: string;
  onSuccess: (order: OrderResponse) => void;
}

export function SubmitWorkDialog({ open, onOpenChange, orderId, onSuccess }: SubmitWorkDialogProps) {
  const [description, setDescription] = useState("");
  const [link, setLink] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function resetAndClose() {
    setDescription("");
    setLink("");
    setError(null);
    setApiError(null);
    onOpenChange(false);
  }

  async function handleSubmit() {
    const trimmed = description.trim();
    if (!trimmed) {
      setError("Please describe what you're delivering.");
      return;
    }
    if (trimmed.length > DESCRIPTION_MAX) {
      setError(`Please keep this under ${DESCRIPTION_MAX} characters.`);
      return;
    }
    if (link.trim() && !isValidUrl(link.trim())) {
      setError("Link needs to be a valid URL (https://...).");
      return;
    }
    setError(null);
    setApiError(null);
    setSubmitting(true);
    try {
      const updated = await submitWork(orderId, { description: trimmed, link: link.trim() || undefined });
      onSuccess(updated);
      resetAndClose();
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Submit your work</DialogTitle>
          <DialogDescription>The requester will be notified and asked to review it.</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

          <FormField label="What are you delivering?" htmlFor="submit-description" error={error ?? undefined}>
            <Textarea
              id="submit-description"
              placeholder="Summarize what you've completed and where the requester can find it..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={5}
              disabled={submitting}
            />
          </FormField>

          <FormField label="Link" htmlFor="submit-link" hint="Optional — a Drive, GitHub, or hosted link.">
            <Input
              id="submit-link"
              placeholder="https://..."
              value={link}
              onChange={(e) => setLink(e.target.value)}
              className="h-10"
              disabled={submitting}
            />
          </FormField>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={resetAndClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Submitting..." : "Submit Work"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
