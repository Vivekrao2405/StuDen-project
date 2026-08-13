import { Check, X } from "lucide-react";

import type { OrderResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface TimelineEntry {
  label: string;
  detail?: string;
  at: string;
  variant?: "default" | "destructive";
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString("en-IN", { day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit" });
}

interface OrderTimelineProps {
  order: OrderResponse;
}

// Computed entirely client-side from OrderResponse's own timestamps — no separate activity-log
// entity. requestAcceptedAt is kept as its own entry even though it lands moments before
// createdAt (this order is created directly at IN_PROGRESS), so the timeline reads honestly as
// "request accepted" -> "work started" rather than merging the two.
export function OrderTimeline({ order }: OrderTimelineProps) {
  const entries: TimelineEntry[] = [];
  if (order.requestAcceptedAt) {
    entries.push({ label: "Request accepted", at: order.requestAcceptedAt });
  }
  entries.push({ label: "Work started", at: order.createdAt });
  if (order.submittedAt) {
    entries.push({ label: "Work submitted", at: order.submittedAt });
  }
  if (order.completedAt) {
    entries.push({ label: "Marked completed", at: order.completedAt });
  }
  if (order.cancelledAt) {
    entries.push({
      label: "Order cancelled",
      detail: order.cancellationReason ?? undefined,
      at: order.cancelledAt,
      variant: "destructive",
    });
  }

  return (
    <ol className="space-y-3">
      {entries.map((entry, i) => (
        <li key={i} className="flex gap-3">
          <span
            className={cn(
              "mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-full",
              entry.variant === "destructive" ? "bg-destructive/10 text-destructive" : "bg-primary/10 text-primary"
            )}
          >
            {entry.variant === "destructive" ? <X className="size-3" /> : <Check className="size-3" />}
          </span>
          <div className="min-w-0">
            <p className="text-sm font-medium text-foreground">{entry.label}</p>
            {entry.detail ? <p className="text-xs text-muted-foreground">&ldquo;{entry.detail}&rdquo;</p> : null}
            <p className="text-xs text-muted-foreground">{formatDateTime(entry.at)}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}
