import { Package } from "lucide-react";
import { useEffect, useState } from "react";

import { SegmentedControl } from "@/components/ui/segmented-control";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { listMyOrders } from "@/lib/api/endpoints/orders";
import { getMyPortfolio } from "@/lib/api/endpoints/portfolio";
import type { OrderStatus } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { OrderCard } from "@/pages/orders/OrderCard";
import { ServiceRequestsSkeleton } from "@/pages/requests/ServiceRequestsSkeleton";

type Perspective = "requester" | "provider";
type StatusFilter = "ALL" | OrderStatus;

const TAB_OPTIONS: { value: Perspective; label: string }[] = [
  { value: "requester", label: "My Orders" },
  { value: "provider", label: "My Work" },
];

const STATUS_FILTER_OPTIONS: { value: StatusFilter; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "WORK_SUBMITTED", label: "Submitted" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
];

export function OrdersPage() {
  const [perspective, setPerspective] = useState<Perspective>("requester");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const { data, error, loading, refetch } = useAsync(
    () => listMyOrders(statusFilter === "ALL" ? undefined : statusFilter),
    [statusFilter]
  );

  // Same "compare against my own portfolio slug" technique as ServiceRequestDetailPage — the
  // response never exposes the viewer's own user id/role directly, so this is how the page tells
  // which side of each order the viewer is on. A viewer with no portfolio can never be a
  // provider (publishing a service requires one), so ownSlug staying null just means every order
  // resolves to the requester side, which is correct.
  const [ownSlug, setOwnSlug] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false;
    getMyPortfolio()
      .then((portfolio) => {
        if (!cancelled) setOwnSlug(portfolio.publicSlug);
      })
      .catch(() => {
        if (!cancelled) setOwnSlug(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const visible = (data ?? []).filter((order) => {
    const isProviderRow = ownSlug != null && order.providerSlug === ownSlug;
    return perspective === "provider" ? isProviderRow : !isProviderRow;
  });

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Orders</h1>
        <p className="text-sm text-muted-foreground">Track work you've requested and work you're delivering.</p>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <SegmentedControl value={perspective} onChange={setPerspective} options={TAB_OPTIONS} />
        <SegmentedControl value={statusFilter} onChange={setStatusFilter} options={STATUS_FILTER_OPTIONS} />
      </div>

      {loading ? (
        <ServiceRequestsSkeleton />
      ) : error ? (
        <ErrorState message={error.message} onRetry={refetch} />
      ) : visible.length > 0 ? (
        <div className="space-y-3">
          {visible.map((order) => (
            <OrderCard key={order.id} order={order} perspective={perspective} />
          ))}
        </div>
      ) : (
        <EmptyState
          icon={Package}
          title={statusFilter !== "ALL" ? "No orders match this filter" : "No active orders yet"}
          description={
            statusFilter !== "ALL"
              ? undefined
              : "Orders are created automatically once a service request is accepted."
          }
        />
      )}
    </div>
  );
}
