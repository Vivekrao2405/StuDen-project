import { ArrowLeft, CheckCircle2 } from "lucide-react";
import { useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { SkillIcon } from "@/components/shared/SkillIcon";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { listAssessableSkills, startAssessment } from "@/lib/api/endpoints/assessments";
import type { AssessableSkillResponse, AssessableSkillsResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";

const INSTRUCTIONS = [
  "Choose the best answer for each question.",
  "Some questions may have multiple correct answers.",
  "Your answers are saved automatically as you go.",
  "You may navigate freely between questions.",
  "Submit when you have completed the assessment.",
];

export function AssessmentInstructionsPage() {
  const { skillId = "" } = useParams<{ skillId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const toast = useToast();
  const [starting, setStarting] = useState(false);

  // Navigated here from the skills grid, we already have the skill — avoids a redundant fetch.
  // Reached directly (refresh/shared link), fall back to fetching the list and matching by id.
  const preloadedSkill = (location.state as { skill?: AssessableSkillResponse } | null)?.skill ?? null;
  const { data, error, loading, refetch } = useAsync(
    () =>
      preloadedSkill
        ? Promise.resolve<AssessableSkillsResponse>({ state: "HAS_AVAILABLE_ASSESSMENTS", skills: [preloadedSkill] })
        : listAssessableSkills(),
    [skillId]
  );
  // listAssessableSkills() only ever returns skills with a PUBLISHED assessment, so reaching this
  // page directly (no preloadedSkill) for an unknown/unpublished skillId naturally falls through
  // to "Assessment not found" below — no separate check needed here.
  const skill = data?.skills.find((s) => s.skillId === skillId) ?? null;

  async function handleStart() {
    setStarting(true);
    try {
      const assessment = await startAssessment(skillId);
      navigate(ROUTES.assessmentDetail(assessment.id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't start this assessment. Please try again.");
    } finally {
      setStarting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading assessment..." />;
  }

  if (error || !skill) {
    return <ErrorState title="Assessment not found" message="This skill assessment isn't available." onRetry={refetch} />;
  }

  return (
    <div className="mx-auto max-w-xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.skillAssessments}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Skill Assessments
      </Link>

      <Card>
        <CardContent className="space-y-6 pt-6">
          <div className="flex items-center gap-3">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-accent">
              <SkillIcon iconSlug={skill.iconSlug} iconType={skill.iconType} className="size-7" />
            </span>
            <div>
              <h1 className="text-xl font-bold text-foreground">{skill.name} Assessment</h1>
              <p className="text-sm text-muted-foreground">
                {skill.requiredQuestionCount} Questions · Knowledge Assessment
              </p>
            </div>
          </div>

          {skill.assessable ? (
            <>
              <div className="space-y-2.5">
                <h2 className="text-sm font-semibold text-foreground">Instructions</h2>
                <ul className="space-y-2">
                  {INSTRUCTIONS.map((line) => (
                    <li key={line} className="flex items-start gap-2 text-sm text-muted-foreground">
                      <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-primary" />
                      {line}
                    </li>
                  ))}
                </ul>
              </div>

              <Button className="w-full" size="lg" onClick={handleStart} disabled={starting}>
                {starting ? "Starting..." : "Start Assessment"}
              </Button>
            </>
          ) : (
            <p className="rounded-lg border border-dashed border-border bg-muted/30 px-4 py-6 text-center text-sm text-muted-foreground">
              Not enough questions are available for this assessment yet. Check back soon.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
