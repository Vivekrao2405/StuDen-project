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
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import { acceptServiceRequest, rejectServiceRequest } from "@/lib/api/endpoints/serviceRequests";
import type { ServiceRequestRecord } from "@/lib/api/types";

interface RespondToRequestDialogProps {
  action: "accept" | "reject";
  open: boolean;
  onOpenChange: (open: boolean) => void;
  requestId: string;
  onSuccess: (request: ServiceRequestRecord) => void;
}

const COPY = {
  accept: {
    title: "Accept this service request?",
    description: "The requester will be notified that you accepted their request.",
    confirmLabel: "Accept Request",
    submittingLabel: "Accepting...",
  },
  reject: {
    title: "Reject this service request?",
    description: "The requester will be notified that this request wasn't accepted.",
    confirmLabel: "Reject Request",
    submittingLabel: "Rejecting...",
  },
} as const;

export function RespondToRequestDialog({ action, open, onOpenChange, requestId, onSuccess }: RespondToRequestDialogProps) {
  const [reason, setReason] = useState("");
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const copy = COPY[action];

  function resetAndClose() {
    setReason("");
    setApiError(null);
    onOpenChange(false);
  }

  async function handleConfirm() {
    setApiError(null);
    setSubmitting(true);
    try {
      const updated =
        action === "accept" ? await acceptServiceRequest(requestId) : await rejectServiceRequest(requestId, reason.trim() || undefined);
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
          <DialogTitle>{copy.title}</DialogTitle>
          <DialogDescription>{copy.description}</DialogDescription>
        </DialogHeader>

        {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

        {action === "reject" ? (
          <FormField label="Reason" htmlFor="reject-reason" hint="Optional — shared with the requester.">
            <Textarea
              id="reject-reason"
              placeholder="e.g. I don't have availability for this project right now."
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={3}
              disabled={submitting}
            />
          </FormField>
        ) : null}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={resetAndClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" variant={action === "reject" ? "destructive" : "default"} onClick={handleConfirm} disabled={submitting}>
            {submitting ? copy.submittingLabel : copy.confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
