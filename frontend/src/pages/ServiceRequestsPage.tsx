import { Inbox } from "lucide-react";
import { useState } from "react";

import { SegmentedControl } from "@/components/ui/segmented-control";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { listIncomingServiceRequests, listMyServiceRequests } from "@/lib/api/endpoints/serviceRequests";
import { useAsync } from "@/lib/hooks/useAsync";
import { ServiceRequestCard } from "@/pages/requests/ServiceRequestCard";
import { ServiceRequestsSkeleton } from "@/pages/requests/ServiceRequestsSkeleton";

type Tab = "SENT" | "RECEIVED";

const TAB_OPTIONS: { value: Tab; label: string }[] = [
  { value: "SENT", label: "Sent" },
  { value: "RECEIVED", label: "Received" },
];

export function ServiceRequestsPage() {
  const [tab, setTab] = useState<Tab>("SENT");
  const { data, error, loading, refetch } = useAsync(
    () => (tab === "SENT" ? listMyServiceRequests() : listIncomingServiceRequests()),
    [tab]
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Requests</h1>
        <p className="text-sm text-muted-foreground">Service requests you've sent and received.</p>
      </div>

      <SegmentedControl value={tab} onChange={setTab} options={TAB_OPTIONS} />

      {loading ? (
        <ServiceRequestsSkeleton />
      ) : error ? (
        <ErrorState message={error.message} onRetry={refetch} />
      ) : data && data.length > 0 ? (
        <div className="space-y-3">
          {data.map((request) => (
            <ServiceRequestCard key={request.id} request={request} perspective={tab === "SENT" ? "sent" : "received"} />
          ))}
        </div>
      ) : (
        <EmptyState
          icon={Inbox}
          title={tab === "SENT" ? "No requests sent yet" : "No requests received yet"}
          description={
            tab === "SENT"
              ? "Requests you send to service providers will show up here."
              : "Requests other students send for your services will show up here."
          }
        />
      )}
    </div>
  );
}
