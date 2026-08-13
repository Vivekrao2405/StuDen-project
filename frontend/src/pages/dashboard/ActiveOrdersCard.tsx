import { Package } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { listMyOrders } from "@/lib/api/endpoints/orders";
import { getMyPortfolio } from "@/lib/api/endpoints/portfolio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { OrderCard } from "@/pages/orders/OrderCard";

const PREVIEW_COUNT = 2;

/** Own useAsync call rather than folding into DashboardPage's Promise.all — keeps this card's
 * fetch independent of (and non-blocking for) the rest of the dashboard's initial load, same
 * pattern as ServiceRequestsCard/RecentMessages. */
export function ActiveOrdersCard() {
  const navigate = useNavigate();
  const { data, loading } = useAsync(() => listMyOrders(), []);

  // Same slug-comparison technique as OrdersPage/OrderDetailPage — needed here too since each
  // preview card must know which side of that specific order the viewer is on.
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

  const active = (data ?? []).filter((o) => o.status === "IN_PROGRESS" || o.status === "WORK_SUBMITTED");

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Active Orders</CardTitle>
        <button
          type="button"
          onClick={() => navigate(ROUTES.orders)}
          className="text-sm font-medium text-primary hover:underline"
        >
          View all
        </button>
      </CardHeader>
      <CardContent>
        {loading ? null : active.length > 0 ? (
          <div className="space-y-2">
            {active.slice(0, PREVIEW_COUNT).map((order) => (
              <OrderCard
                key={order.id}
                order={order}
                perspective={ownSlug != null && order.providerSlug === ownSlug ? "provider" : "requester"}
              />
            ))}
          </div>
        ) : (
          <EmptyState icon={Package} title="No active orders yet" />
        )}
      </CardContent>
    </Card>
  );
}
