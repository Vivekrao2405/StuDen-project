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
import { scheduleSession } from "@/lib/api/endpoints/calendar";
import type { LearningSession, LearningSessionCategory } from "@/lib/api/calendarTypes";
import { SESSION_CATEGORY_OPTIONS } from "@/pages/calendar/sessionCategoryDisplay";

const CATEGORY_SELECT_CLASS =
  "h-9 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30";

interface ScheduleSessionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  resourceId: string | null;
  resourceTitle: string;
  topic: string | null;
  defaultDurationMinutes?: number;
  onScheduled: (session: LearningSession) => void;
}

function defaultDate(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
}

// Shared by the Roadmap page ("Schedule" on a roadmap item) and the Calendar page (editing/adding
// against an already-known resource) — student always stays in control: this is the only way a
// single ad-hoc session gets created (the study plan generator is the other, separate path, and it
// never writes anything until the student explicitly saves it).
export function ScheduleSessionDialog({
  open,
  onOpenChange,
  resourceId,
  resourceTitle,
  topic,
  defaultDurationMinutes = 60,
  onScheduled,
}: ScheduleSessionDialogProps) {
  const [date, setDate] = useState(defaultDate);
  const [time, setTime] = useState("18:00");
  const [duration, setDuration] = useState(String(defaultDurationMinutes));
  const [category, setCategory] = useState<LearningSessionCategory>("LEARNING");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function reset() {
    setDate(defaultDate());
    setTime("18:00");
    setDuration(String(defaultDurationMinutes));
    setCategory("LEARNING");
    setError(null);
  }

  function close(next: boolean) {
    if (!next) reset();
    onOpenChange(next);
  }

  async function handleSubmit() {
    setError(null);
    const scheduledStart = new Date(`${date}T${time}`);
    if (Number.isNaN(scheduledStart.getTime())) {
      setError("Enter a valid date and time.");
      return;
    }
    if (scheduledStart.getTime() <= Date.now()) {
      setError("Start time must be in the future.");
      return;
    }
    const minutes = Number(duration);
    if (!Number.isFinite(minutes) || minutes < 5) {
      setError("Duration must be at least 5 minutes.");
      return;
    }

    setSubmitting(true);
    try {
      const session = await scheduleSession({
        resourceId,
        topic,
        scheduledStart: scheduledStart.toISOString(),
        durationMinutes: minutes,
        category,
      });
      onScheduled(session);
      close(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Schedule study session</DialogTitle>
          <DialogDescription>{resourceTitle}</DialogDescription>
        </DialogHeader>

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        <div className="grid grid-cols-2 gap-3">
          <FormField label="Date" htmlFor="schedule-date">
            <Input
              id="schedule-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              disabled={submitting}
            />
          </FormField>
          <FormField label="Start time" htmlFor="schedule-time">
            <Input
              id="schedule-time"
              type="time"
              value={time}
              onChange={(e) => setTime(e.target.value)}
              disabled={submitting}
            />
          </FormField>
        </div>
        <FormField label="Duration (minutes)" htmlFor="schedule-duration">
          <Input
            id="schedule-duration"
            type="number"
            min={5}
            step={5}
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
            disabled={submitting}
          />
        </FormField>
        <FormField label="Category" htmlFor="schedule-category">
          <select
            id="schedule-category"
            value={category}
            onChange={(e) => setCategory(e.target.value as LearningSessionCategory)}
            disabled={submitting}
            className={CATEGORY_SELECT_CLASS}
          >
            {SESSION_CATEGORY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </FormField>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => close(false)} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Scheduling..." : "Schedule"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
