import { ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Card, CardContent } from "@/components/ui/card";
import { getAssessment } from "@/lib/api/endpoints/assessments";
import type { AssessmentResultQuestionView, AssessmentResultResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

function optionText(question: AssessmentResultQuestionView, optionId: string) {
  return question.options.find((o) => o.id === optionId)?.optionText ?? optionId;
}

function QuestionReview({ question, index }: { question: AssessmentResultQuestionView; index: number }) {
  const yourAnswer = question.selectedOptionIds.length > 0
    ? question.selectedOptionIds.map((id) => optionText(question, id)).join(", ")
    : "Not answered";
  const correctAnswer = question.correctOptionIds.map((id) => optionText(question, id)).join(", ");

  return (
    <Card>
      <CardContent className="space-y-3 pt-4">
        <div className="flex items-start gap-2.5">
          {question.correct ? (
            <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-emerald-600" />
          ) : (
            <XCircle className="mt-0.5 size-5 shrink-0 text-destructive" />
          )}
          <p className="text-sm font-medium text-foreground">
            <span className="text-muted-foreground">Question {index + 1}. </span>
            {question.questionText}
          </p>
        </div>

        <div className="space-y-1.5 pl-7 text-sm">
          <p className={cn("font-medium", question.correct ? "text-emerald-600" : "text-destructive")}>
            Your answer: {yourAnswer}
          </p>
          {!question.correct ? <p className="font-medium text-emerald-600">Correct answer: {correctAnswer}</p> : null}
          {question.explanation ? <p className="text-muted-foreground">{question.explanation}</p> : null}
        </div>
      </CardContent>
    </Card>
  );
}

export function AssessmentResultPage() {
  const { assessmentId = "" } = useParams<{ assessmentId: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  const preloaded = (location.state as { result?: AssessmentResultResponse } | null)?.result ?? null;
  const { data, error, loading, refetch } = useAsync(
    () => (preloaded ? Promise.resolve(preloaded) : getAssessment(assessmentId)),
    [assessmentId]
  );

  if (loading) {
    return <LoadingState label="Loading result..." />;
  }

  if (error || !data) {
    return <ErrorState title="Result unavailable" message={error?.message ?? "This result isn't available."} onRetry={refetch} />;
  }

  if (data.status === "IN_PROGRESS") {
    navigate(ROUTES.assessmentDetail(assessmentId), { replace: true });
    return <LoadingState label="Resuming assessment..." />;
  }

  const result = data as AssessmentResultResponse;
  const score = result.scorePercentage ?? 0;

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.skillAssessments}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Skill Assessments
      </Link>

      <Card>
        <CardContent className="space-y-2 py-8 text-center">
          <p className="text-sm font-medium text-muted-foreground">{result.skillName} Assessment</p>
          <h1 className="text-lg font-bold text-foreground">
            {result.status === "EXPIRED" ? "Time's Up" : "Assessment Complete"} {result.status === "EXPIRED" ? "" : "🎉"}
          </h1>
          <p className="text-5xl font-bold text-primary">{score}%</p>
          <p className="text-sm text-muted-foreground">
            {result.correctCount ?? 0} / {result.totalQuestions} Correct
          </p>
        </CardContent>
      </Card>

      <div className="space-y-3">
        <h2 className="text-sm font-semibold text-foreground">Review your answers</h2>
        {result.questions.map((question, i) => (
          <QuestionReview key={question.id} question={question} index={i} />
        ))}
      </div>
    </div>
  );
}
