import { ArrowLeft, Clock, ListChecks } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { getPracticalAssessment, startPracticalAttempt } from "@/lib/api/endpoints/practicalAssessments";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { IntegrityRulesNotice } from "@/pages/practical/IntegrityRulesNotice";
import { difficultyBadgeVariant, PRACTICAL_TYPE_LABEL } from "@/pages/practical/practicalDisplay";

export function PracticalAssessmentDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const [starting, setStarting] = useState(false);
  const { data: assessment, error, loading, refetch } = useAsync(() => getPracticalAssessment(id), [id]);

  async function handleStart() {
    setStarting(true);
    try {
      const attempt = await startPracticalAttempt(id);
      navigate(ROUTES.practicalAttempt(attempt.id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't start this assessment. Please try again.");
    } finally {
      setStarting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading assessment..." />;
  }
  if (error || !assessment) {
    return <ErrorState title="Assessment not found" message="This practical assessment isn't available." onRetry={refetch} />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.skillAssessments}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Skill Assessments
      </Link>

      <Card>
        <CardContent className="space-y-6 pt-6">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{PRACTICAL_TYPE_LABEL[assessment.practicalType]}</Badge>
              <Badge variant={difficultyBadgeVariant(assessment.difficulty)}>{assessment.difficulty}</Badge>
            </div>
            <h1 className="mt-2 text-xl font-bold text-foreground">{assessment.title}</h1>
            <p className="text-sm text-muted-foreground">{assessment.skillName}</p>
          </div>

          <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
            <span className="inline-flex items-center gap-1.5">
              <Clock className="size-4" /> {assessment.timeLimitMinutes} minutes
            </span>
            {assessment.practicalType === "CODING" ? (
              <span className="inline-flex items-center gap-1.5">
                <ListChecks className="size-4" /> {assessment.supportedLanguages.length} languages supported
              </span>
            ) : null}
          </div>

          <div className="space-y-2">
            <h2 className="text-sm font-semibold text-foreground">Instructions</h2>
            <p className="whitespace-pre-wrap text-sm text-muted-foreground">{assessment.instructions}</p>
          </div>

          {/* The actual problem — statement, examples, constraints, test cases, starter code — is
              deliberately never fetched or shown here. It's only returned once a real attempt
              exists, via startPracticalAttempt/getPracticalAttempt (see PracticalAttemptPage). */}

          <IntegrityRulesNotice policy={assessment.integrityPolicy} />

          <Button className="w-full" size="lg" onClick={handleStart} disabled={starting}>
            {starting ? "Starting..." : "Start Assessment"}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
