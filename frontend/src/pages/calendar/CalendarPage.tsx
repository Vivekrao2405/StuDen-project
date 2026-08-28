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
import { localDateKey, periodLabel, rangeForView, shiftAnchor, type CalendarView } from "@/pages/calendar/calendarRange";
import { EditSessionDialog } from "@/pages/calendar/EditSessionDialog";
import { StudyPlanDialog } from "@/pages/calendar/StudyPlanDialog";

const VIEW_OPTIONS: { value: CalendarView; label: string }[] = [
  { value: "day", label: "Day" },
  { value: "week", label: "Week" },
  { value: "month", label: "Month" },
];

function statusBadgeVariant(status: LearningSession["status"]): "default" | "secondary" | "outline" {
  if (status === "COMPLETED") return "default";
  if (status === "CANCELLED") return "outline";
  return "secondary";
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

  return (
    <div className="flex flex-wrap items-center gap-3 py-3">
      <div className="w-20 shrink-0 text-sm font-medium text-foreground">{formatSessionTime(session.scheduledStart)}</div>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="truncate text-sm font-semibold text-foreground">{title}</p>
          <Badge variant={statusBadgeVariant(session.status)}>{session.status}</Badge>
        </div>
        <p className="flex items-center gap-1 text-xs text-muted-foreground">
          {Icon ? <Icon className="size-3.5" /> : null}
          {session.resource ? `${session.resource.title} · ` : ""}
          {formatDurationMinutes(session.durationMinutes)}
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
      </div>
    </div>
  );
}

export function CalendarPage() {
  const [view, setView] = useState<CalendarView>("week");
  const [anchor, setAnchor] = useState(() => new Date());
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

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Calendar</h1>
          <p className="text-sm text-muted-foreground">Schedule and track your study sessions.</p>
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
          <span className="min-w-40 text-center text-sm font-medium text-foreground">{periodLabel(view, anchor)}</span>
          <Button size="icon-sm" variant="outline" aria-label="Next" onClick={() => setAnchor((a) => shiftAnchor(view, a, 1))}>
            <ChevronRight className="size-4" />
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setAnchor(new Date())}>
            Today
          </Button>
        </div>
        <SegmentedControl value={view} onChange={setView} options={VIEW_OPTIONS} />
      </div>

      {loading ? (
        <LoadingState label="Loading sessions..." />
      ) : error || !data ? (
        <ErrorState message={error?.message ?? "Couldn't load your calendar."} onRetry={refetch} />
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
