import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import type { PracticalAssessmentSummary } from "@/lib/api/practicalTypes";
import { ROUTES } from "@/lib/routes";
import { difficultyBadgeVariant, PRACTICAL_TYPE_LABEL } from "@/pages/practical/practicalDisplay";

export function PracticalAssessmentCard({ assessment }: { assessment: PracticalAssessmentSummary }) {
  const navigate = useNavigate();

  return (
    <Card className="flex flex-col">
      <CardContent className="flex flex-1 flex-col gap-3 pt-4">
        <div>
          <h3 className="text-base font-semibold text-foreground">{assessment.title}</h3>
          <p className="text-xs text-muted-foreground">{assessment.skillName}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">{PRACTICAL_TYPE_LABEL[assessment.practicalType]}</Badge>
          <Badge variant={difficultyBadgeVariant(assessment.difficulty)}>{assessment.difficulty}</Badge>
        </div>
        <div className="mt-auto flex items-center justify-between gap-3 pt-2">
          <span className="text-xs font-medium text-muted-foreground">{assessment.timeLimitMinutes} min</span>
          <Button size="sm" onClick={() => navigate(ROUTES.practicalAssessmentDetail(assessment.id))}>
            View
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
