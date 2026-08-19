import { ArrowLeft, CheckCircle2, XCircle } from "lucide-react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { getAssessment, getAssessmentResult } from "@/lib/api/endpoints/assessments";
import type { AssessmentResultQuestionView, AssessmentResultResponse, AssessmentResultSummaryResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { isPlainTextContent, parseQuestionContent } from "@/lib/questionContent";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { levelColorClasses, levelLabel } from "@/pages/assessment/assessmentLevelDisplay";
import { TopicPerformanceBar } from "@/pages/assessment/TopicPerformanceBar";

interface ResultPageData {
  review: AssessmentResultResponse;
  summary: AssessmentResultSummaryResponse;
}

async function loadResultPageData(
  assessmentId: string,
  preloadedReview: AssessmentResultResponse | null
): Promise<ResultPageData | "IN_PROGRESS"> {
  const data = preloadedReview ?? (await getAssessment(assessmentId));
  if (data.status === "IN_PROGRESS") {
    return "IN_PROGRESS";
  }
  const review = data as AssessmentResultResponse;
  const summary = await getAssessmentResult(assessmentId);
  return { review, summary };
}

// The "Your answer: ..." / "Correct answer: ..." lines are a single-line plain-text summary, not a
// full QuestionContent render — for a code-only option, show the bare code (fences stripped, newlines
// flattened to spaces) instead of the raw ```fence``` markers so the summary stays readable.
function optionText(question: AssessmentResultQuestionView, optionId: string) {
  const raw = question.options.find((o) => o.id === optionId)?.optionText ?? optionId;
  const segments = parseQuestionContent(raw);
  if (segments.length === 1 && segments[0].type === "code") {
    return segments[0].value.replace(/\n+/g, " ").trim();
  }
  return raw;
}

function QuestionReview({ question, index }: { question: AssessmentResultQuestionView; index: number }) {
  const yourAnswer = question.selectedOptionIds.length > 0
    ? question.selectedOptionIds.map((id) => optionText(question, id)).join(", ")
    : "Not answered";
  const correctAnswer = question.correctOptionIds.map((id) => optionText(question, id)).join(", ");
  const plainQuestion = isPlainTextContent(question.questionText);
  const plainExplanation = !question.explanation || isPlainTextContent(question.explanation);

  return (
    <Card>
      <CardContent className="space-y-3 pt-4">
        <div className="flex items-start gap-2.5">
          {question.correct ? (
            <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-emerald-600" />
          ) : (
            <XCircle className="mt-0.5 size-5 shrink-0 text-destructive" />
          )}
          {plainQuestion ? (
            <p className="text-sm font-medium text-foreground">
              <span className="text-muted-foreground">Question {index + 1}. </span>
              {question.questionText}
            </p>
          ) : (
            <div className="min-w-0 flex-1 space-y-2">
              <p className="text-sm font-medium text-foreground">
                <span className="text-muted-foreground">Question {index + 1}.</span>
              </p>
              <QuestionContent text={question.questionText} textClassName="text-sm font-medium text-foreground" />
            </div>
          )}
        </div>

        <div className="space-y-1.5 pl-7 text-sm">
          <p className={cn("font-medium", question.correct ? "text-emerald-600" : "text-destructive")}>
            Your answer: {yourAnswer}
          </p>
          {!question.correct ? <p className="font-medium text-emerald-600">Correct answer: {correctAnswer}</p> : null}
          {question.explanation ? (
            plainExplanation ? (
              <p className="text-muted-foreground">{question.explanation}</p>
            ) : (
              <QuestionContent text={question.explanation} textClassName="text-muted-foreground" />
            )
          ) : null}
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
    () => loadResultPageData(assessmentId, preloaded),
    [assessmentId]
  );

  if (loading) {
    return <LoadingState label="Loading result..." />;
  }

  if (error || !data) {
    return <ErrorState title="Result unavailable" message={error?.message ?? "This result isn't available."} onRetry={refetch} />;
  }

  if (data === "IN_PROGRESS") {
    navigate(ROUTES.assessmentDetail(assessmentId), { replace: true });
    return <LoadingState label="Resuming assessment..." />;
  }

  const { review, summary } = data;

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.skillAssessments}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Skill Assessments
      </Link>

      <Card>
        <CardContent className="space-y-3 py-8 text-center">
          <p className="text-sm font-medium text-muted-foreground">{summary.skillName} Assessment</p>
          <h1 className="text-lg font-bold text-foreground">
            {summary.status === "EXPIRED" ? "Time's Up" : "Assessment Complete"} {summary.status === "EXPIRED" ? "" : "🎉"}
          </h1>
          <p className="text-5xl font-bold text-primary">{summary.scorePercentage}%</p>
          <span
            className={cn(
              "inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold",
              levelColorClasses(summary.level)
            )}
          >
            Assessment Level: {levelLabel(summary.level)}
          </span>
          <p className="text-sm text-muted-foreground">
            {summary.correctCount} / {summary.totalQuestions} Correct
          </p>
        </CardContent>
      </Card>

      {summary.topicPerformance.length > 0 ? (
        <Card>
          <CardContent className="space-y-4 pt-4">
            <h2 className="text-sm font-semibold text-foreground">Topic Performance</h2>
            <div className="space-y-3">
              {summary.topicPerformance.map((topic) => (
                <TopicPerformanceBar key={topic.topicId ?? "general"} topic={topic} />
              ))}
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Card>
          <CardContent className="space-y-2 pt-4">
            <h2 className="text-sm font-semibold text-foreground">Strong Areas</h2>
            {summary.summary.strongTopics.length > 0 ? (
              <ul className="space-y-1.5">
                {summary.summary.strongTopics.map((topic) => (
                  <li key={topic} className="flex items-center gap-2 text-sm text-foreground">
                    <CheckCircle2 className="size-4 shrink-0 text-emerald-600" /> {topic}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">None yet — keep practicing.</p>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-2 pt-4">
            <h2 className="text-sm font-semibold text-foreground">Needs Improvement</h2>
            {summary.summary.needsImprovementTopics.length > 0 ? (
              <ul className="space-y-1.5">
                {summary.summary.needsImprovementTopics.map((topic) => (
                  <li key={topic} className="flex items-center gap-2 text-sm text-foreground">
                    <span className="size-1.5 shrink-0 rounded-full bg-destructive" /> {topic}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">Nice — no weak areas found.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <Button className="w-full" size="lg" onClick={() => navigate(ROUTES.skillAssessmentDetail(summary.skillId))}>
        Retake Assessment
      </Button>

      <div className="space-y-3">
        <h2 className="text-sm font-semibold text-foreground">Review your answers</h2>
        {review.questions.map((question, i) => (
          <QuestionReview key={question.id} question={question} index={i} />
        ))}
      </div>
    </div>
  );
}
