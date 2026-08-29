export type CalendarView = "month" | "week" | "agenda";

const AGENDA_SPAN_DAYS = 30;

export function localDateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

// [start, end) in the viewer's local time zone for the given view/anchor — backend range queries
// are Instant-based, so these are converted to ISO strings at the call site. "agenda" is a flat
// rolling window (today through +30 days) rather than a calendar-aligned period.
export function rangeForView(view: CalendarView, anchor: Date): [Date, Date] {
  if (view === "week") {
    // Sunday-start, matching the Month grid below (and the mockup's Sun..Sat header row).
    const start = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() - anchor.getDay());
    const end = new Date(start);
    end.setDate(end.getDate() + 7);
    return [start, end];
  }
  if (view === "agenda") {
    const start = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate());
    const end = new Date(start);
    end.setDate(end.getDate() + AGENDA_SPAN_DAYS);
    return [start, end];
  }
  const start = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const end = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 1);
  return [start, end];
}

// The Month grid always shows full leading/trailing weeks so every row has 7 days (Sun-start,
// matching the mockup's header row) — a wider range than rangeForView("month", ...), which stays
// exactly the calendar month for the session-fetch query.
export function monthGridRange(anchor: Date): [Date, Date] {
  const [monthStart, monthEnd] = rangeForView("month", anchor);
  const gridStart = new Date(monthStart);
  gridStart.setDate(gridStart.getDate() - monthStart.getDay());

  const lastOfMonth = new Date(monthEnd);
  lastOfMonth.setDate(lastOfMonth.getDate() - 1);
  const trailOffset = 6 - lastOfMonth.getDay();
  const gridEnd = new Date(monthEnd);
  gridEnd.setDate(gridEnd.getDate() + trailOffset);

  return [gridStart, gridEnd];
}

export function shiftAnchor(view: CalendarView, anchor: Date, direction: 1 | -1): Date {
  const next = new Date(anchor);
  if (view === "week") next.setDate(next.getDate() + 7 * direction);
  else if (view === "agenda") next.setDate(next.getDate() + AGENDA_SPAN_DAYS * direction);
  else next.setMonth(next.getMonth() + direction);
  return next;
}

export function periodLabel(view: CalendarView, anchor: Date): string {
  if (view === "week") {
    const [start, end] = rangeForView(view, anchor);
    const last = new Date(end);
    last.setDate(last.getDate() - 1);
    const fmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" });
    return `${fmt.format(start)} – ${fmt.format(last)}, ${start.getFullYear()}`;
  }
  if (view === "agenda") {
    const [start, end] = rangeForView(view, anchor);
    const last = new Date(end);
    last.setDate(last.getDate() - 1);
    const fmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" });
    return `Next 30 days · ${fmt.format(start)} – ${fmt.format(last)}`;
  }
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" }).format(anchor);
}
