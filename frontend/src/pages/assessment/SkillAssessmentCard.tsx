import { useNavigate } from "react-router-dom";

import { SkillIcon } from "@/components/shared/SkillIcon";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { AssessableSkillResponse } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";

interface SkillAssessmentCardProps {
  skill: AssessableSkillResponse;
}

export function SkillAssessmentCard({ skill }: SkillAssessmentCardProps) {
  const navigate = useNavigate();

  return (
    <Card className="flex flex-col">
      <CardContent className="flex flex-1 flex-col gap-3 pt-4">
        <div className="flex items-center gap-3">
          <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-accent">
            <SkillIcon iconSlug={skill.iconSlug} iconType={skill.iconType} className="size-6" />
          </span>
          <div className="min-w-0">
            <h3 className="truncate text-base font-semibold text-foreground">{skill.name}</h3>
            <p className="text-xs text-muted-foreground">Test your {skill.name} knowledge</p>
          </div>
        </div>

        <div className="mt-auto flex items-center justify-between gap-3 pt-2">
          {skill.assessable ? (
            <>
              <span className="text-xs font-medium text-muted-foreground">{skill.publishedQuestionCount} Questions</span>
              <Button
                size="sm"
                onClick={() => navigate(ROUTES.skillAssessmentDetail(skill.skillId), { state: { skill } })}
              >
                Start Assessment
              </Button>
            </>
          ) : (
            <>
              <span className="text-xs font-medium text-muted-foreground">Assessment coming soon</span>
              <Button size="sm" disabled>
                Coming Soon
              </Button>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
