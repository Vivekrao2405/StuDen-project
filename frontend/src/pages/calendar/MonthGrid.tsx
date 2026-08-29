import type { LearningSession } from "@/lib/api/calendarTypes";
import { sessionCategoryAccent } from "@/pages/calendar/sessionCategoryDisplay";
import { localDateKey, monthGridRange } from "@/pages/calendar/calendarRange";
import { cn } from "@/lib/utils";

const WEEKDAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MAX_CHIPS_PER_DAY = 2;

interface MonthGridProps {
  monthAnchor: Date;
  sessions: LearningSession[];
  selectedDate: Date;
  onSelectDate: (date: Date) => void;
}

export function MonthGrid({ monthAnchor, sessions, selectedDate, onSelectDate }: MonthGridProps) {
  const [gridStart, gridEnd] = monthGridRange(monthAnchor);
  const byDay = new Map<string, LearningSession[]>();
  for (const session of sessions) {
    const key = localDateKey(new Date(session.scheduledStart));
    const list = byDay.get(key) ?? [];
    list.push(session);
    byDay.set(key, list);
  }

  const days: Date[] = [];
  for (const cursor = new Date(gridStart); cursor < gridEnd; cursor.setDate(cursor.getDate() + 1)) {
    days.push(new Date(cursor));
  }

  const today = new Date();
  const todayKey = localDateKey(today);
  const selectedKey = localDateKey(selectedDate);

  return (
    <div className="overflow-hidden rounded-xl border border-border">
      <div className="grid grid-cols-7 border-b border-border bg-muted/40">
        {WEEKDAY_LABELS.map((label) => (
          <div key={label} className="px-2 py-2 text-center text-xs font-medium text-muted-foreground">
            {label}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {days.map((date) => {
          const key = localDateKey(date);
          const inCurrentMonth = date.getMonth() === monthAnchor.getMonth();
          const daySessions = inCurrentMonth ? (byDay.get(key) ?? []) : [];
          const isToday = key === todayKey;
          const isSelected = key === selectedKey;
          const overflow = daySessions.length - MAX_CHIPS_PER_DAY;

          return (
            <button
              key={key}
              type="button"
              disabled={!inCurrentMonth}
              onClick={() => onSelectDate(date)}
              className={cn(
                "flex min-h-20 flex-col items-start gap-1 border-b border-r border-border p-1.5 text-left align-top transition-colors last:border-r-0 sm:min-h-24 sm:p-2",
                inCurrentMonth ? "bg-card hover:bg-muted/50" : "bg-muted/20",
                "disabled:cursor-default"
              )}
            >
              <span
                className={cn(
                  "flex size-6 shrink-0 items-center justify-center rounded-full text-xs font-medium",
                  !inCurrentMonth && "text-muted-foreground/40",
                  inCurrentMonth && !isToday && !isSelected && "text-foreground",
                  isToday && !isSelected && "bg-accent text-accent-foreground",
                  isSelected && "bg-primary text-primary-foreground"
                )}
              >
                {date.getDate()}
              </span>
              <div className="flex w-full min-w-0 flex-col gap-0.5">
                {daySessions.slice(0, MAX_CHIPS_PER_DAY).map((session) => {
                  const accent = sessionCategoryAccent(session.category);
                  const title = session.topic ?? session.resource?.title ?? "Session";
                  return (
                    <span key={session.id} className="flex min-w-0 items-center gap-1 text-[0.7rem] text-foreground">
                      <span className={cn("size-1.5 shrink-0 rounded-full", accent.dot)} />
                      <span className="truncate">{title}</span>
                    </span>
                  );
                })}
                {overflow > 0 ? <span className="text-[0.7rem] text-muted-foreground">+{overflow} more</span> : null}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
