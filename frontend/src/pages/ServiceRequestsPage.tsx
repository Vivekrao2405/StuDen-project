import { Inbox } from "lucide-react";
import { useState } from "react";

import { SegmentedControl } from "@/components/ui/segmented-control";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { listIncomingServiceRequests, listMyServiceRequests } from "@/lib/api/endpoints/serviceRequests";
import type { ServiceRequestStatus } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ServiceRequestCard } from "@/pages/requests/ServiceRequestCard";
import { ServiceRequestsSkeleton } from "@/pages/requests/ServiceRequestsSkeleton";

type Tab = "SENT" | "RECEIVED";
type StatusFilter = "ALL" | Extract<ServiceRequestStatus, "PENDING" | "ACCEPTED" | "REJECTED">;

const TAB_OPTIONS: { value: Tab; label: string }[] = [
  { value: "SENT", label: "Sent" },
  { value: "RECEIVED", label: "Received" },
];

const STATUS_FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "ACCEPTED", label: "Accepted" },
  { value: "REJECTED", label: "Rejected" },
];

export function ServiceRequestsPage() {
  const [tab, setTab] = useState<Tab>("SENT");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const { data, error, loading, refetch } = useAsync(
    () => (tab === "SENT" ? listMyServiceRequests() : listIncomingServiceRequests()),
    [tab]
  );

  const filtered = data?.filter((r) => statusFilter === "ALL" || r.status === statusFilter) ?? [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Requests</h1>
        <p className="text-sm text-muted-foreground">Service requests you've sent and received.</p>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <SegmentedControl value={tab} onChange={setTab} options={TAB_OPTIONS} />
        <SegmentedControl value={statusFilter} onChange={setStatusFilter} options={STATUS_FILTER_OPTIONS} />
      </div>

      {loading ? (
        <ServiceRequestsSkeleton />
      ) : error ? (
        <ErrorState message={error.message} onRetry={refetch} />
      ) : filtered.length > 0 ? (
        <div className="space-y-3">
          {filtered.map((request) => (
            <ServiceRequestCard key={request.id} request={request} perspective={tab === "SENT" ? "sent" : "received"} />
          ))}
        </div>
      ) : (
        <EmptyState
          icon={Inbox}
          title={
            statusFilter !== "ALL"
              ? "No requests match this filter"
              : tab === "SENT"
                ? "No requests sent yet"
                : "No requests received yet"
          }
          description={
            statusFilter !== "ALL"
              ? undefined
              : tab === "SENT"
                ? "Requests you send to service providers will show up here."
                : "Requests other students send for your services will show up here."
          }
        />
      )}
    </div>
  );
}
