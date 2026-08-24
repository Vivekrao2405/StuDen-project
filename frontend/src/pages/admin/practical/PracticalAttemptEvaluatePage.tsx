import { ArrowLeft } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { evaluatePracticalAttempt, getAdminPracticalAttempt } from "@/lib/api/endpoints/adminPracticalAssessments";
import type { AdminAttemptQuestionDetail } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { IntegrityReviewSection } from "@/pages/admin/practical/IntegrityReviewSection";
import { PRACTICAL_TYPE_LABEL, questionStatusLabel } from "@/pages/practical/practicalDisplay";

interface QuestionDraft {
  rubricPoints: Record<string, number>;
  directScore: number;
  feedback: string;
}

function QuestionEvaluationCard({
  question,
  index,
  draft,
  onChange,
  readOnly,
}: {
  question: AdminAttemptQuestionDetail;
  index: number;
  draft: QuestionDraft;
  onChange: (patch: Partial<QuestionDraft>) => void;
  readOnly: boolean;
}) {
  const hasRubric = question.rubricCriteria.length > 0;
  const rubricTotal = Object.values(draft.rubricPoints).reduce((s, v) => s + v, 0);

  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-foreground">
          Question {index + 1} — {question.title}
        </h2>
        <Badge variant="outline">{question.pointsPossible} pts</Badge>
      </div>

      <div className="space-y-2 text-sm">
        {question.selectedLanguage ? <p className="text-xs text-muted-foreground">Language: {question.selectedLanguage}</p> : null}
        {question.submissionContent ? (
          <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-lg border border-border bg-muted/30 p-3 text-xs">{question.submissionContent}</pre>
        ) : null}
        {question.submissionLinkUrl ? (
          <p>
            Link:{" "}
            <a href={question.submissionLinkUrl} target="_blank" rel="noreferrer" className="text-primary hover:underline">
              {question.submissionLinkUrl}
            </a>
          </p>
        ) : null}
        {!question.submissionContent && !question.submissionLinkUrl ? (
          <p className="text-muted-foreground">No submission content.</p>
        ) : null}
      </div>

      {question.testsTotal != null ? (
        <p className="text-xs text-muted-foreground">
          Automated result: {question.testsPassed}/{question.testsTotal} tests passed ({questionStatusLabel(question.status)})
        </p>
      ) : null}

      {question.testCases.length > 0 ? (
        <div className="space-y-2">
          <h3 className="text-xs font-semibold text-foreground">Test Cases (admin view — includes hidden)</h3>
          {question.testCases.map((tc) => (
            <div key={tc.id} className="rounded-lg border border-border bg-muted/30 p-3 text-xs">
              <p className="font-medium text-foreground">{tc.hidden ? "Hidden" : "Public"}</p>
              <p className="mt-1 text-muted-foreground">Input: <code className="whitespace-pre-wrap">{tc.input}</code></p>
              <p className="text-muted-foreground">Expected: <code className="whitespace-pre-wrap">{tc.expectedOutput}</code></p>
            </div>
          ))}
        </div>
      ) : null}

      {!readOnly ? (
        <div className="space-y-3 border-t border-border pt-3">
          {hasRubric ? (
            <div className="space-y-2">
              {question.rubricCriteria.map((rc) => (
                <div key={rc.id} className="flex items-center gap-2">
                  <span className="flex-1 text-sm text-foreground">{rc.criterion}</span>
                  <Input
                    type="number"
                    min={0}
                    max={rc.maxPoints}
                    value={draft.rubricPoints[rc.id] ?? 0}
                    onChange={(e) =>
                      onChange({ rubricPoints: { ...draft.rubricPoints, [rc.id]: Number(e.target.value) || 0 } })
                    }
                    className="w-20"
                  />
                  <span className="text-xs text-muted-foreground">/ {rc.maxPoints}</span>
                </div>
              ))}
              <p className="text-xs text-muted-foreground">
                Total: {rubricTotal} / 100 → {Math.round((rubricTotal * question.pointsPossible) / 100)} / {question.pointsPossible} pts
              </p>
            </div>
          ) : (
            <div className="space-y-1.5">
              <label className="text-sm font-medium text-foreground">Score (0-{question.pointsPossible})</label>
              <Input
                type="number"
                min={0}
                max={question.pointsPossible}
                value={draft.directScore}
                onChange={(e) => onChange({ directScore: Number(e.target.value) || 0 })}
                className="w-24"
              />
            </div>
          )}
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">Feedback for this question</label>
            <Textarea rows={3} value={draft.feedback} onChange={(e) => onChange({ feedback: e.target.value })} />
          </div>
        </div>
      ) : (
        <div className="border-t border-border pt-3">
          <p className="text-sm font-medium text-foreground">Score: {question.pointsEarned ?? "—"} / {question.pointsPossible}</p>
          {question.feedback ? <p className="mt-1 text-sm text-muted-foreground">{question.feedback}</p> : null}
        </div>
      )}
    </div>
  );
}

export function PracticalAttemptEvaluatePage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const { data: attempt, error, loading, refetch } = useAsync(() => getAdminPracticalAttempt(id), [id]);

  const [drafts, setDrafts] = useState<Record<string, QuestionDraft>>({});
  const [overallFeedback, setOverallFeedback] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (loading) {
    return <LoadingState label="Loading attempt..." />;
  }
  if (error || !attempt) {
    return <ErrorState title="Attempt not found" message="This attempt isn't available." onRetry={refetch} />;
  }

  // Manual evaluation is only a valid action while UNDER_REVIEW — an AUTOMATED assessment reaches
  // SUBMITTED with a real, server-computed score of its own and never needs (or accepts, per the
  // backend's status guard) admin scoring at all. Treat both as "already resolved" for display.
  const alreadyEvaluated = attempt.status !== "UNDER_REVIEW";

  function draftFor(questionId: string): QuestionDraft {
    return drafts[questionId] ?? { rubricPoints: {}, directScore: 0, feedback: "" };
  }

  function updateDraft(questionId: string, patch: Partial<QuestionDraft>) {
    setDrafts((prev) => ({ ...prev, [questionId]: { ...draftFor(questionId), ...patch } }));
  }

  async function handleEvaluate() {
    setSubmitting(true);
    try {
      await evaluatePracticalAttempt(id, {
        questions: attempt!.questions.map((q) => {
          const draft = draftFor(q.attemptQuestionId);
          const hasRubric = q.rubricCriteria.length > 0;
          return {
            attemptQuestionId: q.attemptQuestionId,
            rubricScores: hasRubric
              ? q.rubricCriteria.map((rc) => ({ criterionId: rc.id, points: draft.rubricPoints[rc.id] ?? 0 }))
              : null,
            score: hasRubric ? null : draft.directScore,
            feedback: draft.feedback.trim() || undefined,
          };
        }),
        feedback: overallFeedback.trim() || undefined,
      });
      toast.success("Attempt evaluated.");
      navigate(ROUTES.adminPracticalAttempts);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Link to={ROUTES.adminPracticalAttempts} className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" /> Back to queue
      </Link>

      <div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="outline">{PRACTICAL_TYPE_LABEL[attempt.practicalType]}</Badge>
          <Badge variant="secondary">{attempt.status}</Badge>
          {attempt.questions.length > 1 ? <span className="text-xs text-muted-foreground">{attempt.questions.length} questions</span> : null}
        </div>
        <h1 className="mt-2 text-xl font-bold text-foreground">{attempt.assessmentTitle}</h1>
        <p className="text-sm text-muted-foreground">{attempt.studentName}</p>
        {alreadyEvaluated ? <p className="mt-1 text-sm font-medium text-foreground">Total: {attempt.score} / {attempt.maxScore}</p> : null}
      </div>

      <IntegrityReviewSection attemptId={id} integrity={attempt.integrity} onOverridden={refetch} />

      {attempt.questions.map((q, i) => (
        <QuestionEvaluationCard
          key={q.attemptQuestionId}
          question={q}
          index={i}
          draft={draftFor(q.attemptQuestionId)}
          onChange={(patch) => updateDraft(q.attemptQuestionId, patch)}
          readOnly={alreadyEvaluated}
        />
      ))}

      {!alreadyEvaluated ? (
        <div className="space-y-4 rounded-xl border border-border bg-card p-4">
          <div className="space-y-1.5">
            <label className="text-sm font-medium text-foreground">Overall feedback (optional)</label>
            <Textarea rows={3} value={overallFeedback} onChange={(e) => setOverallFeedback(e.target.value)} />
          </div>
          <Button onClick={handleEvaluate} disabled={submitting}>
            {submitting ? "Submitting..." : "Submit Evaluation"}
          </Button>
        </div>
      ) : attempt.feedback ? (
        <div className="rounded-xl border border-border bg-card p-4">
          <p className="text-sm font-semibold text-foreground">Overall feedback</p>
          <p className="mt-1 text-sm text-muted-foreground">{attempt.feedback}</p>
        </div>
      ) : null}
    </div>
  );
}
