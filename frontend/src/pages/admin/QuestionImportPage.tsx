import { AlertTriangle, CheckCircle2, Plus, Upload, X } from "lucide-react";
import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { confirmQuestionImport, parseQuestionImport } from "@/lib/api/endpoints/questionBank";
import type { Difficulty, ImportedOptionDraft, ImportedQuestionDraft, QuestionType, SkillResponse } from "@/lib/api/types";
import { isPlainTextContent, parseQuestionContent } from "@/lib/questionContent";
import { ROUTES } from "@/lib/routes";
import { DIFFICULTY_OPTIONS, QUESTION_TYPE_OPTIONS, difficultyLabel, questionTypeLabel } from "@/pages/admin/questionBankOptions";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { QuestionSkillPicker } from "@/pages/admin/QuestionSkillPicker";
import { QuestionTopicPicker } from "@/pages/admin/QuestionTopicPicker";

type Step = "upload" | "preview" | "success";

const TAG_FORMAT = /^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$/;

function revalidate(draft: ImportedQuestionDraft): ImportedQuestionDraft {
  // Duplicate flags (same ID reused in the file, or already exists in the Question Bank) are
  // computed server-side and can't be recomputed client-side — preserve them across an edit
  // rather than silently letting a duplicate row look "Ready" again. A fresh Parse or Remove from
  // Import is the only way to actually clear one.
  const errors: string[] = draft.errors.filter((e) => e.toLowerCase().startsWith("duplicate"));
  if (!draft.questionText.trim()) errors.push("Question text is required");
  if (draft.options.length < 2) errors.push("At least 2 options are required");
  const correctCount = draft.options.filter((o) => o.isCorrect).length;
  if (draft.questionType === "MCQ_MULTIPLE") {
    if (correctCount < 1) errors.push("At least 1 correct option is required");
  } else if (correctCount !== 1) {
    errors.push("Exactly 1 correct option is required");
  }
  if (!draft.difficulty) errors.push("Difficulty is required");
  const tag = (draft.tag ?? "").trim();
  if (!tag) {
    errors.push("Tag is required");
  } else if (tag.includes(",") || tag.includes(" ") || !TAG_FORMAT.test(tag)) {
    errors.push("Tag must be a single hierarchical string, e.g. python-sets-operators");
  }
  return { ...draft, errors };
}

function previewText(text: string): string {
  const plain = parseQuestionContent(text)
    .filter((s) => s.type === "text")
    .map((s) => s.value.trim())
    .join(" ")
    .trim();
  const display = plain || (isPlainTextContent(text) ? text : "[code question]");
  return display.length > 90 ? display.slice(0, 90) + "…" : display;
}

export function QuestionImportPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState<Step>("upload");
  const [file, setFile] = useState<File | null>(null);
  const [parsing, setParsing] = useState(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [fileName, setFileName] = useState("");
  const [rows, setRows] = useState<ImportedQuestionDraft[]>([]);
  const [removedIndices, setRemovedIndices] = useState<Set<number>>(new Set());
  const [skill, setSkill] = useState<SkillResponse | null>(null);
  const [topicId, setTopicId] = useState<string | null>(null);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [importing, setImporting] = useState(false);
  const [importError, setImportError] = useState<string | null>(null);
  const [importedCount, setImportedCount] = useState(0);

  const visibleRows = rows.filter((r) => !removedIndices.has(r.index));
  // A row is ready once it has no blocking errors AND a skill — either its own (resolved from a
  // "### SKILL" field) or the fallback picker below. Rows using the original template (no SKILL
  // field at all) always rely on the picker.
  const rowReady = (row: ImportedQuestionDraft) => row.errors.length === 0 && Boolean(row.skillId || skill);
  const validRows = visibleRows.filter(rowReady);
  const duplicateCount = visibleRows.filter((r) => r.duplicate).length;
  const needsFallbackSkill = visibleRows.some((r) => !r.skillId) && !skill;
  const canImport = visibleRows.length > 0 && validRows.length === visibleRows.length && !importing;

  const difficultyCounts: Record<Difficulty, number> = { EASY: 0, MEDIUM: 0, HARD: 0 };
  for (const row of visibleRows) {
    if (row.difficulty) difficultyCounts[row.difficulty]++;
  }
  const distinctTags = Array.from(new Set(visibleRows.map((r) => r.tag).filter((t): t is string => Boolean(t)))).sort();

  async function handleParse() {
    if (!file) return;
    setParsing(true);
    setParseError(null);
    try {
      const result = await parseQuestionImport(file);
      setFileName(result.fileName);
      setRows(result.questions);
      setRemovedIndices(new Set());
      setStep("preview");
    } catch (err) {
      setParseError(err instanceof ApiError ? err.message : "Couldn't parse that file. Please try again.");
    } finally {
      setParsing(false);
    }
  }

  function removeRow(index: number) {
    setRemovedIndices((prev) => new Set(prev).add(index));
    if (editingIndex === index) setEditingIndex(null);
  }

  function saveEdit(updated: ImportedQuestionDraft) {
    const revalidated = revalidate(updated);
    setRows((prev) => prev.map((r) => (r.index === updated.index ? revalidated : r)));
    setEditingIndex(null);
  }

  async function handleImport() {
    setImporting(true);
    setImportError(null);
    try {
      const result = await confirmQuestionImport({ skillId: skill?.id, topicId: topicId ?? undefined, questions: validRows });
      setImportedCount(result.importedCount);
      setStep("success");
      toast.success(`${result.importedCount} question${result.importedCount === 1 ? "" : "s"} imported.`);
    } catch (err) {
      setImportError(err instanceof ApiError ? err.message : "Import failed. Please try again.");
    } finally {
      setImporting(false);
    }
  }

  if (step === "success") {
    return (
      <div className="mx-auto max-w-lg space-y-4 py-12 text-center">
        <CheckCircle2 className="mx-auto size-12 text-primary" />
        <h1 className="text-xl font-bold text-foreground">Import Successful</h1>
        <p className="text-sm text-muted-foreground">
          {importedCount} question{importedCount === 1 ? "" : "s"} added to Question Bank.
        </p>
        <Button onClick={() => navigate(ROUTES.questionBank)}>View Question Bank</Button>
      </div>
    );
  }

  if (step === "upload") {
    return (
      <div className="mx-auto max-w-lg space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Import Questions</h1>
          <p className="text-sm text-muted-foreground">Upload a Markdown (.md) file to bulk-add questions to the Question Bank.</p>
        </div>

        <Card>
          <CardContent className="space-y-4">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="flex w-full flex-col items-center gap-2 rounded-xl border-2 border-dashed border-border p-8 text-center transition-colors hover:bg-muted/50"
            >
              <Upload className="size-8 text-muted-foreground" />
              <span className="text-sm font-medium text-foreground">{file ? file.name : "Upload Markdown File"}</span>
              <span className="text-xs text-muted-foreground">Supported format: .md</span>
            </button>
            <input
              ref={fileInputRef}
              type="file"
              accept=".md"
              className="hidden"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            {parseError ? <p className="text-sm text-destructive">{parseError}</p> : null}
            <Button className="w-full" onClick={handleParse} disabled={!file || parsing}>
              {parsing ? "Parsing..." : "Parse File"}
            </Button>
          </CardContent>
        </Card>
      </div>
    );
  }

  const editingRow = editingIndex === null ? null : (rows.find((r) => r.index === editingIndex) ?? null);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Import Preview</h1>
        <p className="text-sm text-muted-foreground">
          {fileName} — {visibleRows.length} question{visibleRows.length === 1 ? "" : "s"} detected · {validRows.length} ready ·{" "}
          {visibleRows.length - validRows.length} need{visibleRows.length - validRows.length === 1 ? "s" : ""} attention
          {duplicateCount > 0 ? ` (${duplicateCount} duplicate${duplicateCount === 1 ? "" : "s"})` : ""}
        </p>
      </div>

      <Card>
        <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-3">
          <div>
            <p className="text-xs font-medium text-muted-foreground">By difficulty</p>
            <p className="text-sm text-foreground">
              Easy: {difficultyCounts.EASY} · Medium: {difficultyCounts.MEDIUM} · Hard: {difficultyCounts.HARD}
            </p>
          </div>
          <div className="sm:col-span-2">
            <p className="text-xs font-medium text-muted-foreground">Tags ({distinctTags.length})</p>
            <div className="mt-1 flex flex-wrap gap-1">
              {distinctTags.length > 0 ? (
                distinctTags.map((t) => (
                  <Badge key={t} variant="outline">
                    {t}
                  </Badge>
                ))
              ) : (
                <span className="text-sm text-muted-foreground">—</span>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField
            label="Skill"
            htmlFor="import-skill"
            hint={
              needsFallbackSkill || !visibleRows.some((r) => r.skillId)
                ? "Used for any question that doesn't already name its own SKILL in the file."
                : "Optional — every question already has its own skill from the file."
            }
          >
            <QuestionSkillPicker value={skill} onChange={setSkill} />
          </FormField>
          <FormField label="Topic" htmlFor="import-topic" hint="Optional.">
            <QuestionTopicPicker skillId={skill?.id ?? null} value={topicId} onChange={setTopicId} />
          </FormField>
        </CardContent>
      </Card>

      <div className="overflow-x-auto rounded-xl border border-border">
        <table className="w-full text-sm">
          <thead className="bg-muted/50 text-left text-xs text-muted-foreground">
            <tr>
              <th className="px-3 py-2">#</th>
              <th className="px-3 py-2">ID</th>
              <th className="px-3 py-2">Question</th>
              <th className="px-3 py-2">Skill</th>
              <th className="px-3 py-2">Type</th>
              <th className="px-3 py-2">Difficulty</th>
              <th className="px-3 py-2">Tag</th>
              <th className="px-3 py-2">Status</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {visibleRows.map((row) => (
              <tr key={row.index} className="border-t border-border">
                <td className="px-3 py-2 text-muted-foreground">{row.index}</td>
                <td className="px-3 py-2 text-muted-foreground">{row.externalId || "—"}</td>
                <td
                  className="max-w-xs cursor-pointer truncate px-3 py-2 text-foreground hover:underline"
                  onClick={() => setEditingIndex(row.index)}
                >
                  {previewText(row.questionText) || <span className="text-muted-foreground">(empty)</span>}
                </td>
                <td className="px-3 py-2 text-muted-foreground">
                  {row.skillId ? (
                    row.skillName
                  ) : skill ? (
                    <span title={row.skillName ? `"${row.skillName}" wasn't found — using the fallback skill` : undefined}>
                      {skill.name}
                    </span>
                  ) : row.skillName ? (
                    <span className="text-destructive">"{row.skillName}" not found</span>
                  ) : (
                    <span className="text-destructive">Select a skill</span>
                  )}
                </td>
                <td className="px-3 py-2 text-muted-foreground">{questionTypeLabel(row.questionType)}</td>
                <td className="px-3 py-2 text-muted-foreground">{row.difficulty ? difficultyLabel(row.difficulty) : "—"}</td>
                <td className="px-3 py-2 text-muted-foreground">{row.tag || "—"}</td>
                <td className="px-3 py-2">
                  {rowReady(row) ? (
                    <Badge variant="default">
                      <CheckCircle2 className="size-3" /> Ready
                    </Badge>
                  ) : (
                    <Badge variant="destructive">
                      <AlertTriangle className="size-3" /> Needs attention
                    </Badge>
                  )}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center gap-1">
                    <Button variant="outline" size="sm" onClick={() => setEditingIndex(row.index)}>
                      Edit
                    </Button>
                    <Button variant="ghost" size="icon-sm" aria-label="Remove from import" onClick={() => removeRow(row.index)}>
                      <X />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {importError ? <p className="text-sm text-destructive">{importError}</p> : null}
      {needsFallbackSkill ? (
        <p className="text-sm text-muted-foreground">
          Select a skill above — some questions don't have their own SKILL from the file.
        </p>
      ) : null}

      <div className="flex justify-end gap-2 border-t border-border pt-4">
        <Button variant="outline" onClick={() => setStep("upload")} disabled={importing}>
          Back
        </Button>
        <Button onClick={handleImport} disabled={!canImport}>
          {importing ? "Importing..." : `Import ${validRows.length} Question${validRows.length === 1 ? "" : "s"}`}
        </Button>
      </div>

      {editingRow ? (
        <EditQuestionDialog
          key={editingRow.index}
          draft={editingRow}
          onCancel={() => setEditingIndex(null)}
          onSave={saveEdit}
          onRemove={() => removeRow(editingRow.index)}
        />
      ) : null}
    </div>
  );
}

interface EditQuestionDialogProps {
  draft: ImportedQuestionDraft;
  onCancel: () => void;
  onSave: (draft: ImportedQuestionDraft) => void;
  onRemove: () => void;
}

function EditQuestionDialog({ draft, onCancel, onSave, onRemove }: EditQuestionDialogProps) {
  const [questionText, setQuestionText] = useState(draft.questionText);
  const [questionType, setQuestionType] = useState<QuestionType>(draft.questionType);
  const [difficulty, setDifficulty] = useState<Difficulty | "">(draft.difficulty ?? "");
  const [explanation, setExplanation] = useState(draft.explanation ?? "");
  const [tag, setTag] = useState(draft.tag ?? "");
  const [options, setOptions] = useState<ImportedOptionDraft[]>(draft.options);

  function toggleCorrect(i: number) {
    setOptions((prev) =>
      prev.map((o, idx) => {
        if (questionType === "MCQ_MULTIPLE") {
          return idx === i ? { ...o, isCorrect: !o.isCorrect } : o;
        }
        return { ...o, isCorrect: idx === i };
      })
    );
  }

  function updateOptionText(i: number, text: string) {
    setOptions((prev) => prev.map((o, idx) => (idx === i ? { ...o, optionText: text } : o)));
  }

  function addOption() {
    setOptions((prev) => [...prev, { optionText: "", isCorrect: false }]);
  }

  function removeOption(i: number) {
    setOptions((prev) => prev.filter((_, idx) => idx !== i));
  }

  function handleSave() {
    onSave({
      ...draft,
      questionText: questionText.trim(),
      questionType,
      difficulty: difficulty || null,
      explanation: explanation.trim() || null,
      tag: tag.trim() || null,
      options,
      // Structural errors are recomputed by the parent's revalidate(); duplicate flags aren't
      // recomputable client-side, so they're carried through here rather than discarded.
      errors: draft.errors.filter((e) => e.toLowerCase().startsWith("duplicate")),
    });
  }

  return (
    <Dialog open onOpenChange={(next) => !next && onCancel()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Question {draft.index}</DialogTitle>
          <DialogDescription>Review and fix the extracted content before importing.</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {draft.externalId || draft.skillName ? (
            <p className="text-xs text-muted-foreground">
              {draft.externalId ? <>ID: {draft.externalId}</> : null}
              {draft.externalId && draft.skillName ? " · " : null}
              {draft.skillName ? <>Skill (from file): {draft.skillName}</> : null}
            </p>
          ) : null}

          {draft.errors.length > 0 ? (
            <div className="rounded-lg border border-destructive/40 bg-destructive/10 p-3 text-xs text-destructive">
              <p className="font-medium">Needs attention:</p>
              <ul className="list-inside list-disc">
                {draft.errors.map((e) => (
                  <li key={e}>{e}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <FormField label="Question" htmlFor="edit-question-text">
            <Textarea id="edit-question-text" value={questionText} onChange={(e) => setQuestionText(e.target.value)} rows={4} />
          </FormField>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField label="Type" htmlFor="edit-question-type">
              <select
                id="edit-question-type"
                value={questionType}
                onChange={(e) => setQuestionType(e.target.value as QuestionType)}
                className={QB_SELECT_CLASS}
              >
                {QUESTION_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Difficulty" htmlFor="edit-question-difficulty">
              <select
                id="edit-question-difficulty"
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as Difficulty)}
                className={QB_SELECT_CLASS}
              >
                <option value="">Select...</option>
                {DIFFICULTY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
          </div>

          <FormField label="Options" htmlFor="edit-question-options" hint="Mark the correct option(s).">
            <div className="space-y-2">
              {options.map((o, i) => (
                <div key={i} className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={() => toggleCorrect(i)}
                    aria-label={o.isCorrect ? "Marked correct" : "Mark as correct"}
                    className={`flex size-5 shrink-0 items-center justify-center rounded-full border text-[10px] ${
                      o.isCorrect ? "border-primary bg-primary text-primary-foreground" : "border-input"
                    }`}
                  >
                    {o.isCorrect ? "✓" : ""}
                  </button>
                  <Input value={o.optionText} onChange={(e) => updateOptionText(i, e.target.value)} className="h-9 flex-1" />
                  <Button variant="ghost" size="icon-sm" aria-label="Remove option" onClick={() => removeOption(i)} disabled={options.length <= 2}>
                    <X />
                  </Button>
                </div>
              ))}
              <Button type="button" variant="outline" size="sm" onClick={addOption}>
                <Plus className="size-3.5" /> Add option
              </Button>
            </div>
          </FormField>

          <FormField label="Explanation" htmlFor="edit-question-explanation">
            <Textarea id="edit-question-explanation" value={explanation} onChange={(e) => setExplanation(e.target.value)} rows={3} />
          </FormField>

          <FormField label="Tag" htmlFor="edit-question-tag" hint="One hierarchical tag, e.g. python-sets-operators.">
            <Input id="edit-question-tag" value={tag} onChange={(e) => setTag(e.target.value)} />
          </FormField>

          {!isPlainTextContent(questionText) ? (
            <div className="rounded-lg border border-border p-3">
              <p className="mb-2 text-xs font-medium text-muted-foreground">Rendered preview</p>
              <QuestionContent text={questionText} textClassName="text-sm text-foreground" />
            </div>
          ) : null}
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={onRemove} className="sm:mr-auto">
            Remove from Import
          </Button>
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button onClick={handleSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
