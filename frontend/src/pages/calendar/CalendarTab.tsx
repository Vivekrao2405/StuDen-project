import { CalendarPlus, Check, ChevronLeft, ChevronRight, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { completeSession, deleteSession, getSessions } from "@/lib/api/endpoints/calendar";
import type { LearningSession } from "@/lib/api/calendarTypes";
import { ApiError } from "@/lib/api/ApiError";
import { formatDurationMinutes, formatSessionTime } from "@/lib/format";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { resourceTypeIcon } from "@/pages/learning/resourceDisplay";
import {
  localDateKey,
  periodLabel,
  rangeForView,
  shiftAnchor,
  type CalendarView,
} from "@/pages/calendar/calendarRange";
import { MonthGrid } from "@/pages/calendar/MonthGrid";
import { SESSION_CATEGORY_LABEL, sessionCategoryAccent } from "@/pages/calendar/sessionCategoryDisplay";
import { EditSessionDialog } from "@/pages/calendar/EditSessionDialog";
import { StudyPlanDialog } from "@/pages/calendar/StudyPlanDialog";

const VIEW_OPTIONS: { value: CalendarView; label: string }[] = [
  { value: "month", label: "Month" },
  { value: "week", label: "Week" },
  { value: "agenda", label: "Agenda" },
];

function sessionEndTime(session: LearningSession): string {
  const end = new Date(new Date(session.scheduledStart).getTime() + session.durationMinutes * 60_000);
  return formatSessionTime(end.toISOString());
}

interface SessionRowProps {
  session: LearningSession;
  onComplete: (session: LearningSession) => void;
  onEdit: (session: LearningSession) => void;
  onDelete: (session: LearningSession) => void;
}

function SessionRow({ session, onComplete, onEdit, onDelete }: SessionRowProps) {
  const navigate = useNavigate();
  const Icon = session.resource ? resourceTypeIcon(session.resource.resourceType) : null;
  const title = session.topic ? session.topic[0].toUpperCase() + session.topic.slice(1) : (session.resource?.title ?? "Study session");
  const accent = sessionCategoryAccent(session.category);

  return (
    <div className="flex flex-wrap items-center gap-3 py-3">
      <span className={`flex size-9 shrink-0 items-center justify-center rounded-lg ${accent.iconBg}`}>
        {Icon ? <Icon className={`size-4 ${accent.iconText}`} /> : <span className={`size-2 rounded-full ${accent.dot}`} />}
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-semibold text-foreground">{title}</p>
          <Badge className={`${accent.badgeBg} ${accent.badgeText} border-transparent`}>
            {SESSION_CATEGORY_LABEL[session.category]}
          </Badge>
          {session.status !== "SCHEDULED" ? <Badge variant="outline">{session.status}</Badge> : null}
        </div>
        <p className="text-xs text-muted-foreground">
          {formatSessionTime(session.scheduledStart)} – {sessionEndTime(session)} · {formatDurationMinutes(session.durationMinutes)}
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-1.5">
        {session.resource ? (
          <Button size="sm" variant="outline" onClick={() => navigate(ROUTES.myLearningResourceDetail(session.resource!.id))}>
            Open
          </Button>
        ) : null}
        {session.status === "SCHEDULED" ? (
          <>
            <Button size="sm" variant="outline" onClick={() => onEdit(session)}>
              Edit
            </Button>
            <Button size="sm" onClick={() => onComplete(session)}>
              <Check className="size-3.5" /> Complete
            </Button>
          </>
        ) : null}
        <Button size="icon-sm" variant="ghost" aria-label="Delete session" onClick={() => onDelete(session)}>
          <Trash2 className="size-4 text-destructive" />
        </Button>
        <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
      </div>
    </div>
  );
}

function dayLabel(date: Date): string {
  const today = new Date();
  if (localDateKey(date) === localDateKey(today)) {
    return `Today · ${new Intl.DateTimeFormat("en-US", { day: "numeric", month: "long", year: "numeric" }).format(date)}`;
  }
  return new Intl.DateTimeFormat("en-US", { weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(date);
}

export function CalendarTab() {
  const [view, setView] = useState<CalendarView>("month");
  const [anchor, setAnchor] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => new Date());
  const [editTarget, setEditTarget] = useState<LearningSession | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<LearningSession | null>(null);
  const [studyPlanOpen, setStudyPlanOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);

  const [start, end] = useMemo(() => rangeForView(view, anchor), [view, anchor]);

  const { data, error, loading, refetch } = useAsync(
    () => getSessions(start.toISOString(), end.toISOString()),
    [start.getTime(), end.getTime()]
  );

  const grouped = useMemo(() => {
    const map = new Map<string, LearningSession[]>();
    for (const session of data ?? []) {
      const key = localDateKey(new Date(session.scheduledStart));
      const list = map.get(key) ?? [];
      list.push(session);
      map.set(key, list);
    }
    for (const list of map.values()) {
      list.sort((a, b) => new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime());
    }
    return [...map.entries()].sort(([a], [b]) => (a < b ? -1 : 1));
  }, [data]);

  const selectedDaySessions = useMemo(() => {
    const key = localDateKey(selectedDate);
    return (data ?? [])
      .filter((s) => localDateKey(new Date(s.scheduledStart)) === key)
      .sort((a, b) => new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime());
  }, [data, selectedDate]);

  async function handleComplete(session: LearningSession) {
    setActionError(null);
    try {
      await completeSession(session.id);
      refetch();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't complete this session.");
    }
  }

  async function handleDeleteConfirmed() {
    if (!deleteTarget) return;
    setActionError(null);
    try {
      await deleteSession(deleteTarget.id);
      refetch();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't delete this session.");
    } finally {
      setDeleteTarget(null);
    }
  }

  function handleViewChange(next: CalendarView) {
    setView(next);
    setAnchor(new Date());
    setSelectedDate(new Date());
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-foreground">Learning Calendar</h2>
          <p className="text-sm text-muted-foreground">Plan, track and stay consistent with your learning.</p>
        </div>
        <Button onClick={() => setStudyPlanOpen(true)}>
          <CalendarPlus className="size-4" /> Generate Study Plan
        </Button>
      </div>

      {banner ? <p className="rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700 dark:text-emerald-400">{banner}</p> : null}
      {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button size="icon-sm" variant="outline" aria-label="Previous" onClick={() => setAnchor((a) => shiftAnchor(view, a, -1))}>
            <ChevronLeft className="size-4" />
          </Button>
          <span className="min-w-32 text-center text-sm font-medium text-foreground sm:min-w-40">{periodLabel(view, anchor)}</span>
          <Button size="icon-sm" variant="outline" aria-label="Next" onClick={() => setAnchor((a) => shiftAnchor(view, a, 1))}>
            <ChevronRight className="size-4" />
          </Button>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => {
              setAnchor(new Date());
              setSelectedDate(new Date());
            }}
          >
            Today
          </Button>
        </div>
        <div className="max-w-full overflow-x-auto">
          <SegmentedControl value={view} onChange={handleViewChange} options={VIEW_OPTIONS} />
        </div>
      </div>

      {loading ? (
        <LoadingState label="Loading sessions..." />
      ) : error || !data ? (
        <ErrorState message={error?.message ?? "Couldn't load your calendar."} onRetry={refetch} />
      ) : view === "month" ? (
        <>
          <MonthGrid monthAnchor={anchor} sessions={data} selectedDate={selectedDate} onSelectDate={setSelectedDate} />
          <div>
            <h3 className="mb-2 text-sm font-semibold text-foreground">{dayLabel(selectedDate)}</h3>
            {selectedDaySessions.length === 0 ? (
              <EmptyState
                icon={CalendarPlus}
                title="Nothing scheduled"
                description="Schedule a session from your Roadmap, or generate a study plan to fill this day."
                action={<Button onClick={() => setStudyPlanOpen(true)}>Generate Study Plan</Button>}
              />
            ) : (
              <Card>
                <CardContent className="divide-y divide-border">
                  {selectedDaySessions.map((session) => (
                    <SessionRow
                      key={session.id}
                      session={session}
                      onComplete={handleComplete}
                      onEdit={setEditTarget}
                      onDelete={setDeleteTarget}
                    />
                  ))}
                </CardContent>
              </Card>
            )}
          </div>
        </>
      ) : grouped.length === 0 ? (
        <EmptyState
          icon={CalendarPlus}
          title="No sessions scheduled"
          description="Schedule a session from your Roadmap, or generate a study plan to fill this period."
          action={<Button onClick={() => setStudyPlanOpen(true)}>Generate Study Plan</Button>}
        />
      ) : (
        grouped.map(([dateKey, sessions]) => (
          <Card key={dateKey}>
            <CardContent className="divide-y divide-border">
              <p className="pb-1 text-sm font-semibold text-foreground">
                {new Date(`${dateKey}T00:00:00`).toLocaleDateString("en-US", {
                  weekday: "long",
                  month: "short",
                  day: "numeric",
                })}
              </p>
              {sessions.map((session) => (
                <SessionRow
                  key={session.id}
                  session={session}
                  onComplete={handleComplete}
                  onEdit={setEditTarget}
                  onDelete={setDeleteTarget}
                />
              ))}
            </CardContent>
          </Card>
        ))
      )}

      {editTarget ? (
        <EditSessionDialog
          session={editTarget}
          open
          onOpenChange={(open) => !open && setEditTarget(null)}
          onUpdated={() => {
            setEditTarget(null);
            refetch();
          }}
        />
      ) : null}

      <ConfirmDeleteDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        title="Delete this session?"
        description="This study session will be removed from your calendar."
        onConfirm={handleDeleteConfirmed}
      />

      <StudyPlanDialog
        open={studyPlanOpen}
        onOpenChange={setStudyPlanOpen}
        onSaved={(created, skipped) => {
          setBanner(
            `Added ${created} session${created === 1 ? "" : "s"} to your calendar` +
              (skipped > 0 ? ` (${skipped} skipped — already scheduled).` : ".")
          );
          refetch();
        }}
      />
    </div>
  );
}
