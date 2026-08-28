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
import { previewStudyPlan, saveStudyPlan } from "@/lib/api/endpoints/calendar";
import type { StudyPlanSessionSuggestion } from "@/lib/api/calendarTypes";
import { topicLabel } from "@/lib/learningTags";

interface StudyPlanDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSaved: (createdCount: number, skippedCount: number) => void;
}

const DAYS: { value: string; label: string }[] = [
  { value: "MONDAY", label: "Mon" },
  { value: "TUESDAY", label: "Tue" },
  { value: "WEDNESDAY", label: "Wed" },
  { value: "THURSDAY", label: "Thu" },
  { value: "FRIDAY", label: "Fri" },
  { value: "SATURDAY", label: "Sat" },
  { value: "SUNDAY", label: "Sun" },
];

interface EditableSuggestion extends StudyPlanSessionSuggestion {
  time: string;
  included: boolean;
}

function defaultStartDate(): string {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  return d.toISOString().slice(0, 10);
}

// Generates a suggested study plan from the roadmap's own priority ordering (RoadmapService),
// lets the student edit times/duration or drop days, then persists exactly what they confirm —
// nothing is written until "Save Plan" is clicked (section 14's explicit requirement).
export function StudyPlanDialog({ open, onOpenChange, onSaved }: StudyPlanDialogProps) {
  const [step, setStep] = useState<"form" | "preview">("form");
  const [startDate, setStartDate] = useState(defaultStartDate);
  const [durationMinutesPerDay, setDurationMinutesPerDay] = useState("60");
  const [selectedDays, setSelectedDays] = useState<Set<string>>(
    new Set(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"])
  );
  const [suggestions, setSuggestions] = useState<EditableSuggestion[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function reset() {
    setStep("form");
    setStartDate(defaultStartDate());
    setDurationMinutesPerDay("60");
    setSelectedDays(new Set(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]));
    setSuggestions([]);
    setError(null);
  }

  function close(next: boolean) {
    if (!next) reset();
    onOpenChange(next);
  }

  function toggleDay(day: string) {
    setSelectedDays((prev) => {
      const next = new Set(prev);
      if (next.has(day)) next.delete(day);
      else next.add(day);
      return next;
    });
  }

  async function handlePreview() {
    setError(null);
    const minutes = Number(durationMinutesPerDay);
    if (!Number.isFinite(minutes) || minutes < 5) {
      setError("Duration must be at least 5 minutes.");
      return;
    }
    if (selectedDays.size === 0) {
      setError("Choose at least one available day.");
      return;
    }
    setSubmitting(true);
    try {
      const response = await previewStudyPlan({
        startDate,
        availableDays: Array.from(selectedDays),
        durationMinutesPerDay: minutes,
      });
      setSuggestions(
        response.sessions.map((s) => ({ ...s, time: "18:00", included: true }))
      );
      setStep("preview");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSave() {
    setError(null);
    const toSave = suggestions.filter((s) => s.included);
    if (toSave.length === 0) {
      setError("Include at least one day to save a plan.");
      return;
    }
    setSubmitting(true);
    try {
      const response = await saveStudyPlan({
        sessions: toSave.map((s) => ({
          resourceId: s.resource?.id ?? null,
          topic: s.topic,
          scheduledStart: new Date(`${s.date}T${s.time}`).toISOString(),
          durationMinutes: s.durationMinutes,
        })),
      });
      onSaved(response.created.length, response.skipped.length);
      close(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function updateSuggestion(index: number, patch: Partial<EditableSuggestion>) {
    setSuggestions((prev) => prev.map((s, i) => (i === index ? { ...s, ...patch } : s)));
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Generate study plan</DialogTitle>
          <DialogDescription>
            {step === "form"
              ? "We'll suggest one session per available day, prioritized by your roadmap."
              : "Review and adjust before saving — nothing is scheduled yet."}
          </DialogDescription>
        </DialogHeader>

        {error ? <p className="text-sm text-destructive">{error}</p> : null}

        {step === "form" ? (
          <div className="space-y-3">
            <FormField label="Start date" htmlFor="plan-start">
              <Input
                id="plan-start"
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                disabled={submitting}
              />
            </FormField>
            <FormField label="Study duration per day (minutes)" htmlFor="plan-duration">
              <Input
                id="plan-duration"
                type="number"
                min={5}
                step={5}
                value={durationMinutesPerDay}
                onChange={(e) => setDurationMinutesPerDay(e.target.value)}
                disabled={submitting}
              />
            </FormField>
            <div className="space-y-1.5">
              <p className="text-sm font-medium text-foreground">Available days</p>
              <div className="flex flex-wrap gap-1.5">
                {DAYS.map((day) => (
                  <button
                    key={day.value}
                    type="button"
                    onClick={() => toggleDay(day.value)}
                    className={
                      "rounded-full border px-3 py-1 text-xs font-medium transition-colors " +
                      (selectedDays.has(day.value)
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-border bg-background text-muted-foreground hover:text-foreground")
                    }
                  >
                    {day.label}
                  </button>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="max-h-80 space-y-2 overflow-y-auto">
            {suggestions.map((s, index) => (
              <div key={`${s.date}-${index}`} className="flex items-center gap-2 rounded-lg border border-border p-2">
                <input
                  type="checkbox"
                  checked={s.included}
                  onChange={(e) => updateSuggestion(index, { included: e.target.checked })}
                  className="size-4 shrink-0"
                  aria-label={`Include ${s.date}`}
                />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium text-foreground">
                    {new Date(`${s.date}T00:00:00`).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric" })}
                    {" — "}
                    {topicLabel(s.topic)}
                  </p>
                  {s.resource ? <p className="truncate text-xs text-muted-foreground">{s.resource.title}</p> : null}
                </div>
                <Input
                  type="time"
                  value={s.time}
                  onChange={(e) => updateSuggestion(index, { time: e.target.value })}
                  disabled={!s.included || submitting}
                  className="w-24 shrink-0"
                />
                <Input
                  type="number"
                  min={5}
                  step={5}
                  value={s.durationMinutes}
                  onChange={(e) => updateSuggestion(index, { durationMinutes: Number(e.target.value) })}
                  disabled={!s.included || submitting}
                  className="w-16 shrink-0"
                />
              </div>
            ))}
          </div>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => close(false)} disabled={submitting}>
            Cancel
          </Button>
          {step === "form" ? (
            <Button type="button" onClick={handlePreview} disabled={submitting}>
              {submitting ? "Generating..." : "Preview Plan"}
            </Button>
          ) : (
            <>
              <Button type="button" variant="secondary" onClick={() => setStep("form")} disabled={submitting}>
                Back
              </Button>
              <Button type="button" onClick={handleSave} disabled={submitting}>
                {submitting ? "Saving..." : "Save Plan"}
              </Button>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
