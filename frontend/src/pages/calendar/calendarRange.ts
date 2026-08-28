export type CalendarView = "day" | "week" | "month";

export function localDateKey(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

// [start, end) in the viewer's local time zone for the given view/anchor — backend range queries
// are Instant-based, so these are converted to ISO strings at the call site.
export function rangeForView(view: CalendarView, anchor: Date): [Date, Date] {
  if (view === "day") {
    const start = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate());
    const end = new Date(start);
    end.setDate(end.getDate() + 1);
    return [start, end];
  }
  if (view === "week") {
    const day = anchor.getDay();
    const diffToMonday = (day + 6) % 7;
    const start = new Date(anchor.getFullYear(), anchor.getMonth(), anchor.getDate() - diffToMonday);
    const end = new Date(start);
    end.setDate(end.getDate() + 7);
    return [start, end];
  }
  const start = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const end = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 1);
  return [start, end];
}

export function shiftAnchor(view: CalendarView, anchor: Date, direction: 1 | -1): Date {
  const next = new Date(anchor);
  if (view === "day") next.setDate(next.getDate() + direction);
  else if (view === "week") next.setDate(next.getDate() + 7 * direction);
  else next.setMonth(next.getMonth() + direction);
  return next;
}

export function periodLabel(view: CalendarView, anchor: Date): string {
  if (view === "day") {
    return new Intl.DateTimeFormat("en-US", { weekday: "long", month: "long", day: "numeric", year: "numeric" }).format(
      anchor
    );
  }
  if (view === "week") {
    const [start, end] = rangeForView(view, anchor);
    const last = new Date(end);
    last.setDate(last.getDate() - 1);
    const fmt = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric" });
    return `${fmt.format(start)} – ${fmt.format(last)}, ${start.getFullYear()}`;
  }
  return new Intl.DateTimeFormat("en-US", { month: "long", year: "numeric" }).format(anchor);
}
