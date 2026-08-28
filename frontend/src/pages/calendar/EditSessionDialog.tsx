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
import { FormField } from "@/components/shared/FormField";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api/ApiError";
import { updateSession } from "@/lib/api/endpoints/calendar";
import type { LearningSession } from "@/lib/api/calendarTypes";

interface EditSessionDialogProps {
  session: LearningSession;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onUpdated: (session: LearningSession) => void;
}

function toDateInputValue(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function toTimeInputValue(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

export function EditSessionDialog({ session, open, onOpenChange, onUpdated }: EditSessionDialogProps) {
  const [date, setDate] = useState(() => toDateInputValue(session.scheduledStart));
  const [time, setTime] = useState(() => toTimeInputValue(session.scheduledStart));
  const [duration, setDuration] = useState(String(session.durationMinutes));
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit() {
    setError(null);
    const scheduledStart = new Date(`${date}T${time}`);
    if (Number.isNaN(scheduledStart.getTime()) || scheduledStart.getTime() <= Date.now()) {
      setError("Start time must be a valid time in the future.");
      return;
    }
    const minutes = Number(duration);
    if (!Number.isFinite(minutes) || minutes < 5) {
      setError("Duration must be at least 5 minutes.");
      return;
    }

    setSubmitting(true);
    try {
      const updated = await updateSession(session.id, {
        scheduledStart: scheduledStart.toISOString(),
        durationMinutes: minutes,
      });
      onUpdated(updated);
      onOpenChange(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit session</DialogTitle>
          <DialogDescription>{session.topic ?? session.resource?.title ?? "Study session"}</DialogDescription>
        </DialogHeader>

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        <div className="grid grid-cols-2 gap-3">
          <FormField label="Date" htmlFor="edit-date">
            <Input id="edit-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} disabled={submitting} />
          </FormField>
          <FormField label="Start time" htmlFor="edit-time">
            <Input id="edit-time" type="time" value={time} onChange={(e) => setTime(e.target.value)} disabled={submitting} />
          </FormField>
        </div>
        <FormField label="Duration (minutes)" htmlFor="edit-duration">
          <Input
            id="edit-duration"
            type="number"
            min={5}
            step={5}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
            disabled={submitting}
          />
        </FormField>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Saving..." : "Save Changes"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
