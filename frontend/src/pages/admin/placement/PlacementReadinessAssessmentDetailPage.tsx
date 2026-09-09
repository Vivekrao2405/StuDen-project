import { AlertTriangle, ArrowLeft, Code2, FileQuestion, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { FormField } from "@/components/shared/FormField";
import { Input } from "@/components/ui/input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  addAssessmentItem,
  archiveAssessment,
  getAdminAssessment,
  listAdminRoles,
  publishAssessment,
  removeAssessmentItem,
  unpublishAssessment,
  updateAssessment,
} from "@/lib/api/endpoints/adminPlacement";
import { listAdminPracticalAssessments } from "@/lib/api/endpoints/adminPracticalAssessments";
import { listQuestions } from "@/lib/api/endpoints/questionBank";
import type { PracticalAssessmentSummary } from "@/lib/api/practicalTypes";
import type { QuestionSummaryResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { DIFFICULTY_OPTIONS } from "@/pages/admin/questionBankOptions";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";

type PickerTab = "question" | "practical";

export function PlacementReadinessAssessmentDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const toast = useToast();

  const detail = useAsync(() => getAdminAssessment(id), [id]);
  const roles = useAsync(() => listAdminRoles(), []);

  const [saving, setSaving] = useState(false);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [pickerTab, setPickerTab] = useState<PickerTab>("question");
  const [pickerSearch, setPickerSearch] = useState("");
  const [pendingRemove, setPendingRemove] = useState<{ id: string; label: string } | null>(null);
  const [removing, setRemoving] = useState(false);

  const assessment = detail.data;

  const questionResults = useAsync(
    () => (pickerOpen && pickerTab === "question"
      ? listQuestions({ status: "PUBLISHED", search: pickerSearch || undefined, size: 20 })
      : Promise.resolve(null)),
    [pickerOpen, pickerTab, pickerSearch]
  );
  const practicalResults = useAsync(
    () => (pickerOpen && pickerTab === "practical"
      ? listAdminPracticalAssessments({ status: "PUBLISHED", search: pickerSearch || undefined, size: 20 })
      : Promise.resolve(null)),
    [pickerOpen, pickerTab, pickerSearch]
  );

  const linkedQuestionIds = useMemo(
    () => new Set((assessment?.questions ?? []).filter((q) => q.itemType === "QUESTION").map((q) => q.questionId)),
    [assessment]
  );
  const linkedPracticalIds = useMemo(
    () =>
      new Set(
        (assessment?.questions ?? [])
          .filter((q) => q.itemType === "PRACTICAL_ASSESSMENT")
          .map((q) => q.practicalAssessmentId)
      ),
    [assessment]
  );

  async function handleFieldSave(field: "title" | "description" | "roleId" | "durationMinutes" | "passingScore", value: string) {
    if (!assessment) return;
    setSaving(true);
    try {
      await updateAssessment(assessment.id, {
        title: field === "title" ? value : assessment.title,
        description: field === "description" ? value || null : assessment.description,
        roleId: field === "roleId" ? value : assessment.roleId,
        difficulty: assessment.difficulty,
        durationMinutes: field === "durationMinutes" ? (value.trim() ? Number(value) : null) : assessment.durationMinutes,
        passingScore: field === "passingScore" ? (value.trim() ? Number(value) : null) : assessment.passingScore,
      });
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't save. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDifficultyChange(value: string) {
    if (!assessment) return;
    setSaving(true);
    try {
      await updateAssessment(assessment.id, {
        title: assessment.title,
        description: assessment.description,
        roleId: assessment.roleId,
        difficulty: value as never,
        durationMinutes: assessment.durationMinutes,
        passingScore: assessment.passingScore,
      });
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't save. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusAction(action: "publish" | "unpublish" | "archive") {
    if (!assessment) return;
    try {
      const fn = action === "publish" ? publishAssessment : action === "unpublish" ? unpublishAssessment : archiveAssessment;
      await fn(assessment.id);
      toast.success(`Assessment ${action === "publish" ? "published" : action === "unpublish" ? "unpublished" : "archived"}.`);
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    }
  }

  async function handleAddQuestion(question: QuestionSummaryResponse) {
    if (!assessment) return;
    try {
      await addAssessmentItem(assessment.id, { questionId: question.id, practicalAssessmentId: null, displayOrder: null, points: null });
      toast.success("Question added.");
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't add this question.");
    }
  }

  async function handleAddPractical(practical: PracticalAssessmentSummary) {
    if (!assessment) return;
    try {
      await addAssessmentItem(assessment.id, { questionId: null, practicalAssessmentId: practical.id, displayOrder: null, points: null });
      toast.success("Practical assessment added.");
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't add this practical assessment.");
    }
  }

  async function handleRemove() {
    if (!assessment || !pendingRemove) return;
    setRemoving(true);
    try {
      await removeAssessmentItem(assessment.id, pendingRemove.id);
      toast.success("Removed.");
      setPendingRemove(null);
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't remove this item.");
    } finally {
      setRemoving(false);
    }
  }

  if (detail.loading) {
    return <LoadingState label="Loading assessment..." />;
  }
  if (detail.error || !assessment) {
    return <ErrorState message={detail.error?.message ?? "Assessment not found."} onRetry={detail.refetch} />;
  }

  return (
    <div className="space-y-6">
      <Link
        to={ROUTES.adminPlacementAssessments}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Readiness Assessments
      </Link>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-foreground">{assessment.title}</h1>
          <Badge variant={assessment.status === "PUBLISHED" ? "default" : assessment.status === "DRAFT" ? "secondary" : "outline"}>
            {assessment.status}
          </Badge>
        </div>
        <div className="flex gap-2">
          {assessment.status === "DRAFT" ? (
            <Button size="sm" onClick={() => handleStatusAction("publish")}>
              Publish
            </Button>
          ) : null}
          {assessment.status === "PUBLISHED" ? (
            <Button size="sm" variant="outline" onClick={() => handleStatusAction("unpublish")}>
              Unpublish
            </Button>
          ) : null}
          {assessment.status !== "ARCHIVED" ? (
            <Button size="sm" variant="outline" onClick={() => handleStatusAction("archive")}>
              Archive
            </Button>
          ) : null}
        </div>
      </div>

      <Card>
        <CardContent className="space-y-4 pt-5">
          <FormField label="Title" htmlFor="edit-title">
            <Input
              id="edit-title"
              defaultValue={assessment.title}
              disabled={saving}
              onBlur={(e) => e.target.value !== assessment.title && handleFieldSave("title", e.target.value)}
            />
          </FormField>
          <FormField label="Description" htmlFor="edit-description" hint="Optional">
            <Textarea
              id="edit-description"
              defaultValue={assessment.description ?? ""}
              disabled={saving}
              rows={2}
              onBlur={(e) => e.target.value !== (assessment.description ?? "") && handleFieldSave("description", e.target.value)}
            />
          </FormField>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <FormField label="Target Role" htmlFor="edit-role">
              <select
                id="edit-role"
                className={QB_SELECT_CLASS}
                defaultValue={assessment.roleId}
                disabled={saving}
                onChange={(e) => handleFieldSave("roleId", e.target.value)}
              >
                {(roles.data ?? []).map((role) => (
                  <option key={role.id} value={role.id}>
                    {role.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Difficulty" htmlFor="edit-difficulty">
              <select
                id="edit-difficulty"
                className={QB_SELECT_CLASS}
                defaultValue={assessment.difficulty}
                disabled={saving}
                onChange={(e) => handleDifficultyChange(e.target.value)}
              >
                {DIFFICULTY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Duration (min)" htmlFor="edit-duration" hint="Optional">
              <Input
                id="edit-duration"
                type="number"
                defaultValue={assessment.durationMinutes ?? ""}
                disabled={saving}
                onBlur={(e) => handleFieldSave("durationMinutes", e.target.value)}
              />
            </FormField>
            <FormField label="Passing %" htmlFor="edit-passing" hint="Optional">
              <Input
                id="edit-passing"
                type="number"
                defaultValue={assessment.passingScore ?? ""}
                disabled={saving}
                onBlur={(e) => handleFieldSave("passingScore", e.target.value)}
              />
            </FormField>
          </div>
        </CardContent>
      </Card>

      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-foreground">Questions ({assessment.questions.length})</h2>
        <Button size="sm" onClick={() => setPickerOpen(true)}>
          Add Question
        </Button>
      </div>

      {assessment.questions.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border bg-muted/30 px-4 py-6 text-center text-sm text-muted-foreground">
          No questions yet — add at least one before publishing.
        </p>
      ) : (
        <div className="space-y-2">
          {assessment.questions.map((q) => (
            <div key={q.id} className="flex items-start gap-3 rounded-lg border border-border bg-card p-3">
              <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent">
                {q.itemType === "QUESTION" ? <FileQuestion className="size-4" /> : <Code2 className="size-4" />}
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-medium text-foreground">
                  {q.itemType === "QUESTION" ? q.questionTextPreview : q.practicalAssessmentTitle}
                </p>
                <div className="mt-1 flex flex-wrap items-center gap-1.5 text-xs text-muted-foreground">
                  <Badge variant="outline">{q.skillName}</Badge>
                  {!q.skillMappedToRole ? (
                    <span className="inline-flex items-center gap-1 text-amber-600" title="This skill isn't part of the target role's configured skills">
                      <AlertTriangle className="size-3" /> Not in role's skill list
                    </span>
                  ) : null}
                  <span>{q.points} pt{q.points === 1 ? "" : "s"}</span>
                  {q.itemType === "PRACTICAL_ASSESSMENT" ? <span>{q.practicalType}</span> : <span>{q.questionType}</span>}
                </div>
              </div>
              <Button
                variant="ghost"
                size="icon-sm"
                onClick={() =>
                  setPendingRemove({
                    id: q.itemType === "QUESTION" ? (q.questionId as string) : (q.practicalAssessmentId as string),
                    label: q.itemType === "QUESTION" ? (q.questionTextPreview as string) : (q.practicalAssessmentTitle as string),
                  })
                }
                title="Remove"
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </div>
          ))}
        </div>
      )}

      <Dialog open={pickerOpen} onOpenChange={(open) => { setPickerOpen(open); if (!open) setPickerSearch(""); }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Add Question</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <SegmentedControl
              value={pickerTab}
              onChange={setPickerTab}
              options={[
                { value: "question", label: "Question Bank" },
                { value: "practical", label: "Practical Assessment" },
              ]}
            />
            <Input
              value={pickerSearch}
              onChange={(e) => setPickerSearch(e.target.value)}
              placeholder={pickerTab === "question" ? "Search published questions..." : "Search published practical assessments..."}
            />
            <div className="max-h-80 space-y-1.5 overflow-y-auto">
              {pickerTab === "question"
                ? (questionResults.data?.content ?? []).map((q) => (
                    <button
                      key={q.id}
                      type="button"
                      disabled={linkedQuestionIds.has(q.id)}
                      onClick={() => handleAddQuestion(q)}
                      className="flex w-full items-start justify-between gap-2 rounded-lg border border-border p-2.5 text-left text-sm hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <span className="min-w-0 flex-1 truncate">{q.questionTextPreview}</span>
                      <Badge variant="outline">{q.skillName}</Badge>
                    </button>
                  ))
                : (practicalResults.data?.content ?? []).map((p) => (
                    <button
                      key={p.id}
                      type="button"
                      disabled={linkedPracticalIds.has(p.id)}
                      onClick={() => handleAddPractical(p)}
                      className="flex w-full items-start justify-between gap-2 rounded-lg border border-border p-2.5 text-left text-sm hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <span className="min-w-0 flex-1 truncate">{p.title}</span>
                      <Badge variant="outline">{p.skillName}</Badge>
                    </button>
                  ))}
              {pickerTab === "question" && (questionResults.data?.content ?? []).length === 0 && !questionResults.loading ? (
                <p className="py-4 text-center text-sm text-muted-foreground">No published questions found.</p>
              ) : null}
              {pickerTab === "practical" && (practicalResults.data?.content ?? []).length === 0 && !practicalResults.loading ? (
                <p className="py-4 text-center text-sm text-muted-foreground">No published practical assessments found.</p>
              ) : null}
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingRemove !== null}
        onOpenChange={(open) => !open && setPendingRemove(null)}
        title="Remove this item?"
        description={`"${pendingRemove?.label}" will be removed from this assessment. The underlying content is not deleted.`}
        confirmLabel="Remove"
        loading={removing}
        onConfirm={handleRemove}
      />
    </div>
  );
}
