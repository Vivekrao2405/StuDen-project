import { FolderKanban } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { ROUTES } from "@/lib/routes";

/** Always the empty state — StuDen has no Projects/Showcase backend yet (confirmed empty stub
 * package), so there is no real project data to show here. */
export function YourProjectsCard() {
  const navigate = useNavigate();

  return (
    <Card>
      <CardHeader>
        <CardTitle>Your Projects</CardTitle>
      </CardHeader>
      <CardContent>
        <EmptyState
          icon={FolderKanban}
          title="Showcase your work"
          description="Add your first project."
          action={
            <Button size="sm" variant="outline" onClick={() => navigate(ROUTES.projects)}>
              Add your first project
            </Button>
          }
        />
      </CardContent>
    </Card>
  );
}
