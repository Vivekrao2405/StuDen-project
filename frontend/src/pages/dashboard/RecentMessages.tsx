import { MessageCircle } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { ROUTES } from "@/lib/routes";

/** Always the empty state — there is no messaging backend yet, so there are no real
 * conversations to preview. */
export function RecentMessages() {
  const navigate = useNavigate();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Recent Messages</CardTitle>
        <button
          type="button"
          onClick={() => navigate(ROUTES.messages)}
          className="text-sm font-medium text-primary hover:underline"
        >
          View all
        </button>
      </CardHeader>
      <CardContent>
        <EmptyState icon={MessageCircle} title="No messages yet" />
      </CardContent>
    </Card>
  );
}
