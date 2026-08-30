import { Plus, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { LoadingState } from "@/components/shared/LoadingState";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  archiveQuestion,
  checkDuplicateQuestion,
  createNewQuestionVersion,
  createQuestion,
  getQuestion,
  publishQuestion,
  submitQuestionForReview,
  updateQuestion,
} from "@/lib/api/endpoints/questionBank";
import type {
  Difficulty,
  DuplicateWarning,
  QuestionOptionRequest,
  QuestionRequest,
  QuestionResponse,
  QuestionType,
  SkillResponse,
} from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { isPlainTextContent } from "@/lib/questionContent";
import { ROUTES } from "@/lib/routes";
import {
  DIFFICULTY_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  statusBadgeVariant,
  statusLabel,
} from "@/pages/admin/questionBankOptions";
import { QuestionPreviewDialog } from "@/pages/admin/QuestionPreviewDialog";
import { QuestionSkillPicker } from "@/pages/admin/QuestionSkillPicker";
import { QuestionTopicPicker } from "@/pages/admin/QuestionTopicPicker";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";

interface OptionRow {
  key: string;
  text: string;
  correct: boolean;
}

const TRUE_FALSE_ROWS: OptionRow[] = [
  { key: "true", text: "True", correct: true },
  { key: "false", text: "False", correct: false },
];

function blankOptionRows(): OptionRow[] {
  return [
    { key: crypto.randomUUID(), text: "", correct: false },
    { key: crypto.randomUUID(), text: "", correct: false },
  ];
}

function rowsFromQuestion(question?: QuestionResponse): OptionRow[] {
  if (!question) return blankOptionRows();
  return question.options.map((o) => ({ key: o.id, text: o.optionText, correct: o.isCorrect }));
}

interface FormErrors {
  skill?: string;
  questionText?: string;
  options?: string;
}

export function QuestionEditorPage() {
  const { questionId } = useParams<{ questionId: string }>();
  const isEditing = Boolean(questionId);
  const navigate = useNavigate();
  const toast = useToast();

  const existing = useAsync(() => (questionId ? getQuestion(questionId) : Promise.resolve(null)), [questionId]);

  const [skill, setSkill] = useState<SkillResponse | null>(null);
  const [topicId, setTopicId] = useState<string | null>(null);
  const [questionText, setQuestionText] = useState("");
  const [questionType, setQuestionType] = useState<QuestionType>("MCQ_SINGLE");
  const [difficulty, setDifficulty] = useState<Difficulty>("EASY");
  const [explanation, setExplanation] = useState("");
  const [tag, setTag] = useState("");
  const [options, setOptions] = useState<OptionRow[]>(blankOptionRows());
  const [initialized, setInitialized] = useState(false);

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [duplicateWarning, setDuplicateWarning] = useState<DuplicateWarning | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const loadedQuestion = existing.data ?? undefined;

  // Populate form state once, when the fetched question first arrives — avoids clobbering
  // in-progress edits on every background refetch.
  useEffect(() => {
    if (loadedQuestion && !initialized) {
      setSkill({
        id: loadedQuestion.skillId,
        name: loadedQuestion.skillName,
        category: "",
        iconSlug: null,
        iconType: "LUCIDE",
      });
      setTopicId(loadedQuestion.topicId);
      setQuestionText(loadedQuestion.questionText);
      setQuestionType(loadedQuestion.questionType);
      setDifficulty(loadedQuestion.difficulty);
      setExplanation(loadedQuestion.explanation ?? "");
      setTag(loadedQuestion.tag ?? "");
      setOptions(rowsFromQuestion(loadedQuestion));
      setInitialized(true);
    }
  }, [loadedQuestion, initialized]);

  function handleTypeChange(next: QuestionType) {
    setQuestionType(next);
    if (next === "TRUE_FALSE") {
      setOptions(TRUE_FALSE_ROWS.map((r) => ({ ...r, key: crypto.randomUUID() })));
    } else if (questionType === "TRUE_FALSE") {
      setOptions(blankOptionRows());
    }
  }

  function updateOptionText(key: string, text: string) {
    setOptions((prev) => prev.map((o) => (o.key === key ? { ...o, text } : o)));
  }

  function toggleOptionCorrect(key: string) {
    setOptions((prev) =>
      prev.map((o) => {
        if (questionType === "MCQ_SINGLE") {
          return { ...o, correct: o.key === key };
        }
        return o.key === key ? { ...o, correct: !o.correct } : o;
      })
    );
  }

  function addOptionRow() {
    setOptions((prev) => [...prev, { key: crypto.randomUUID(), text: "", correct: false }]);
  }

  function removeOptionRow(key: string) {
    setOptions((prev) => prev.filter((o) => o.key !== key));
  }

  async function handleQuestionTextBlur() {
    if (!skill || !questionText.trim()) {
      setDuplicateWarning(null);
      return;
    }
    try {
      const warning = await checkDuplicateQuestion(skill.id, questionText.trim());
      // Don't warn about a duplicate of the question we're currently editing.
      setDuplicateWarning(warning && warning.existingQuestionId !== questionId ? warning : null);
    } catch {
      // Best-effort — a failed duplicate check must never block editing.
    }
  }

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (!skill) next.skill = "Select a skill.";
    if (!questionText.trim()) next.questionText = "Question text is required.";

    const filledOptions = options.filter((o) => o.text.trim());
    const correctCount = options.filter((o) => o.correct).length;
    if (questionType === "MCQ_SINGLE") {
      if (filledOptions.length < 2) next.options = "Add at least 2 options.";
      else if (correctCount !== 1) next.options = "Mark exactly 1 option as correct.";
    } else if (questionType === "MCQ_MULTIPLE") {
      if (filledOptions.length < 2) next.options = "Add at least 2 options.";
      else if (correctCount < 1) next.options = "Mark at least 1 option as correct.";
    } else if (correctCount !== 1) {
      next.options = "Choose whether True or False is correct.";
    }

    return next;
  }

  function buildPayload(): QuestionRequest {
    const optionPayload: QuestionOptionRequest[] = options
      .filter((o) => o.text.trim())
      .map((o, i) => ({ optionText: o.text.trim(), displayOrder: i, isCorrect: o.correct }));
    return {
      skillId: skill?.id ?? "",
      topicId: topicId ?? undefined,
      questionText: questionText.trim(),
      questionType,
      difficulty,
      explanation: explanation.trim() || undefined,
      tag: tag.trim() || undefined,
      options: optionPayload,
    };
  }

  async function handleSave() {
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setApiError(null);
    setSubmitting(true);
    try {
      const result =
        isEditing && questionId ? await updateQuestion(questionId, buildPayload()) : await createQuestion(buildPayload());
      toast.success(isEditing ? "Question updated." : "Question created.");
      navigate(ROUTES.questionDetail(result.id), { replace: true });
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function runTransition(action: (id: string) => Promise<QuestionResponse>, successMessage: string) {
    if (!questionId) return;
    setApiError(null);
    setSubmitting(true);
    try {
      await action(questionId);
      toast.success(successMessage);
      existing.refetch();
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCreateNewVersion() {
    if (!questionId) return;
    setApiError(null);
    setSubmitting(true);
    try {
      const next = await createNewQuestionVersion(questionId);
      toast.success("New draft version created.");
      navigate(ROUTES.questionDetail(next.id));
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (isEditing && existing.loading) {
    return <LoadingState label="Loading question..." />;
  }
  if (isEditing && existing.error) {
    return <ErrorState message={existing.error.message} onRetry={existing.refetch} />;
  }

  const readOnly = Boolean(loadedQuestion && (loadedQuestion.status === "PUBLISHED" || loadedQuestion.status === "ARCHIVED"));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{isEditing ? "Edit Question" : "Create Question"}</h1>
          {loadedQuestion ? (
            <div className="mt-1 flex items-center gap-2">
              <Badge variant={statusBadgeVariant(loadedQuestion.status)}>{statusLabel(loadedQuestion.status)}</Badge>
              {loadedQuestion.version > 1 ? (
                <span className="text-xs text-muted-foreground">Version {loadedQuestion.version}</span>
              ) : null}
            </div>
          ) : null}
        </div>
        <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.questionBank)}>
          Back to Question Bank
        </Button>
      </div>

      {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

      {readOnly && loadedQuestion ? (
        <div className="space-y-4 rounded-lg border border-border p-4">
          <p className="text-sm text-muted-foreground">
            {loadedQuestion.status === "PUBLISHED"
              ? "This question is published and can't be edited directly, to protect the integrity of any assessment that has already used it."
              : "This question is archived and read-only."}
          </p>
          <div className="space-y-2">
            <QuestionContent text={loadedQuestion.questionText} textClassName="text-sm font-medium text-foreground" />
            <ul className="space-y-1 text-sm">
              {loadedQuestion.options.map((o) => (
                <li key={o.id} className={o.isCorrect ? "font-medium text-primary" : "text-muted-foreground"}>
                  {isPlainTextContent(o.optionText) ? (
                    <>
                      {o.optionText} {o.isCorrect ? "✓" : ""}
                    </>
                  ) : (
                    <div className="space-y-1.5">
                      <QuestionContent text={o.optionText} />
                      {o.isCorrect ? <span>✓</span> : null}
                    </div>
                  )}
                </li>
              ))}
            </ul>
            {loadedQuestion.explanation ? (
              isPlainTextContent(loadedQuestion.explanation) ? (
                <p className="text-sm text-muted-foreground">
                  <span className="font-medium text-foreground">Explanation: </span>
                  {loadedQuestion.explanation}
                </p>
              ) : (
                <div className="space-y-1.5 text-sm text-muted-foreground">
                  <span className="font-medium text-foreground">Explanation:</span>
                  <QuestionContent text={loadedQuestion.explanation} textClassName="text-muted-foreground" />
                </div>
              )
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2 border-t border-border pt-4">
            {loadedQuestion.status === "PUBLISHED" ? (
              <Button size="sm" onClick={handleCreateNewVersion} disabled={submitting}>
                {submitting ? "Creating..." : "Create New Version"}
              </Button>
            ) : null}
            {loadedQuestion.status !== "ARCHIVED" ? (
              <Button
                size="sm"
                variant="outline"
                onClick={() => runTransition(archiveQuestion, "Question archived.")}
                disabled={submitting}
              >
                Archive
              </Button>
            ) : null}
          </div>
        </div>
      ) : (
        <div className="space-y-4 rounded-lg border border-border p-4">
          <FormField label="Skill" htmlFor="question-skill" error={errors.skill}>
            <QuestionSkillPicker value={skill} onChange={setSkill} disabled={submitting} />
          </FormField>

          <FormField label="Topic" htmlFor="question-topic" hint="Optional.">
            <QuestionTopicPicker skillId={skill?.id ?? null} value={topicId} onChange={setTopicId} disabled={submitting} />
          </FormField>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField label="Question type" htmlFor="question-type">
              <select
                id="question-type"
                value={questionType}
                onChange={(e) => handleTypeChange(e.target.value as QuestionType)}
                className={QB_SELECT_CLASS}
                disabled={submitting}
              >
                {QUESTION_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>

            <FormField label="Difficulty" htmlFor="question-difficulty">
              <select
                id="question-difficulty"
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as Difficulty)}
                className={QB_SELECT_CLASS}
                disabled={submitting}
              >
                {DIFFICULTY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
          </div>

          <FormField
            label="Question"
            htmlFor="question-text"
            error={errors.questionText}
            hint="To include code, wrap it in triple backticks with a language, e.g. ```python ... ```."
          >
            <Textarea
              id="question-text"
              value={questionText}
              onChange={(e) => setQuestionText(e.target.value)}
              onBlur={handleQuestionTextBlur}
              placeholder="e.g. Which JOIN returns matching rows from both tables?"
              rows={3}
              disabled={submitting}
            />
          </FormField>

          {duplicateWarning ? (
            <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-700 dark:text-amber-400">
              A similar question already exists for this skill: "{duplicateWarning.existingQuestionText}". You can
              still save — this is just a heads-up.
            </div>
          ) : null}

          <FormField
            label={questionType === "TRUE_FALSE" ? "Correct answer" : "Options"}
            htmlFor="question-options"
            error={errors.options}
          >
            <div className="space-y-2">
              {options.map((row) => (
                <div key={row.key} className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => toggleOptionCorrect(row.key)}
                    disabled={submitting}
                    aria-label={row.correct ? "Marked correct" : "Mark as correct"}
                    className={`flex size-5 shrink-0 items-center justify-center rounded-full border text-[10px] ${
                      row.correct ? "border-primary bg-primary text-primary-foreground" : "border-input"
                    }`}
                  >
                    {row.correct ? "✓" : ""}
                  </button>
                  {questionType === "TRUE_FALSE" ? (
                    <span className="flex-1 text-sm text-foreground">{row.text}</span>
                  ) : (
                    <Input
                      value={row.text}
                      onChange={(e) => updateOptionText(row.key, e.target.value)}
                      placeholder="Option text"
                      className="h-9 flex-1"
                      disabled={submitting}
                    />
                  )}
                  {questionType !== "TRUE_FALSE" ? (
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Remove option"
                      onClick={() => removeOptionRow(row.key)}
                      disabled={submitting || options.length <= 2}
                    >
                      <X />
                    </Button>
                  ) : null}
                </div>
              ))}
              {questionType !== "TRUE_FALSE" ? (
                <Button type="button" variant="outline" size="sm" onClick={addOptionRow} disabled={submitting}>
                  <Plus className="size-3.5" /> Add option
                </Button>
              ) : null}
            </div>
          </FormField>

          <FormField
            label="Explanation"
            htmlFor="question-explanation"
            hint="Required before publishing — shown to learners after they answer."
          >
            <Textarea
              id="question-explanation"
              value={explanation}
              onChange={(e) => setExplanation(e.target.value)}
              placeholder="Explain why the correct answer is correct..."
              rows={3}
              disabled={submitting}
            />
          </FormField>

          <FormField label="Tag" htmlFor="question-tag" hint="Optional — one hierarchical tag, e.g. python-sets-operators.">
            <Input
              id="question-tag"
              value={tag}
              onChange={(e) => setTag(e.target.value)}
              placeholder="e.g. python-sets-operators"
              className="h-10"
              disabled={submitting}
            />
          </FormField>

          <div className="flex flex-wrap gap-2 border-t border-border pt-4">
            <Button type="button" size="sm" onClick={handleSave} disabled={submitting}>
              {submitting ? "Saving..." : isEditing ? "Save Changes" : "Save Draft"}
            </Button>
            <Button type="button" variant="outline" size="sm" onClick={() => setPreviewOpen(true)} disabled={submitting}>
              Preview
            </Button>
            {isEditing && loadedQuestion?.status === "DRAFT" ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => runTransition(submitQuestionForReview, "Submitted for review.")}
                disabled={submitting}
              >
                Submit for Review
              </Button>
            ) : null}
            {isEditing && loadedQuestion?.status === "REVIEW" ? (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => runTransition(publishQuestion, "Question published.")}
                disabled={submitting}
              >
                Publish
              </Button>
            ) : null}
            {isEditing && loadedQuestion && loadedQuestion.status !== "ARCHIVED" ? (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => runTransition(archiveQuestion, "Question archived.")}
                disabled={submitting}
              >
                Archive
              </Button>
            ) : null}
          </div>
        </div>
      )}

      <QuestionPreviewDialog
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
        questionText={questionText}
        skillName={skill?.name ?? ""}
        difficulty={difficulty}
        options={options.map((o) => ({ id: o.key, optionText: o.text, isCorrect: o.correct }))}
      />
    </div>
  );
}
