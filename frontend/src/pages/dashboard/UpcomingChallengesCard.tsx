import { Trophy } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { ROUTES } from "@/lib/routes";

/** Always the empty state — StuDen has no Challenges system at all yet (not even a package),
 * so this shows a clean empty state rather than fake challenge data. */
export function UpcomingChallengesCard() {
  const navigate = useNavigate();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Upcoming Challenges</CardTitle>
        <button
          type="button"
          onClick={() => navigate(ROUTES.challenges)}
          className="text-sm font-medium text-primary hover:underline"
        >
          View all
        </button>
      </CardHeader>
      <CardContent>
        <EmptyState icon={Trophy} title="No challenges yet" description="Check back soon." />
      </CardContent>
    </Card>
  );
}
