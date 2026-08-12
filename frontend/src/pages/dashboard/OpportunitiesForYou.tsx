import { Briefcase } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { ROUTES } from "@/lib/routes";

/** Always the empty state — there is no marketplace/opportunity backend yet, so nothing here is
 * real data a student could actually apply to. */
export function OpportunitiesForYou() {
  const navigate = useNavigate();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Opportunities for you</CardTitle>
        <button
          type="button"
          onClick={() => navigate(ROUTES.marketplace)}
          className="text-sm font-medium text-primary hover:underline"
        >
          View all
        </button>
      </CardHeader>
      <CardContent>
        <EmptyState icon={Briefcase} title="Opportunities will appear here as you build your profile." />
      </CardContent>
    </Card>
  );
}
