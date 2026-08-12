import { ArrowRight, FolderKanban } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import { ProjectCard } from "@/components/shared/ProjectCard";
import type { ProjectResponse } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";

interface YourProjectsCardProps {
  projects: ProjectResponse[];
}

export function YourProjectsCard({ projects }: YourProjectsCardProps) {
  const navigate = useNavigate();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Your Projects</CardTitle>
        {projects.length > 0 ? (
          <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.projects)}>
            View all <ArrowRight />
          </Button>
        ) : null}
      </CardHeader>
      <CardContent>
        {projects.length === 0 ? (
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
        ) : (
          <div className="grid grid-cols-1 gap-3">
            {projects.slice(0, 3).map((project) => (
              <ProjectCard key={project.id} project={project} href={ROUTES.projects} />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
