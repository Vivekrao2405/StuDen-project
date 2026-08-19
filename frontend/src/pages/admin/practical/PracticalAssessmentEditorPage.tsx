import { Eye, FlaskConical, Plus, X } from "lucide-react";
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

// Phase 7.6 Assessment Integrity policy lives inside configurationJson's `integrityPolicy` key
// (see backend IntegrityPolicyResolver) rather than as its own column — this reads/writes just
// that key without disturbing whatever else an admin has typed into the raw JSON textarea for
// non-CODING types (e.g. sqlOrderedComparison).
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

// The raw textarea (non-CODING types) shouldn't also show the integrityPolicy key it no longer
// controls -- the checkboxes below are now the only editor for it.
function stripIntegrityPolicy(configurationJson: string | null): string {
  const config = parseConfig(configurationJson);
  delete config.integrityPolicy;
  return Object.keys(config).length > 0 ? JSON.stringify(config, null, 2) : "";
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
  const [requirements, setRequirements] = useState("");
  const [constraints, setConstraints] = useState("");
  const [evaluationType, setEvaluationType] = useState<EvaluationType>("MANUAL");
  const [configurationJson, setConfigurationJson] = useState("");
  const [integrityPolicy, setIntegrityPolicy] = useState<EditableIntegrityPolicy>(DEFAULT_INTEGRITY_POLICY);
  const [languages, setLanguages] = useState<PracticalCodingLanguageInput[]>([]);
  const [testCases, setTestCases] = useState<PracticalTestCaseInput[]>([]);
  const [rubricCriteria, setRubricCriteria] = useState<PracticalRubricCriterionInput[]>([]);
  const [initialized, setInitialized] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [testQuestionOpen, setTestQuestionOpen] = useState(false);

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
      setRequirements(loaded.requirements ?? "");
      setConstraints(loaded.constraints ?? "");
      setEvaluationType(loaded.evaluationType);
      setConfigurationJson(stripIntegrityPolicy(loaded.configurationJson));
      setIntegrityPolicy(extractIntegrityPolicy(loaded.configurationJson));
      setLanguages(loaded.languages.map((l) => ({ language: l.language, starterCode: l.starterCode ?? "" })));
      setTestCases(
        loaded.testCases.map((tc) => ({
          input: tc.input,
          expectedOutput: tc.expectedOutput,
          hidden: tc.hidden,
          displayOrder: tc.displayOrder,
          comparisonMode: tc.comparisonMode,
        }))
      );
      setRubricCriteria(loaded.rubricCriteria.map((rc) => ({ criterion: rc.criterion, maxPoints: rc.maxPoints, displayOrder: rc.displayOrder })));
      setInitialized(true);
    }
  }, [loaded, initialized]);

  function toggleLanguage(lang: CodingLanguage) {
    setLanguages((prev) =>
      prev.some((l) => l.language === lang)
        ? prev.filter((l) => l.language !== lang)
        : [...prev, { language: lang, starterCode: "" }]
    );
  }

  function updateStarterCode(lang: CodingLanguage, code: string) {
    setLanguages((prev) => prev.map((l) => (l.language === lang ? { ...l, starterCode: code } : l)));
  }

  function addTestCase() {
    setTestCases((prev) => [
      ...prev,
      { input: "", expectedOutput: "", hidden: false, displayOrder: prev.length, comparisonMode: "NORMALIZE_NEWLINES" },
    ]);
  }

  function updateTestCase(index: number, patch: Partial<PracticalTestCaseInput>) {
    setTestCases((prev) => prev.map((tc, i) => (i === index ? { ...tc, ...patch } : tc)));
  }

  function removeTestCase(index: number) {
    setTestCases((prev) => prev.filter((_, i) => i !== index));
  }

  function addRubricCriterion() {
    setRubricCriteria((prev) => [...prev, { criterion: "", maxPoints: 10, displayOrder: prev.length }]);
  }

  function updateRubricCriterion(index: number, patch: Partial<PracticalRubricCriterionInput>) {
    setRubricCriteria((prev) => prev.map((rc, i) => (i === index ? { ...rc, ...patch } : rc)));
  }

  function removeRubricCriterion(index: number) {
    setRubricCriteria((prev) => prev.filter((_, i) => i !== index));
  }

  // Merges the checkbox-driven integrity policy back into whatever raw JSON the admin typed for
  // non-CODING types — never overwrites unrelated keys like sqlOrderedComparison. Returns null on
  // malformed raw JSON so the caller can stop the save instead of silently dropping it.
  function buildConfigurationJson(): string | undefined | null {
    let base: Record<string, unknown> = {};
    const raw = configurationJson.trim();
    if (raw) {
      try {
        const parsed: unknown = JSON.parse(raw);
        if (!parsed || typeof parsed !== "object") return null;
        base = parsed as Record<string, unknown>;
      } catch {
        return null;
      }
    }
    const isDefault =
      integrityPolicy.allowCopy && integrityPolicy.allowPaste && integrityPolicy.allowCut && !integrityPolicy.requireFullscreen;
    if (isDefault) {
      delete base.integrityPolicy;
    } else {
      base.integrityPolicy = integrityPolicy;
    }
    return Object.keys(base).length > 0 ? JSON.stringify(base) : undefined;
  }

  function buildPayload(configJson: string | undefined): PracticalAssessmentRequest {
    return {
      title: title.trim(),
      skillId: skill?.id ?? "",
      practicalType,
      workspaceType,
      difficulty,
      timeLimitMinutes,
      instructions: instructions.trim(),
      requirements: requirements.trim() || undefined,
      constraints: constraints.trim() || undefined,
      evaluationType,
      configurationJson: configJson,
      languages: practicalType === "CODING" ? languages : undefined,
      testCases: practicalType === "CODING" || practicalType === "SQL" ? testCases : undefined,
      rubricCriteria: rubricCriteria.length > 0 ? rubricCriteria : undefined,
    };
  }

  async function handleSave() {
    if (!title.trim() || !skill || !instructions.trim()) {
      toast.error("Title, skill, and instructions are required.");
      return;
    }
    const configJson = buildConfigurationJson();
    if (configJson === null) {
      toast.error("Configuration (JSON) isn't valid JSON.");
      return;
    }
    setSubmitting(true);
    try {
      const payload = buildPayload(configJson);
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

  const previewAssessment = {
    id: loaded?.id ?? "preview",
    title: title || "Untitled",
    skillId: skill?.id ?? "",
    skillName: skill?.name ?? "",
    practicalType,
    workspaceType,
    difficulty,
    timeLimitMinutes,
    instructions,
    requirements: requirements || null,
    constraints: constraints || null,
    configurationJson: configurationJson || null,
    languages: languages.map((l, i) => ({ id: `preview-${i}`, language: l.language, starterCode: l.starterCode })),
    publicTestCases: testCases.filter((tc) => !tc.hidden).map((tc, i) => ({ id: `preview-${i}`, input: tc.input, expectedOutput: tc.expectedOutput, displayOrder: tc.displayOrder })),
    rubricCriteria: rubricCriteria.map((rc, i) => ({ id: `preview-${i}`, criterion: rc.criterion, maxPoints: rc.maxPoints, displayOrder: rc.displayOrder })),
  };
  const PreviewWorkspace = WORKSPACE_REGISTRY[workspaceType];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{isEditing ? "Edit Practical Assessment" : "New Practical Assessment"}</h1>
          {loaded ? <Badge variant={assessmentStatusBadgeVariant(loaded.status)} className="mt-1">{loaded.status}</Badge> : null}
        </div>
        <div className="flex flex-wrap gap-2">
          {isEditing && id && (practicalType === "CODING" || practicalType === "SQL") ? (
            <Button variant="outline" size="sm" onClick={() => setTestQuestionOpen(true)}>
              <FlaskConical className="size-4" /> Test Question
            </Button>
          ) : null}
          <Button variant="outline" size="sm" onClick={() => setPreviewOpen(true)}>
            <Eye className="size-4" /> Preview Workspace
          </Button>
          <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.adminPracticalAssessments)}>
            Back
          </Button>
        </div>
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
            <FormField label="Skill" htmlFor="pa-skill">
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
              <FormField label="Difficulty" htmlFor="pa-difficulty">
                <select id="pa-difficulty" value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)} className={QB_SELECT_CLASS} disabled={submitting}>
                  {DIFFICULTY_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Time limit (minutes)" htmlFor="pa-time">
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

          {/* Instructions */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <h2 className="text-sm font-semibold text-foreground">Instructions</h2>
            <FormField label="Problem / task description" htmlFor="pa-instructions">
              <Textarea id="pa-instructions" rows={5} value={instructions} onChange={(e) => setInstructions(e.target.value)} disabled={submitting} />
            </FormField>
            <FormField label="Requirements" htmlFor="pa-requirements" hint="Optional.">
              <Textarea id="pa-requirements" rows={3} value={requirements} onChange={(e) => setRequirements(e.target.value)} disabled={submitting} />
            </FormField>
            <FormField label="Constraints" htmlFor="pa-constraints" hint="Optional — e.g. input format/limits for CODING.">
              <Textarea id="pa-constraints" rows={3} value={constraints} onChange={(e) => setConstraints(e.target.value)} disabled={submitting} />
            </FormField>
          </section>

          {/* Environment */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <h2 className="text-sm font-semibold text-foreground">Environment</h2>
            <FormField label="Workspace" htmlFor="pa-workspace">
              <select id="pa-workspace" value={workspaceType} onChange={(e) => setWorkspaceType(e.target.value as WorkspaceType)} className={QB_SELECT_CLASS} disabled={submitting}>
                {WORKSPACE_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>

            {practicalType === "CODING" ? (
              <div className="space-y-3">
                <p className="text-sm font-medium text-foreground">Supported languages</p>
                <div className="flex flex-wrap gap-2">
                  {ALL_CODING_LANGUAGES.map((lang) => (
                    <button
                      key={lang}
                      type="button"
                      onClick={() => toggleLanguage(lang)}
                      disabled={submitting}
                      className={`rounded-full px-3 py-1.5 text-xs font-medium ${
                        languages.some((l) => l.language === lang) ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {CODING_LANGUAGE_LABEL[lang]}
                    </button>
                  ))}
                </div>
                {languages.map((l) => (
                  <FormField key={l.language} label={`${CODING_LANGUAGE_LABEL[l.language]} starter code`} htmlFor={`starter-${l.language}`}>
                    <Textarea
                      id={`starter-${l.language}`}
                      rows={4}
                      className="font-mono text-xs"
                      value={l.starterCode}
                      onChange={(e) => updateStarterCode(l.language, e.target.value)}
                      disabled={submitting}
                    />
                  </FormField>
                ))}
              </div>
            ) : (
              <FormField label="Configuration (JSON)" htmlFor="pa-config" hint="Optional — per-workspace settings (e.g. SQL schema description, web starter template, UI/UX reference image URL + submission mode).">
                <Textarea id="pa-config" rows={4} className="font-mono text-xs" value={configurationJson} onChange={(e) => setConfigurationJson(e.target.value)} disabled={submitting} />
              </FormField>
            )}
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
                practice assessment; restrict for a proctored one.
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

          {/* Test cases (CODING/SQL) */}
          {practicalType === "CODING" || practicalType === "SQL" ? (
            <section className="space-y-4 rounded-xl border border-border p-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-foreground">Test Cases</h2>
                <Button variant="outline" size="sm" onClick={addTestCase} disabled={submitting}>
                  <Plus className="size-4" /> Add test case
                </Button>
              </div>
              {practicalType === "SQL" ? (
                <p className="text-xs text-muted-foreground">
                  Each test case seeds its own fresh, isolated sandbox database. "Input" is the seed SQL (INSERT/setup); "Expected
                  output" is the reference query — the known-correct answer, run against the same seed and diffed against the
                  student's query. The reference query is never shown to students. Add <code>{"{\"sqlOrderedComparison\":true}"}</code>{" "}
                  to Configuration (JSON) above if row order matters for this question.
                </p>
              ) : null}
              {testCases.map((tc, i) => (
                <div key={i} className="space-y-2 rounded-lg border border-border p-3">
                  <div className="flex items-center justify-between">
                    <label className="flex items-center gap-2 text-xs font-medium text-foreground">
                      <input type="checkbox" checked={tc.hidden} onChange={(e) => updateTestCase(i, { hidden: e.target.checked })} disabled={submitting} />
                      Hidden test case
                    </label>
                    <Button variant="ghost" size="icon-sm" onClick={() => removeTestCase(i)} disabled={submitting} aria-label="Remove test case">
                      <X className="size-4" />
                    </Button>
                  </div>
                  <Textarea
                    placeholder={practicalType === "SQL" ? "Seed SQL (INSERT/setup)" : "Input"}
                    rows={2}
                    className="font-mono text-xs"
                    value={tc.input}
                    onChange={(e) => updateTestCase(i, { input: e.target.value })}
                    disabled={submitting}
                  />
                  <Textarea
                    placeholder={practicalType === "SQL" ? "Reference query (correct answer)" : "Expected output"}
                    rows={2}
                    className="font-mono text-xs"
                    value={tc.expectedOutput}
                    onChange={(e) => updateTestCase(i, { expectedOutput: e.target.value })}
                    disabled={submitting}
                  />
                  {practicalType === "CODING" ? (
                    <select
                      value={tc.comparisonMode ?? "NORMALIZE_NEWLINES"}
                      onChange={(e) => updateTestCase(i, { comparisonMode: e.target.value as PracticalTestCaseInput["comparisonMode"] })}
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
              {testCases.length === 0 ? <p className="text-xs text-muted-foreground">No test cases yet.</p> : null}
            </section>
          ) : null}

          {/* Rubric */}
          <section className="space-y-4 rounded-xl border border-border p-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold text-foreground">Rubric</h2>
              <Button variant="outline" size="sm" onClick={addRubricCriterion} disabled={submitting}>
                <Plus className="size-4" /> Add criterion
              </Button>
            </div>
            {rubricCriteria.map((rc, i) => (
              <div key={i} className="flex items-center gap-2">
                <Input placeholder="Criterion" value={rc.criterion} onChange={(e) => updateRubricCriterion(i, { criterion: e.target.value })} disabled={submitting} className="flex-1" />
                <Input
                  type="number"
                  min={1}
                  value={rc.maxPoints}
                  onChange={(e) => updateRubricCriterion(i, { maxPoints: Number(e.target.value) || 0 })}
                  disabled={submitting}
                  className="w-24"
                />
                <Button variant="ghost" size="icon-sm" onClick={() => removeRubricCriterion(i)} disabled={submitting} aria-label="Remove criterion">
                  <X className="size-4" />
                </Button>
              </div>
            ))}
            {rubricCriteria.length > 0 ? (
              <p className="text-xs text-muted-foreground">
                Total: {rubricCriteria.reduce((sum, rc) => sum + rc.maxPoints, 0)} / 100 (must equal 100 to publish)
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">No rubric — a direct 0-100 score will be entered at evaluation time instead.</p>
            )}
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

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>Workspace Preview</DialogTitle>
          </DialogHeader>
          <PreviewWorkspace assessment={previewAssessment} mode="preview" />
        </DialogContent>
      </Dialog>

      {id && (practicalType === "CODING" || practicalType === "SQL") ? (
        <AdminTestQuestionDialog
          open={testQuestionOpen}
          onOpenChange={setTestQuestionOpen}
          assessmentId={id}
          practicalType={practicalType}
          availableLanguages={languages.map((l) => l.language)}
        />
      ) : null}
    </div>
  );
}
