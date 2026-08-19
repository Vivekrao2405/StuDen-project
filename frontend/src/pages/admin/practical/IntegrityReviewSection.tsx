import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { getIntegrityTimeline, overrideIntegrity } from "@/lib/api/endpoints/adminPracticalAssessments";
import type { AdminIntegrityTimelineEntry, IntegrityStatus, IntegritySummary } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { INTEGRITY_STATUS_LABEL, integrityStatusBadgeVariant } from "@/pages/practical/practicalDisplay";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";

const OVERRIDE_OPTIONS: IntegrityStatus[] = ["CLEAN", "LOW_CONCERN", "REVIEW", "HIGH_CONCERN", "INVALIDATED"];

function formatTimestamp(iso: string) {
  return new Date(iso).toLocaleString();
}

// Phase 7.6 admin review — score/status/count grid + a merged chronological timeline (lifecycle
// + execution history + integrity events, all server-assembled) + a manual override control.
// Deliberately does not render deduction values or scoring internals (those stay backend-only).
export function IntegrityReviewSection({ attemptId, integrity, onOverridden }: {
  attemptId: string;
  integrity: IntegritySummary;
  onOverridden: () => void;
}) {
  const toast = useToast();
  const [showTimeline, setShowTimeline] = useState(false);
  const timeline = useAsync<AdminIntegrityTimelineEntry[]>(
    () => (showTimeline ? getIntegrityTimeline(attemptId) : Promise.resolve([])),
    [attemptId, showTimeline]
  );

  const [overrideOpen, setOverrideOpen] = useState(false);
  const [overrideStatus, setOverrideStatus] = useState<IntegrityStatus>("REVIEW");
  const [overrideReason, setOverrideReason] = useState("");
  const [submittingOverride, setSubmittingOverride] = useState(false);

  async function handleOverride() {
    if (!overrideReason.trim()) {
      toast.error("A reason is required to override the integrity status.");
      return;
    }
    setSubmittingOverride(true);
    try {
      await overrideIntegrity(attemptId, { status: overrideStatus, reason: overrideReason.trim() });
      toast.success("Integrity status overridden.");
      setOverrideOpen(false);
      setOverrideReason("");
      onOverridden();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmittingOverride(false);
    }
  }

  const effectiveStatus = integrity.effectiveStatus ?? integrity.integrityStatus;

  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">Integrity</h2>
        {effectiveStatus ? (
          <Badge variant={integrityStatusBadgeVariant(effectiveStatus)}>{INTEGRITY_STATUS_LABEL[effectiveStatus]}</Badge>
        ) : null}
      </div>

      {integrity.overridden && effectiveStatus ? (
        <p className="rounded-lg border border-border bg-muted/30 p-2 text-xs text-muted-foreground">
          Manually overridden to <span className="font-medium text-foreground">{INTEGRITY_STATUS_LABEL[effectiveStatus]}</span> by{" "}
          {integrity.overrideByName} — "{integrity.overrideReason}"
        </p>
      ) : null}

      <div className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4">
        <Stat label="Score" value={integrity.integrityScore ?? "—"} />
        <Stat label="Events" value={integrity.totalEvents} />
        <Stat label="Suspicious" value={integrity.suspiciousEvents} />
        <Stat label="Critical" value={integrity.criticalEvents} />
        <Stat label="Tab switches" value={integrity.tabSwitchCount} />
        <Stat label="Copy attempts" value={integrity.copyAttemptCount} />
        <Stat label="Paste attempts" value={integrity.pasteAttemptCount} />
        <Stat label="Cut attempts" value={integrity.cutAttemptCount} />
        <Stat label="Fullscreen exits" value={integrity.fullscreenExitCount} />
        <Stat label="Multiple sessions" value={integrity.multipleSessionCount} />
        <Stat label="Runs" value={integrity.runCount} />
        <Stat label="Submissions" value={integrity.submissionCount} />
      </div>
      {integrity.firstSuccessfulCompilationAt ? (
        <p className="text-xs text-muted-foreground">
          First successful compilation: {formatTimestamp(integrity.firstSuccessfulCompilationAt)}
        </p>
      ) : null}

      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => setShowTimeline((v) => !v)}>
          {showTimeline ? "Hide timeline" : "Show timeline"}
        </Button>
        <Button variant="outline" size="sm" onClick={() => setOverrideOpen((v) => !v)}>
          {overrideOpen ? "Cancel override" : "Manual override"}
        </Button>
      </div>

      {showTimeline ? (
        <div className="max-h-80 space-y-1.5 overflow-y-auto rounded-lg border border-border bg-muted/30 p-3">
          {timeline.loading ? <p className="text-xs text-muted-foreground">Loading timeline...</p> : null}
          {timeline.data?.map((entry, i) => (
            <div key={i} className="flex gap-3 text-xs">
              <span className="w-36 shrink-0 text-muted-foreground">{formatTimestamp(entry.timestamp)}</span>
              <span className="text-foreground">
                {entry.label}
                {entry.detail ? <span className="text-muted-foreground"> — {entry.detail}</span> : null}
              </span>
            </div>
          ))}
          {timeline.data?.length === 0 && !timeline.loading ? (
            <p className="text-xs text-muted-foreground">No timeline events.</p>
          ) : null}
        </div>
      ) : null}

      {overrideOpen ? (
        <div className="space-y-2 rounded-lg border border-border p-3">
          <label className="text-xs font-medium text-foreground" htmlFor="override-status">
            Status
          </label>
          <select
            id="override-status"
            value={overrideStatus}
            onChange={(e) => setOverrideStatus(e.target.value as IntegrityStatus)}
            className={QB_SELECT_CLASS}
          >
            {OVERRIDE_OPTIONS.map((s) => (
              <option key={s} value={s}>
                {INTEGRITY_STATUS_LABEL[s]}
              </option>
            ))}
          </select>
          <label className="text-xs font-medium text-foreground" htmlFor="override-reason">
            Reason (required)
          </label>
          <Textarea id="override-reason" rows={2} value={overrideReason} onChange={(e) => setOverrideReason(e.target.value)} />
          <Button size="sm" onClick={handleOverride} disabled={submittingOverride}>
            {submittingOverride ? "Saving..." : "Save Override"}
          </Button>
        </div>
      ) : null}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className="font-medium text-foreground">{value}</p>
    </div>
  );
}
