import { FolderKanban, Lock, Pencil, Trash2 } from "lucide-react";
import { Link } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SkillChip } from "@/components/shared/SkillChip";
import type { ProjectVisibility, SkillResponse } from "@/lib/api/types";

/** Structurally compatible with both the owner-facing ProjectResponse and the public
 * PublicProjectSummary, so this one card renders both without a type union at call sites. */
export interface ProjectCardData {
  id: string;
  title: string;
  shortDescription: string | null;
  coverImageUrl: string | null;
  skills: SkillResponse[];
  visibility?: ProjectVisibility;
}

interface ProjectCardProps {
  project: ProjectCardData;
  href?: string;
  onEdit?: () => void;
  onDelete?: () => void;
}

export function ProjectCard({ project, href, onEdit, onDelete }: ProjectCardProps) {
  const cover = (
    <div className="relative aspect-[4/3] w-full bg-muted">
      {project.coverImageUrl ? (
        <img src={project.coverImageUrl} alt="" className="h-full w-full object-cover" />
      ) : (
        <div className="flex h-full w-full items-center justify-center">
          <FolderKanban className="size-8 text-muted-foreground" />
        </div>
      )}
      {project.visibility === "PRIVATE" ? (
        <Badge variant="secondary" className="absolute left-2 top-2 gap-1">
          <Lock className="size-3" /> Private
        </Badge>
      ) : null}
    </div>
  );

  const body = (
    <div className="space-y-1 p-3 pb-0">
      <h3 className="line-clamp-1 font-medium text-foreground">{project.title}</h3>
      {project.shortDescription ? (
        <p className="line-clamp-2 text-sm text-muted-foreground">{project.shortDescription}</p>
      ) : null}
    </div>
  );

  return (
    <div className="flex flex-col overflow-hidden rounded-xl border border-border bg-card">
      {href ? (
        <Link to={href} className="block">
          {cover}
          {body}
        </Link>
      ) : (
        <>
          {cover}
          {body}
        </>
      )}

      <div className="flex flex-1 flex-col gap-2 p-3 pt-2">
        {project.skills.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {project.skills.slice(0, 4).map((skill) => (
              <SkillChip key={skill.id} skill={skill} variant="compact" />
            ))}
          </div>
        ) : null}

        {onEdit || onDelete ? (
          <div className="mt-auto flex justify-end gap-1 pt-1">
            {onEdit ? (
              <Button variant="ghost" size="icon-sm" aria-label="Edit project" onClick={onEdit}>
                <Pencil />
              </Button>
            ) : null}
            {onDelete ? (
              <Button variant="ghost" size="icon-sm" aria-label="Delete project" onClick={onDelete}>
                <Trash2 />
              </Button>
            ) : null}
          </div>
        ) : href ? (
          <Link to={href} className="mt-auto text-sm font-medium text-primary hover:underline">
            View Project →
          </Link>
        ) : null}
      </div>
    </div>
  );
}
