import { Inbox } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { listMyServiceRequests } from "@/lib/api/endpoints/serviceRequests";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { ServiceRequestCard } from "@/pages/requests/ServiceRequestCard";

const PREVIEW_COUNT = 2;

/** Own useAsync call rather than folding into DashboardPage's Promise.all — keeps this card's
 * fetch independent of (and non-blocking for) the rest of the dashboard's initial load. */
export function ServiceRequestsCard() {
  const navigate = useNavigate();
  const { data, loading } = useAsync(listMyServiceRequests, []);

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>My Requests</CardTitle>
        <button
          type="button"
          onClick={() => navigate(ROUTES.serviceRequests)}
          className="text-sm font-medium text-primary hover:underline"
        >
          View all
        </button>
      </CardHeader>
      <CardContent>
        {loading ? null : data && data.length > 0 ? (
          <div className="space-y-2">
            {data.slice(0, PREVIEW_COUNT).map((request) => (
              <ServiceRequestCard key={request.id} request={request} perspective="sent" />
            ))}
          </div>
        ) : (
          <EmptyState icon={Inbox} title="No requests sent yet" />
        )}
      </CardContent>
    </Card>
  );
}
