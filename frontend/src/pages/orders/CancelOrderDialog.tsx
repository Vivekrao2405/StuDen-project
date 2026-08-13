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
import { cancelOrder } from "@/lib/api/endpoints/orders";
import type { OrderResponse } from "@/lib/api/types";

interface CancelOrderDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderId: string;
  onSuccess: (order: OrderResponse) => void;
}

export function CancelOrderDialog({ open, onOpenChange, orderId, onSuccess }: CancelOrderDialogProps) {
  const [reason, setReason] = useState("");
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function resetAndClose() {
    setReason("");
    setApiError(null);
    onOpenChange(false);
  }

  async function handleConfirm() {
    setApiError(null);
    setSubmitting(true);
    try {
      const updated = await cancelOrder(orderId, reason.trim() ? { reason: reason.trim() } : undefined);
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
          <DialogTitle>Cancel this order?</DialogTitle>
          <DialogDescription>This action cannot be undone. The other participant will be notified.</DialogDescription>
        </DialogHeader>

        {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

        <FormField label="Reason" htmlFor="cancel-reason" hint="Optional — shared with the other participant.">
          <Textarea
            id="cancel-reason"
            placeholder="e.g. No longer able to complete this project."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            disabled={submitting}
          />
        </FormField>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={resetAndClose} disabled={submitting}>
            Keep Order
          </Button>
          <Button type="button" variant="destructive" onClick={handleConfirm} disabled={submitting}>
            {submitting ? "Cancelling..." : "Cancel Order"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
