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
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { IntegrityReviewSection } from "@/pages/admin/practical/IntegrityReviewSection";
import { PRACTICAL_TYPE_LABEL } from "@/pages/practical/practicalDisplay";

export function PracticalAttemptEvaluatePage() {
  const { id = "" } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const { data: attempt, error, loading, refetch } = useAsync(() => getAdminPracticalAttempt(id), [id]);

  const [rubricPoints, setRubricPoints] = useState<Record<string, number>>({});
  const [directScore, setDirectScore] = useState(0);
  const [feedback, setFeedback] = useState("");
  const [submitting, setSubmitting] = useState(false);

  if (loading) {
    return <LoadingState label="Loading attempt..." />;
  }
  if (error || !attempt) {
    return <ErrorState title="Attempt not found" message="This attempt isn't available." onRetry={refetch} />;
  }

  const hasRubric = attempt.rubricCriteria.length > 0;
  const alreadyEvaluated = attempt.status === "EVALUATED";

  async function handleEvaluate() {
    setSubmitting(true);
    try {
      if (hasRubric) {
        await evaluatePracticalAttempt(id, {
          rubricScores: attempt!.rubricCriteria.map((rc) => ({ criterionId: rc.id, points: rubricPoints[rc.id] ?? 0 })),
          feedback: feedback.trim() || undefined,
        });
      } else {
        await evaluatePracticalAttempt(id, { score: directScore, feedback: feedback.trim() || undefined });
      }
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
        </div>
        <h1 className="mt-2 text-xl font-bold text-foreground">{attempt.assessmentTitle}</h1>
        <p className="text-sm text-muted-foreground">{attempt.studentName}</p>
      </div>

      <div className="space-y-3 rounded-xl border border-border bg-card p-4">
        <h2 className="text-sm font-semibold text-foreground">Submission</h2>
        {attempt.selectedLanguage ? <p className="text-xs text-muted-foreground">Language: {attempt.selectedLanguage}</p> : null}
        {attempt.submissionContent ? (
          <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-lg border border-border bg-muted/30 p-3 text-xs">{attempt.submissionContent}</pre>
        ) : null}
        {attempt.submissionLinkUrl ? (
          <p className="text-sm">
            Link:{" "}
            <a href={attempt.submissionLinkUrl} target="_blank" rel="noreferrer" className="text-primary hover:underline">
              {attempt.submissionLinkUrl}
            </a>
          </p>
        ) : null}
        {!attempt.submissionContent && !attempt.submissionLinkUrl ? <p className="text-sm text-muted-foreground">No submission content.</p> : null}
      </div>

      <IntegrityReviewSection attemptId={id} integrity={attempt.integrity} onOverridden={refetch} />

      {attempt.testCases.length > 0 ? (
        <div className="space-y-2 rounded-xl border border-border bg-card p-4">
          <h2 className="text-sm font-semibold text-foreground">Test Cases (admin view — includes hidden)</h2>
          {attempt.testCases.map((tc) => (
            <div key={tc.id} className="rounded-lg border border-border bg-muted/30 p-3 text-xs">
              <div className="flex items-center gap-2">
                <p className="font-medium text-foreground">{tc.hidden ? "Hidden" : "Public"}</p>
              </div>
              <p className="mt-1 text-muted-foreground">Input: <code className="whitespace-pre-wrap">{tc.input}</code></p>
              <p className="text-muted-foreground">Expected: <code className="whitespace-pre-wrap">{tc.expectedOutput}</code></p>
            </div>
          ))}
        </div>
      ) : null}

      {!alreadyEvaluated ? (
        <div className="space-y-4 rounded-xl border border-border bg-card p-4">
          <h2 className="text-sm font-semibold text-foreground">Evaluation</h2>
          {hasRubric ? (
            <div className="space-y-2">
              {attempt.rubricCriteria.map((rc) => (
                <div key={rc.id} className="flex items-center gap-2">
                  <span className="flex-1 text-sm text-foreground">{rc.criterion}</span>
                  <Input
                    type="number"
                    min={0}
                    max={rc.maxPoints}
                    value={rubricPoints[rc.id] ?? 0}
                    onChange={(e) => setRubricPoints((prev) => ({ ...prev, [rc.id]: Number(e.target.value) || 0 }))}
                    className="w-20"
                  />
                  <span className="text-xs text-muted-foreground">/ {rc.maxPoints}</span>
                </div>
              ))}
              <p className="text-xs text-muted-foreground">
                Total: {Object.values(rubricPoints).reduce((s, v) => s + v, 0)} / 100
              </p>
            </div>
          ) : (
            <div className="space-y-1.5">
              <label htmlFor="direct-score" className="text-sm font-medium text-foreground">
                Score (0-100)
              </label>
              <Input id="direct-score" type="number" min={0} max={100} value={directScore} onChange={(e) => setDirectScore(Number(e.target.value) || 0)} className="w-24" />
            </div>
          )}
          <div className="space-y-1.5">
            <label htmlFor="feedback" className="text-sm font-medium text-foreground">
              Feedback
            </label>
            <Textarea id="feedback" rows={4} value={feedback} onChange={(e) => setFeedback(e.target.value)} />
          </div>
          <Button onClick={handleEvaluate} disabled={submitting}>
            {submitting ? "Submitting..." : "Submit Evaluation"}
          </Button>
        </div>
      ) : (
        <div className="rounded-xl border border-border bg-card p-4">
          <p className="text-sm font-medium text-foreground">Score: {attempt.score} / {attempt.maxScore}</p>
          {attempt.feedback ? <p className="mt-1 text-sm text-muted-foreground">{attempt.feedback}</p> : null}
        </div>
      )}
    </div>
  );
}
