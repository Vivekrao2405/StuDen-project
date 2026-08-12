import { Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { EmptyState } from "@/components/shared/EmptyState";
import type { PortfolioSkillResponse, SkillLevel } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";

const MAX_DISPLAYED_SKILLS = 5;

const LEVEL_LABEL: Record<SkillLevel, string> = {
  BEGINNER: "Beginner",
  INTERMEDIATE: "Intermediate",
  EXPERT: "Expert",
};

const LEVEL_PERCENT: Record<SkillLevel, number> = {
  BEGINNER: 30,
  INTERMEDIATE: 65,
  EXPERT: 95,
};

interface YourSkillsCardProps {
  skills: PortfolioSkillResponse[];
  onLevelChange: (skillId: string, level: SkillLevel) => void;
}

export function YourSkillsCard({ skills, onLevelChange }: YourSkillsCardProps) {
  const navigate = useNavigate();
  const visible = skills.slice(0, MAX_DISPLAYED_SKILLS);

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Your Skills</CardTitle>
        {skills.length > 0 ? (
          <button
            type="button"
            onClick={() => navigate(ROUTES.profile)}
            className="text-sm font-medium text-primary hover:underline"
          >
            View all
          </button>
        ) : null}
      </CardHeader>
      <CardContent>
        {visible.length === 0 ? (
          <EmptyState
            icon={Sparkles}
            title="No skills added yet"
            action={
              <Button size="sm" onClick={() => navigate(ROUTES.profile)}>
                Add your first skill
              </Button>
            }
          />
        ) : (
          <ul className="space-y-3">
            {visible.map((skill) => (
              <li key={skill.id}>
                <div className="mb-1 flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-medium text-foreground">{skill.name}</span>
                  <select
                    value={skill.level}
                    onChange={(e) => onLevelChange(skill.id, e.target.value as SkillLevel)}
                    aria-label={`${skill.name} proficiency level`}
                    className="shrink-0 rounded-md border-none bg-transparent text-xs font-medium text-muted-foreground outline-none"
                  >
                    {(Object.keys(LEVEL_LABEL) as SkillLevel[]).map((level) => (
                      <option key={level} value={level}>
                        {LEVEL_LABEL[level]}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full bg-primary transition-all"
                    style={{ width: `${LEVEL_PERCENT[skill.level]}%` }}
                  />
                </div>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
