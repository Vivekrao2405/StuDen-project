import { ChevronDown, ChevronUp, Copy, Eye, FlaskConical, Plus, X } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  archivePracticalAssessment,
  createNewPracticalAssessmentVersion,
  createPracticalAssessment,
  getAdminPracticalAssessment,
  publishPracticalAssessment,
  submitPracticalAssessmentForReview,
  updatePracticalAssessment,
} from "@/lib/api/endpoints/adminPracticalAssessments";
import type {
  CodingLanguage,
  EvaluationType,
  PracticalAssessmentDetail,
  PracticalAssessmentRequest,
  PracticalCodingLanguageInput,
  PracticalQuestionInput,
  PracticalRubricCriterionInput,
  PracticalTestCaseInput,
  PracticalType,
  WorkspaceType,
} from "@/lib/api/practicalTypes";
import type { Difficulty, SkillResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { QuestionSkillPicker } from "@/pages/admin/QuestionSkillPicker";
import { AdminTestQuestionDialog } from "@/pages/admin/practical/AdminTestQuestionDialog";
import {
  assessmentStatusBadgeVariant,
  CODING_LANGUAGE_LABEL,
  DIFFICULTY_OPTIONS,
  EVALUATION_TYPE_OPTIONS,
  PRACTICAL_TYPE_OPTIONS,
  WORKSPACE_TYPE_OPTIONS,
} from "@/pages/practical/practicalDisplay";
import { WORKSPACE_REGISTRY } from "@/pages/practical/workspaces/registry";

const ALL_CODING_LANGUAGES: CodingLanguage[] = ["JAVA", "PYTHON", "C", "CPP"];

// Phase 7.6 Assessment Integrity policy lives inside the ASSESSMENT's configurationJson's
// `integrityPolicy` key (see backend IntegrityPolicyResolver) — assessment-wide, not per-question
// — rather than as its own column. This reads/writes just that key without disturbing anything
// else an admin might put in that field (there currently is nothing else assessment-level).
interface EditableIntegrityPolicy {
  allowCopy: boolean;
  allowPaste: boolean;
  allowCut: boolean;
  requireFullscreen: boolean;
}

const DEFAULT_INTEGRITY_POLICY: EditableIntegrityPolicy = {
  allowCopy: true,
  allowPaste: true,
  allowCut: true,
  requireFullscreen: false,
};

function parseConfig(configurationJson: string | null): Record<string, unknown> {
  if (!configurationJson || !configurationJson.trim()) return {};
  try {
    const parsed: unknown = JSON.parse(configurationJson);
    return parsed && typeof parsed === "object" ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function extractIntegrityPolicy(configurationJson: string | null): EditableIntegrityPolicy {
  const raw = parseConfig(configurationJson).integrityPolicy;
  if (!raw || typeof raw !== "object") return DEFAULT_INTEGRITY_POLICY;
  const policy = raw as Partial<EditableIntegrityPolicy>;
  return {
    allowCopy: policy.allowCopy ?? true,
    allowPaste: policy.allowPaste ?? true,
    allowCut: policy.allowCut ?? true,
    requireFullscreen: policy.requireFullscreen ?? false,
  };
}

// One question's editable form state — a client-side-only shape, never sent as-is; see
// buildQuestionsPayload. `key` is a stable React key independent of the backend id (a brand-new or
// duplicated question has id=null until the first Save).
interface QuestionFormState {
  key: string;
  id: string | null;
  title: string;
  skillOverride: SkillResponse | null;
  difficultyOverride: Difficulty | "";
  instructions: string;
  requirements: string;
  constraints: string;
  configurationJson: string;
  points: number;
  languages: PracticalCodingLanguageInput[];
  testCases: PracticalTestCaseInput[];
  rubricCriteria: PracticalRubricCriterionInput[];
}

function newQuestionKey() {
  return crypto.randomUUID();
}

function blankQuestion(title: string): QuestionFormState {
  return {
    key: newQuestionKey(),
    id: null,
    title,
    skillOverride: null,
    difficultyOverride: "",
    instructions: "",
    requirements: "",
    constraints: "",
    configurationJson: "",
    points: 100,
    languages: [],
    testCases: [],
    rubricCriteria: [],
  };
}

export function PracticalAssessmentEditorPage() {
  const { id } = useParams<{ id: string }>();
  const isEditing = Boolean(id);
  const navigate = useNavigate();
  const toast = useToast();

  const existing = useAsync(() => (id ? getAdminPracticalAssessment(id) : Promise.resolve(null)), [id]);

  const [title, setTitle] = useState("");
  const [skill, setSkill] = useState<SkillResponse | null>(null);
  const [practicalType, setPracticalType] = useState<PracticalType>("CODING");
  const [workspaceType, setWorkspaceType] = useState<WorkspaceType>("CODE_EDITOR");
  const [difficulty, setDifficulty] = useState<Difficulty>("MEDIUM");
  const [timeLimitMinutes, setTimeLimitMinutes] = useState(30);
  const [instructions, setInstructions] = useState("");
  const [evaluationType, setEvaluationType] = useState<EvaluationType>("MANUAL");
  const [integrityPolicy, setIntegrityPolicy] = useState<EditableIntegrityPolicy>(DEFAULT_INTEGRITY_POLICY);
  const [questions, setQuestions] = useState<QuestionFormState[]>([blankQuestion("Question 1")]);
  const [initialized, setInitialized] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [previewKey, setPreviewKey] = useState<string | null>(null);
  const [testQuestionKey, setTestQuestionKey] = useState<string | null>(null);

  const loaded = existing.data ?? undefined;

  useEffect(() => {
    if (loaded && !initialized) {
      setTitle(loaded.title);
      setSkill({
        id: loaded.skillId,
        name: loaded.skillName,
        category: "",
        iconSlug: null,
        iconType: "LUCIDE",
      });
      setPracticalType(loaded.practicalType);
      setWorkspaceType(loaded.workspaceType);
      setDifficulty(loaded.difficulty);
      setTimeLimitMinutes(loaded.timeLimitMinutes);
      setInstructions(loaded.instructions);
      setEvaluationType(loaded.evaluationType);
      setIntegrityPolicy(extractIntegrityPolicy(loaded.configurationJson));
      setQuestions(
        loaded.questions.length > 0
          ? loaded.questions.map((q) => ({
              key: newQuestionKey(),
              id: q.id,
              title: q.title,
              skillOverride: q.skillId ? { id: q.skillId, name: q.skillName ?? "", category: "", iconSlug: null, iconType: "LUCIDE" } : null,
              difficultyOverride: q.difficulty ?? "",
              instructions: q.instructions,
              requirements: q.requirements ?? "",
              constraints: q.constraints ?? "",
              configurationJson: q.configurationJson ?? "",
              points: q.points,
              languages: q.languages.map((l) => ({ language: l.language, starterCode: l.starterCode ?? "" })),
              testCases: q.testCases.map((tc) => ({
                input: tc.input,
                expectedOutput: tc.expectedOutput,
                hidden: tc.hidden,
                displayOrder: tc.displayOrder,
                comparisonMode: tc.comparisonMode,
              })),
              rubricCriteria: q.rubricCriteria.map((rc) => ({ criterion: rc.criterion, maxPoints: rc.maxPoints, displayOrder: rc.displayOrder })),
            }))
          : [blankQuestion("Question 1")]
      );
      setInitialized(true);
    }
  }, [loaded, initialized]);

  function updateQuestion(key: string, patch: Partial<QuestionFormState>) {
    setQuestions((prev) => prev.map((q) => (q.key === key ? { ...q, ...patch } : q)));
  }

  function addQuestion() {
    setQuestions((prev) => [...prev, blankQuestion(`Question ${prev.length + 1}`)]);
  }

  function duplicateQuestion(key: string) {
    setQuestions((prev) => {
      const index = prev.findIndex((q) => q.key === key);
      if (index === -1) return prev;
      const clone: QuestionFormState = {
        ...prev[index],
        key: newQuestionKey(),
        id: null,
        title: `${prev[index].title} (Copy)`,
      };
      return [...prev.slice(0, index + 1), clone, ...prev.slice(index + 1)];
    });
  }

  function removeQuestion(key: string) {
    setQuestions((prev) => (prev.length <= 1 ? prev : prev.filter((q) => q.key !== key)));
  }

  function moveQuestion(key: string, direction: -1 | 1) {
    setQuestions((prev) => {
      const index = prev.findIndex((q) => q.key === key);
      const target = index + direction;
      if (index === -1 || target < 0 || target >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  function toggleLanguage(key: string, lang: CodingLanguage) {
    setQuestions((prev) =>
      prev.map((q) =>
        q.key === key
          ? {
              ...q,
              languages: q.languages.some((l) => l.language === lang)
                ? q.languages.filter((l) => l.language !== lang)
                : [...q.languages, { language: lang, starterCode: "" }],
            }
          : q
      )
    );
  }

  function updateStarterCode(key: string, lang: CodingLanguage, code: string) {
    setQuestions((prev) =>
      prev.map((q) =>
        q.key === key ? { ...q, languages: q.languages.map((l) => (l.language === lang ? { ...l, starterCode: code } : l)) } : q
      )
    );
  }

  function addTestCase(key: string) {
    setQuestions((prev) =>
      prev.map((q) =>
        q.key === key
          ? { ...q, testCases: [...q.testCases, { input: "", expectedOutput: "", hidden: false, displayOrder: q.testCases.length, comparisonMode: "NORMALIZE_NEWLINES" }] }
          : q
      )
    );
  }

  function updateTestCase(key: string, index: number, patch: Partial<PracticalTestCaseInput>) {
    setQuestions((prev) =>
      prev.map((q) => (q.key === key ? { ...q, testCases: q.testCases.map((tc, i) => (i === index ? { ...tc, ...patch } : tc)) } : q))
    );
  }

  function removeTestCase(key: string, index: number) {
    setQuestions((prev) => prev.map((q) => (q.key === key ? { ...q, testCases: q.testCases.filter((_, i) => i !== index) } : q)));
  }

  function addRubricCriterion(key: string) {
    setQuestions((prev) =>
      prev.map((q) => (q.key === key ? { ...q, rubricCriteria: [...q.rubricCriteria, { criterion: "", maxPoints: 10, displayOrder: q.rubricCriteria.length }] } : q))
    );
  }

  function updateRubricCriterion(key: string, index: number, patch: Partial<PracticalRubricCriterionInput>) {
    setQuestions((prev) =>
      prev.map((q) => (q.key === key ? { ...q, rubricCriteria: q.rubricCriteria.map((rc, i) => (i === index ? { ...rc, ...patch } : rc)) } : q))
    );
  }

  function removeRubricCriterion(key: string, index: number) {
    setQuestions((prev) => prev.map((q) => (q.key === key ? { ...q, rubricCriteria: q.rubricCriteria.filter((_, i) => i !== index) } : q)));
  }

  // Merges the checkbox-driven integrity policy back into the assessment's configurationJson.
  function buildConfigurationJson(): string | undefined {
    const isDefault =
      integrityPolicy.allowCopy && integrityPolicy.allowPaste && integrityPolicy.allowCut && !integrityPolicy.requireFullscreen;
    return isDefault ? undefined : JSON.stringify({ integrityPolicy });
  }

  function buildQuestionsPayload(): PracticalQuestionInput[] {
    return questions.map((q, i) => ({
      id: q.id,
      title: q.title.trim() || `Question ${i + 1}`,
      skillId: q.skillOverride?.id ?? null,
      difficulty: q.difficultyOverride || null,
      instructions: q.instructions,
      requirements: q.requirements.trim() || undefined,
      constraints: q.constraints.trim() || undefined,
      configurationJson: q.configurationJson.trim() || undefined,
      points: q.points,
      displayOrder: i,
      languages: practicalType === "CODING" ? q.languages : undefined,
      testCases: practicalType === "CODING" || practicalType === "SQL" ? q.testCases : undefined,
      rubricCriteria: q.rubricCriteria.length > 0 ? q.rubricCriteria : undefined,
    }));
  }

  function buildPayload(): PracticalAssessmentRequest {
    return {
      title: title.trim(),
      skillId: skill?.id ?? "",
      practicalType,
      workspaceType,
      difficulty,
      timeLimitMinutes,
      instructions: instructions.trim(),
      evaluationType,
      configurationJson: buildConfigurationJson(),
      questions: buildQuestionsPayload(),
    };
  }

  async function handleSave() {
    if (!title.trim() || !skill || !instructions.trim()) {
      toast.error("Title, skill, and overview instructions are required.");
      return;
    }
    if (questions.some((q) => !q.title.trim() || !q.instructions.trim())) {
      toast.error("Every question needs a title and instructions.");
      return;
    }
    setSubmitting(true);
    try {
      const payload = buildPayload();
      const result = isEditing && id ? await updatePracticalAssessment(id, payload) : await createPracticalAssessment(payload);
      toast.success(isEditing ? "Practical assessment updated." : "Practical assessment created.");
      navigate(ROUTES.adminPracticalAssessmentDetail(result.id), { replace: true });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function runTransition(action: (id: string) => Promise<PracticalAssessmentDetail>, successMessage: string) {
    if (!id) return;
    setSubmitting(true);
    try {
      await action(id);
      toast.success(successMessage);
      existing.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleCreateNewVersion() {
    if (!id) return;
    setSubmitting(true);
    try {
      const next = await createNewPracticalAssessmentVersion(id);
      toast.success("New draft version created.");
      navigate(ROUTES.adminPracticalAssessmentDetail(next.id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (isEditing && existing.loading) {
    return <LoadingState label="Loading practical assessment..." />;
  }
  if (isEditing && existing.error) {
    return <ErrorState message={existing.error.message} onRetry={existing.refetch} />;
  }

  const readOnly = Boolean(loaded && (loaded.status === "PUBLISHED" || loaded.status === "ARCHIVED"));
  const previewQuestion = questions.find((q) => q.key === previewKey) ?? null;
  const testQuestion = questions.find((q) => q.key === testQuestionKey) ?? null;
  const PreviewWorkspace = WORKSPACE_REGISTRY[workspaceType];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{isEditing ? "Edit Practical Assessment" : "New Practical Assessment"}</h1>
          {loaded ? <Badge variant={assessmentStatusBadgeVariant(loaded.status)} className="mt-1">{loaded.status}</Badge> : null}
        </div>
        <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.adminPracticalAssessments)}>
          Back
        </Button>
      </div>

      {readOnly ? (
        <div className="rounded-lg border border-border p-4 text-sm text-muted-foreground">
          This assessment is {loaded?.status} and can't be edited directly — use "Create New Version" instead.
          <div className="mt-3 flex flex-wrap gap-2">
            {loaded?.status === "PUBLISHED" ? (
              <Button size="sm" onClick={handleCreateNewVersion} disabled={submitting}>
                Create New Version
              </Button>
            ) : null}
            {loaded?.status !== "ARCHIVED" ? (
              <Button size="sm" variant="outline" onClick={() => runTransition(archivePracticalAssessment, "Archived.")} disabled={submitting}>
                Archive
              </Button>
            ) : null}
          </div>
        </div>
      ) : (
        <div className="space-y-6">
          {/* Basic information */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <h2 className="text-sm font-semibold text-foreground">Basic Information</h2>
            <FormField label="Title" htmlFor="pa-title">
              <Input id="pa-title" value={title} onChange={(e) => setTitle(e.target.value)} disabled={submitting} />
            </FormField>
            <FormField label="Skill" htmlFor="pa-skill" hint="The assessment's default skill — individual questions can override it.">
              <QuestionSkillPicker value={skill} onChange={setSkill} disabled={submitting} />
            </FormField>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <FormField label="Type" htmlFor="pa-type">
                <select id="pa-type" value={practicalType} onChange={(e) => setPracticalType(e.target.value as PracticalType)} className={QB_SELECT_CLASS} disabled={submitting}>
                  {PRACTICAL_TYPE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Difficulty" htmlFor="pa-difficulty" hint="Default — questions can override it.">
                <select id="pa-difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)} className={QB_SELECT_CLASS} disabled={submitting}>
                  {DIFFICULTY_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Time limit (minutes)" htmlFor="pa-time" hint="Total for the whole assessment.">
                <Input
                  id="pa-time"
                  type="number"
                  min={1}
                  value={timeLimitMinutes}
                  onChange={(e) => setTimeLimitMinutes(Number(e.target.value) || 1)}
                  disabled={submitting}
                />
              </FormField>
            </div>
          </section>

          {/* Overview */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <h2 className="text-sm font-semibold text-foreground">Overview</h2>
            <FormField label="Assessment overview" htmlFor="pa-instructions" hint='Shown before the student starts, e.g. "Complete all 5 questions before time runs out."'>
              <Textarea id="pa-instructions" rows={3} value={instructions} onChange={(e) => setInstructions(e.target.value)} disabled={submitting} />
            </FormField>
          </section>

          {/* Environment */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <h2 className="text-sm font-semibold text-foreground">Environment</h2>
            <FormField label="Workspace" htmlFor="pa-workspace" hint="Shared by every question in this assessment.">
              <select id="pa-workspace" value={workspaceType} onChange={(e) => setWorkspaceType(e.target.value as WorkspaceType)} className={QB_SELECT_CLASS} disabled={submitting}>
                {WORKSPACE_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
          </section>

          {/* Evaluation */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <h2 className="text-sm font-semibold text-foreground">Evaluation</h2>
            <FormField label="Evaluation type" htmlFor="pa-eval-type">
              <select id="pa-eval-type" value={evaluationType} onChange={(e) => setEvaluationType(e.target.value as EvaluationType)} className={QB_SELECT_CLASS} disabled={submitting}>
                {EVALUATION_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
          </section>

          {/* Integrity policy (Phase 7.6) */}
          <section className="space-y-3 rounded-xl border border-border p-4">
            <div>
              <h2 className="text-sm font-semibold text-foreground">Integrity Policy</h2>
              <p className="text-xs text-muted-foreground">
                Controls what the attempt workspace allows and what gets flagged for review. Leave everything allowed for a
                practice assessment; restrict for a proctored one. Applies to the whole assessment.
              </p>
            </div>
            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              <label className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={integrityPolicy.allowCopy}
                  onChange={(e) => setIntegrityPolicy((prev) => ({ ...prev, allowCopy: e.target.checked }))}
                  disabled={submitting}
                />
                Allow copy
              </label>
              <label className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={integrityPolicy.allowPaste}
                  onChange={(e) => setIntegrityPolicy((prev) => ({ ...prev, allowPaste: e.target.checked }))}
                  disabled={submitting}
                />
                Allow paste
              </label>
              <label className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={integrityPolicy.allowCut}
                  onChange={(e) => setIntegrityPolicy((prev) => ({ ...prev, allowCut: e.target.checked }))}
                  disabled={submitting}
                />
                Allow cut
              </label>
              <label className="flex items-center gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={integrityPolicy.requireFullscreen}
                  onChange={(e) => setIntegrityPolicy((prev) => ({ ...prev, requireFullscreen: e.target.checked }))}
                  disabled={submitting}
                />
                Require fullscreen
              </label>
            </div>
          </section>

          {/* Questions */}
          <section className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">Questions ({questions.length})</h2>
              <Button variant="outline" size="sm" onClick={addQuestion} disabled={submitting}>
                <Plus className="size-4" /> Add Question
              </Button>
            </div>

            {questions.map((q, qi) => (
              <div key={q.key} className="space-y-4 rounded-xl border border-border p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="text-sm font-semibold text-foreground">Question {qi + 1}</h3>
                  <div className="flex flex-wrap items-center gap-1.5">
                    <Button variant="ghost" size="icon-sm" onClick={() => moveQuestion(q.key, -1)} disabled={submitting || qi === 0} aria-label="Move question up">
                      <ChevronUp className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => moveQuestion(q.key, 1)} disabled={submitting || qi === questions.length - 1} aria-label="Move question down">
                      <ChevronDown className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => duplicateQuestion(q.key)} disabled={submitting} aria-label="Duplicate question">
                      <Copy className="size-4" />
                    </Button>
                    {q.id && (practicalType === "CODING" || practicalType === "SQL") ? (
                      <Button variant="ghost" size="icon-sm" onClick={() => setTestQuestionKey(q.key)} disabled={submitting} aria-label="Test question">
                        <FlaskConical className="size-4" />
                      </Button>
                    ) : null}
                    <Button variant="ghost" size="icon-sm" onClick={() => setPreviewKey(q.key)} disabled={submitting} aria-label="Preview question">
                      <Eye className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => removeQuestion(q.key)} disabled={submitting || questions.length <= 1} aria-label="Remove question">
                      <X className="size-4" />
                    </Button>
                  </div>
                </div>

                <FormField label="Question title" htmlFor={`q-title-${q.key}`}>
                  <Input id={`q-title-${q.key}`} value={q.title} onChange={(e) => updateQuestion(q.key, { title: e.target.value })} disabled={submitting} />
                </FormField>

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                  <FormField label="Skill (override)" htmlFor={`q-skill-${q.key}`} hint="Leave blank to use the assessment's skill.">
                    <QuestionSkillPicker value={q.skillOverride} onChange={(s) => updateQuestion(q.key, { skillOverride: s })} disabled={submitting} />
                  </FormField>
                  <FormField label="Difficulty (override)" htmlFor={`q-diff-${q.key}`}>
                    <select
                      id={`q-diff-${q.key}`}
                      value={q.difficultyOverride}
                      onChange={(e) => updateQuestion(q.key, { difficultyOverride: e.target.value as Difficulty | "" })}
                      className={QB_SELECT_CLASS}
                      disabled={submitting}
                    >
                      <option value="">Inherit ({difficulty})</option>
                      {DIFFICULTY_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </FormField>
                  <FormField label="Points" htmlFor={`q-points-${q.key}`}>
                    <Input
                      id={`q-points-${q.key}`}
                      type="number"
                      min={1}
                      value={q.points}
                      onChange={(e) => updateQuestion(q.key, { points: Number(e.target.value) || 1 })}
                      disabled={submitting}
                    />
                  </FormField>
                </div>

                <FormField label="Problem / task description" htmlFor={`q-inst-${q.key}`}>
                  <Textarea id={`q-inst-${q.key}`} rows={5} value={q.instructions} onChange={(e) => updateQuestion(q.key, { instructions: e.target.value })} disabled={submitting} />
                </FormField>
                <FormField label="Requirements" htmlFor={`q-req-${q.key}`} hint="Optional.">
                  <Textarea id={`q-req-${q.key}`} rows={3} value={q.requirements} onChange={(e) => updateQuestion(q.key, { requirements: e.target.value })} disabled={submitting} />
                </FormField>
                <FormField label="Constraints" htmlFor={`q-con-${q.key}`} hint="Optional — e.g. input format/limits for CODING.">
                  <Textarea id={`q-con-${q.key}`} rows={3} value={q.constraints} onChange={(e) => updateQuestion(q.key, { constraints: e.target.value })} disabled={submitting} />
                </FormField>

                {practicalType === "CODING" ? (
                  <div className="space-y-3">
                    <p className="text-sm font-medium text-foreground">Supported languages</p>
                    <div className="flex flex-wrap gap-2">
                      {ALL_CODING_LANGUAGES.map((lang) => (
                        <button
                          key={lang}
                          type="button"
                          onClick={() => toggleLanguage(q.key, lang)}
                          disabled={submitting}
                          className={`rounded-full px-3 py-1.5 text-xs font-medium ${
                            q.languages.some((l) => l.language === lang) ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                          }`}
                        >
                          {CODING_LANGUAGE_LABEL[lang]}
                        </button>
                      ))}
                    </div>
                    {q.languages.map((l) => (
                      <FormField key={l.language} label={`${CODING_LANGUAGE_LABEL[l.language]} starter code`} htmlFor={`starter-${q.key}-${l.language}`}>
                        <Textarea
                          id={`starter-${q.key}-${l.language}`}
                          rows={4}
                          className="font-mono text-xs"
                          value={l.starterCode}
                          onChange={(e) => updateStarterCode(q.key, l.language, e.target.value)}
                          disabled={submitting}
                        />
                      </FormField>
                    ))}
                  </div>
                ) : (
                  <FormField
                    label="Configuration (JSON)"
                    htmlFor={`q-config-${q.key}`}
                    hint="Optional — per-question settings (e.g. SQL schema description, web starter template, UI/UX reference image URL + submission mode)."
                  >
                    <Textarea
                      id={`q-config-${q.key}`}
                      rows={4}
                      className="font-mono text-xs"
                      value={q.configurationJson}
                      onChange={(e) => updateQuestion(q.key, { configurationJson: e.target.value })}
                      disabled={submitting}
                    />
                  </FormField>
                )}

                {/* Test cases (CODING/SQL) */}
                {practicalType === "CODING" || practicalType === "SQL" ? (
                  <div className="space-y-3 rounded-lg border border-border p-3">
                    <div className="flex items-center justify-between">
                      <h4 className="text-xs font-semibold text-foreground">Test Cases</h4>
                      <Button variant="outline" size="sm" onClick={() => addTestCase(q.key)} disabled={submitting}>
                        <Plus className="size-4" /> Add test case
                      </Button>
                    </div>
                    {practicalType === "SQL" ? (
                      <p className="text-xs text-muted-foreground">
                        Each test case seeds its own fresh, isolated sandbox database. "Input" is the seed SQL (INSERT/setup);
                        "Expected output" is the reference query. Add <code>{"{\"sqlOrderedComparison\":true}"}</code> to this
                        question's Configuration (JSON) above if row order matters.
                      </p>
                    ) : null}
                    {q.testCases.map((tc, i) => (
                      <div key={i} className="space-y-2 rounded-lg border border-border p-3">
                        <div className="flex items-center justify-between">
                          <label className="flex items-center gap-2 text-xs font-medium text-foreground">
                            <input type="checkbox" checked={tc.hidden} onChange={(e) => updateTestCase(q.key, i, { hidden: e.target.checked })} disabled={submitting} />
                            Hidden test case
                          </label>
                          <Button variant="ghost" size="icon-sm" onClick={() => removeTestCase(q.key, i)} disabled={submitting} aria-label="Remove test case">
                            <X className="size-4" />
                          </Button>
                        </div>
                        <Textarea
                          placeholder={practicalType === "SQL" ? "Seed SQL (INSERT/setup)" : "Input"}
                          rows={2}
                          className="font-mono text-xs"
                          value={tc.input}
                          onChange={(e) => updateTestCase(q.key, i, { input: e.target.value })}
                          disabled={submitting}
                        />
                        <Textarea
                          placeholder={practicalType === "SQL" ? "Reference query (correct answer)" : "Expected output"}
                          rows={2}
                          className="font-mono text-xs"
                          value={tc.expectedOutput}
                          onChange={(e) => updateTestCase(q.key, i, { expectedOutput: e.target.value })}
                          disabled={submitting}
                        />
                        {practicalType === "CODING" ? (
                          <select
                            value={tc.comparisonMode ?? "NORMALIZE_NEWLINES"}
                            onChange={(e) => updateTestCase(q.key, i, { comparisonMode: e.target.value as PracticalTestCaseInput["comparisonMode"] })}
                            className={QB_SELECT_CLASS}
                            disabled={submitting}
                          >
                            <option value="NORMALIZE_NEWLINES">Normalize newlines (default)</option>
                            <option value="TRIM_WHITESPACE">Trim whitespace only</option>
                            <option value="EXACT">Exact match</option>
                          </select>
                        ) : null}
                      </div>
                    ))}
                    {q.testCases.length === 0 ? <p className="text-xs text-muted-foreground">No test cases yet.</p> : null}
                  </div>
                ) : null}

                {/* Rubric */}
                <div className="space-y-3 rounded-lg border border-border p-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-semibold text-foreground">Rubric</h4>
                    <Button variant="outline" size="sm" onClick={() => addRubricCriterion(q.key)} disabled={submitting}>
                      <Plus className="size-4" /> Add criterion
                    </Button>
                  </div>
                  {q.rubricCriteria.map((rc, i) => (
                    <div key={i} className="flex items-center gap-2">
                      <Input placeholder="Criterion" value={rc.criterion} onChange={(e) => updateRubricCriterion(q.key, i, { criterion: e.target.value })} disabled={submitting} className="flex-1" />
                      <Input
                        type="number"
                        min={1}
                        value={rc.maxPoints}
                        onChange={(e) => updateRubricCriterion(q.key, i, { maxPoints: Number(e.target.value) || 0 })}
                        disabled={submitting}
                        className="w-24"
                      />
                      <Button variant="ghost" size="icon-sm" onClick={() => removeRubricCriterion(q.key, i)} disabled={submitting} aria-label="Remove criterion">
                        <X className="size-4" />
                      </Button>
                    </div>
                  ))}
                  {q.rubricCriteria.length > 0 ? (
                    <p className="text-xs text-muted-foreground">
                      Total: {q.rubricCriteria.reduce((sum, rc) => sum + rc.maxPoints, 0)} / 100 (must equal 100 to publish)
                    </p>
                  ) : (
                    <p className="text-xs text-muted-foreground">No rubric — a direct 0-{q.points} score will be entered at evaluation time instead.</p>
                  )}
                </div>
              </div>
            ))}
          </section>

          <div className="flex flex-wrap gap-2 border-t border-border pt-4">
            <Button onClick={handleSave} disabled={submitting}>
              {submitting ? "Saving..." : isEditing ? "Save Changes" : "Save Draft"}
            </Button>
            {isEditing && loaded?.status === "DRAFT" ? (
              <Button variant="outline" onClick={() => runTransition(submitPracticalAssessmentForReview, "Submitted for review.")} disabled={submitting}>
                Submit for Review
              </Button>
            ) : null}
            {isEditing && loaded?.status === "REVIEW" ? (
              <Button variant="outline" onClick={() => runTransition(publishPracticalAssessment, "Published.")} disabled={submitting}>
                Publish
              </Button>
            ) : null}
          </div>
        </div>
      )}

      <Dialog open={Boolean(previewQuestion)} onOpenChange={(open) => !open && setPreviewKey(null)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Workspace Preview{previewQuestion ? ` — ${previewQuestion.title || "Untitled"}` : ""}</DialogTitle>
          </DialogHeader>
          {previewQuestion ? (
            <PreviewWorkspace
              assessment={{
                instructions: previewQuestion.instructions,
                requirements: previewQuestion.requirements || null,
                constraints: previewQuestion.constraints || null,
                configurationJson: previewQuestion.configurationJson || null,
                languages: previewQuestion.languages.map((l, i) => ({ id: `preview-${i}`, language: l.language, starterCode: l.starterCode })),
                publicTestCases: previewQuestion.testCases
                  .filter((tc) => !tc.hidden)
                  .map((tc, i) => ({ id: `preview-${i}`, input: tc.input, expectedOutput: tc.expectedOutput, displayOrder: tc.displayOrder })),
              }}
              mode="preview"
            />
          ) : null}
        </DialogContent>
      </Dialog>

      {id && testQuestion?.id ? (
        <AdminTestQuestionDialog
          open={Boolean(testQuestionKey)}
          onOpenChange={(open) => !open && setTestQuestionKey(null)}
          assessmentId={id}
          questionId={testQuestion.id}
          practicalType={practicalType}
          availableLanguages={testQuestion.languages.map((l) => l.language)}
        />
      ) : null}
    </div>
  );
}
