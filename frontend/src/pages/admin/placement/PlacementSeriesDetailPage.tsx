import {
  ArrowLeft,
  ArrowDown,
  ArrowUp,
  BookOpen,
  Code2,
  FileQuestion,
  Pencil,
  Plus,
  Trash2,
} from "lucide-react";
import { useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  addModuleItem,
  archiveSeries,
  createModule,
  deleteModule,
  getAdminSeries,
  listAdminCompanies,
  listAdminRoles,
  publishSeries,
  removeModuleItem,
  reorderModuleItems,
  reorderModules,
  unpublishSeries,
  updateModule,
  updateSeries,
} from "@/lib/api/endpoints/adminPlacement";
import { listAdminPracticalAssessments } from "@/lib/api/endpoints/adminPracticalAssessments";
import { listAdminResources } from "@/lib/api/endpoints/adminResources";
import { listQuestions } from "@/lib/api/endpoints/questionBank";
import type { ModuleItemType, PlacementModuleResponse } from "@/lib/api/placementTypes";
import type { PracticalAssessmentSummary } from "@/lib/api/practicalTypes";
import type { ResourceSummary } from "@/lib/api/resourceTypes";
import type { QuestionSummaryResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { DIFFICULTY_OPTIONS } from "@/pages/admin/questionBankOptions";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { SkillPicker } from "@/pages/portfolio/SkillPicker";
import { MODULE_ITEM_TYPE_LABEL, PREPARATION_TYPE_OPTIONS } from "@/pages/placement/placementPrepDisplay";

type PickerTab = "question" | "practical" | "resource";

const STATUS_VARIANT: Record<string, "default" | "secondary" | "outline"> = {
  PUBLISHED: "default",
  DRAFT: "secondary",
  ARCHIVED: "outline",
};

const ITEM_ICON: Record<ModuleItemType, typeof FileQuestion> = {
  QUESTION: FileQuestion,
  PRACTICAL_ASSESSMENT: Code2,
  RESOURCE: BookOpen,
};

export function PlacementSeriesDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const toast = useToast();

  const detail = useAsync(() => getAdminSeries(id), [id]);
  const roles = useAsync(() => listAdminRoles(), []);
  const companies = useAsync(() => listAdminCompanies({ size: 100 }), []);

  const [saving, setSaving] = useState(false);
  const [moduleForm, setModuleForm] = useState<{ id: string | null; name: string; description: string } | null>(null);
  const [moduleSaving, setModuleSaving] = useState(false);
  const [pendingDeleteModule, setPendingDeleteModule] = useState<PlacementModuleResponse | null>(null);
  const [deletingModule, setDeletingModule] = useState(false);
  const [pendingRemoveItem, setPendingRemoveItem] = useState<{ id: string; label: string } | null>(null);
  const [removingItem, setRemovingItem] = useState(false);

  const [pickerModuleId, setPickerModuleId] = useState<string | null>(null);
  const [pickerTab, setPickerTab] = useState<PickerTab>("question");
  const [pickerSearch, setPickerSearch] = useState("");

  const series = detail.data;
  const pickerModule = series?.modules.find((m) => m.id === pickerModuleId) ?? null;

  const questionResults = useAsync(
    () => (pickerModuleId && pickerTab === "question"
      ? listQuestions({ status: "PUBLISHED", search: pickerSearch || undefined, size: 20 })
      : Promise.resolve(null)),
    [pickerModuleId, pickerTab, pickerSearch]
  );
  const practicalResults = useAsync(
    () => (pickerModuleId && pickerTab === "practical"
      ? listAdminPracticalAssessments({ status: "PUBLISHED", search: pickerSearch || undefined, size: 20 })
      : Promise.resolve(null)),
    [pickerModuleId, pickerTab, pickerSearch]
  );
  const resourceResults = useAsync(
    () => (pickerModuleId && pickerTab === "resource"
      ? listAdminResources({ status: "PUBLISHED", search: pickerSearch || undefined, size: 20 })
      : Promise.resolve(null)),
    [pickerModuleId, pickerTab, pickerSearch]
  );

  const linkedTargetIds = useMemo(() => {
    const ids = new Set<string>();
    pickerModule?.items.forEach((item) => ids.add(item.targetId));
    return ids;
  }, [pickerModule]);

  async function saveField(patch: Partial<{
    name: string; description: string | null; companyId: string | null; targetRoleId: string;
    companyType: string | null; difficulty: string; estimatedDurationHours: number | null;
    preparationType: string | null; skillIds: string[];
  }>) {
    if (!series) return;
    setSaving(true);
    try {
      await updateSeries(series.id, {
        name: patch.name ?? series.name,
        description: patch.description !== undefined ? patch.description : series.description,
        companyId: patch.companyId !== undefined ? patch.companyId : series.companyId,
        targetRoleId: patch.targetRoleId ?? series.targetRoleId,
        companyType: (patch.companyType !== undefined ? patch.companyType : series.companyType) as never,
        difficulty: (patch.difficulty ?? series.difficulty) as never,
        estimatedDurationHours:
          patch.estimatedDurationHours !== undefined ? patch.estimatedDurationHours : series.estimatedDurationHours,
        thumbnailUrl: series.thumbnailUrl,
        skillIds: patch.skillIds ?? series.skillsCovered.map((s) => s.id),
        preparationType: (patch.preparationType !== undefined ? patch.preparationType : series.preparationType) as never,
      });
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't save. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  async function handleStatusAction(action: "publish" | "unpublish" | "archive") {
    if (!series) return;
    try {
      const fn = action === "publish" ? publishSeries : action === "unpublish" ? unpublishSeries : archiveSeries;
      await fn(series.id);
      toast.success(`Series ${action === "publish" ? "published" : action === "unpublish" ? "unpublished" : "archived"}.`);
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    }
  }

  async function handleSaveModule() {
    if (!series || !moduleForm) return;
    if (!moduleForm.name.trim()) {
      toast.error("Module name is required.");
      return;
    }
    setModuleSaving(true);
    try {
      if (moduleForm.id) {
        await updateModule(moduleForm.id, {
          name: moduleForm.name.trim(),
          description: moduleForm.description.trim() || null,
          displayOrder: null,
          requiredItemCount: null,
        });
      } else {
        await createModule(series.id, {
          name: moduleForm.name.trim(),
          description: moduleForm.description.trim() || null,
          displayOrder: null,
          requiredItemCount: null,
        });
      }
      toast.success(moduleForm.id ? "Module updated." : "Module added.");
      setModuleForm(null);
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't save this module.");
    } finally {
      setModuleSaving(false);
    }
  }

  async function handleDeleteModule() {
    if (!pendingDeleteModule) return;
    setDeletingModule(true);
    try {
      await deleteModule(pendingDeleteModule.id);
      toast.success("Module removed.");
      setPendingDeleteModule(null);
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't remove this module.");
    } finally {
      setDeletingModule(false);
    }
  }

  async function moveModule(module: PlacementModuleResponse, direction: -1 | 1) {
    if (!series) return;
    const modules = [...series.modules].sort((a, b) => a.displayOrder - b.displayOrder);
    const index = modules.findIndex((m) => m.id === module.id);
    const swapWith = index + direction;
    if (swapWith < 0 || swapWith >= modules.length) return;
    [modules[index], modules[swapWith]] = [modules[swapWith], modules[index]];
    try {
      await reorderModules(series.id, { orderedIds: modules.map((m) => m.id) });
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't reorder modules.");
    }
  }

  async function moveItem(module: PlacementModuleResponse, itemId: string, direction: -1 | 1) {
    const items = [...module.items].sort((a, b) => a.displayOrder - b.displayOrder);
    const index = items.findIndex((i) => i.id === itemId);
    const swapWith = index + direction;
    if (swapWith < 0 || swapWith >= items.length) return;
    [items[index], items[swapWith]] = [items[swapWith], items[index]];
    try {
      await reorderModuleItems(module.id, { orderedIds: items.map((i) => i.id) });
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't reorder items.");
    }
  }

  async function handleAddQuestion(question: QuestionSummaryResponse) {
    if (!pickerModuleId) return;
    try {
      await addModuleItem(pickerModuleId, {
        itemType: "QUESTION", questionId: question.id, practicalAssessmentId: null, resourceId: null,
        displayOrder: null, required: true,
      });
      toast.success("Question added.");
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't add this question.");
    }
  }

  async function handleAddPractical(practical: PracticalAssessmentSummary) {
    if (!pickerModuleId) return;
    try {
      await addModuleItem(pickerModuleId, {
        itemType: "PRACTICAL_ASSESSMENT", questionId: null, practicalAssessmentId: practical.id, resourceId: null,
        displayOrder: null, required: true,
      });
      toast.success("Practical assessment added.");
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't add this practical assessment.");
    }
  }

  async function handleAddResource(resource: ResourceSummary) {
    if (!pickerModuleId) return;
    try {
      await addModuleItem(pickerModuleId, {
        itemType: "RESOURCE", questionId: null, practicalAssessmentId: null, resourceId: resource.id,
        displayOrder: null, required: true,
      });
      toast.success("Resource added.");
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't add this resource.");
    }
  }

  async function handleRemoveItem() {
    if (!pendingRemoveItem) return;
    setRemovingItem(true);
    try {
      await removeModuleItem(pendingRemoveItem.id);
      toast.success("Removed.");
      setPendingRemoveItem(null);
      detail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't remove this item.");
    } finally {
      setRemovingItem(false);
    }
  }

  if (detail.loading) {
    return <LoadingState label="Loading series..." />;
  }
  if (detail.error || !series) {
    return <ErrorState message={detail.error?.message ?? "Series not found."} onRetry={detail.refetch} />;
  }

  const sortedModules = [...series.modules].sort((a, b) => a.displayOrder - b.displayOrder);

  return (
    <div className="space-y-6">
      <Link
        to={ROUTES.adminPlacementSeries}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Placement Series
      </Link>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold text-foreground">{series.name}</h1>
          <Badge variant={STATUS_VARIANT[series.status] ?? "outline"}>{series.status}</Badge>
        </div>
        <div className="flex gap-2">
          {series.status === "DRAFT" ? (
            <Button size="sm" onClick={() => handleStatusAction("publish")}>
              Publish
            </Button>
          ) : null}
          {series.status === "PUBLISHED" ? (
            <Button size="sm" variant="outline" onClick={() => handleStatusAction("unpublish")}>
              Unpublish
            </Button>
          ) : null}
          {series.status !== "ARCHIVED" ? (
            <Button size="sm" variant="outline" onClick={() => handleStatusAction("archive")}>
              Archive
            </Button>
          ) : null}
        </div>
      </div>

      <Card>
        <CardContent className="space-y-4 pt-5">
          <FormField label="Name" htmlFor="series-edit-name">
            <Input
              id="series-edit-name"
              defaultValue={series.name}
              disabled={saving}
              onBlur={(e) => e.target.value !== series.name && saveField({ name: e.target.value })}
            />
          </FormField>
          <FormField label="Description" htmlFor="series-edit-description" hint="Optional">
            <Textarea
              id="series-edit-description"
              defaultValue={series.description ?? ""}
              disabled={saving}
              rows={2}
              onBlur={(e) => e.target.value !== (series.description ?? "") && saveField({ description: e.target.value || null })}
            />
          </FormField>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <FormField label="Target Role" htmlFor="series-edit-role">
              <select
                id="series-edit-role"
                className={QB_SELECT_CLASS}
                defaultValue={series.targetRoleId}
                disabled={saving}
                onChange={(e) => saveField({ targetRoleId: e.target.value })}
              >
                {(roles.data ?? []).map((role) => (
                  <option key={role.id} value={role.id}>
                    {role.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Company" htmlFor="series-edit-company" hint="Optional">
              <select
                id="series-edit-company"
                className={QB_SELECT_CLASS}
                defaultValue={series.companyId ?? ""}
                disabled={saving}
                onChange={(e) => saveField({ companyId: e.target.value || null })}
              >
                <option value="">Not company-specific</option>
                {(companies.data?.content ?? []).map((company) => (
                  <option key={company.id} value={company.id}>
                    {company.name}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Difficulty" htmlFor="series-edit-difficulty">
              <select
                id="series-edit-difficulty"
                className={QB_SELECT_CLASS}
                defaultValue={series.difficulty}
                disabled={saving}
                onChange={(e) => saveField({ difficulty: e.target.value })}
              >
                {DIFFICULTY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Preparation Type" htmlFor="series-edit-prep-type" hint="Optional">
              <select
                id="series-edit-prep-type"
                className={QB_SELECT_CLASS}
                defaultValue={series.preparationType ?? ""}
                disabled={saving}
                onChange={(e) => saveField({ preparationType: e.target.value || null })}
              >
                <option value="">Not narrowed</option>
                {PREPARATION_TYPE_OPTIONS.map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </FormField>
          </div>
          <FormField label="Duration (hours)" htmlFor="series-edit-duration" hint="Optional">
            <Input
              id="series-edit-duration"
              type="number"
              className="max-w-xs"
              defaultValue={series.estimatedDurationHours ?? ""}
              disabled={saving}
              onBlur={(e) => saveField({ estimatedDurationHours: e.target.value.trim() ? Number(e.target.value) : null })}
            />
          </FormField>
          <div>
            <p className="mb-2 text-sm font-medium text-foreground">Skills Covered</p>
            <SkillPicker
              value={series.skillsCovered}
              onChange={(skills) => saveField({ skillIds: skills.map((s) => s.id) })}
              disabled={saving}
            />
          </div>
        </CardContent>
      </Card>

      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-foreground">Modules ({sortedModules.length})</h2>
        <Button size="sm" onClick={() => setModuleForm({ id: null, name: "", description: "" })}>
          <Plus className="size-4" /> Add Module
        </Button>
      </div>

      {sortedModules.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border bg-muted/30 px-4 py-6 text-center text-sm text-muted-foreground">
          No modules yet — add at least one before publishing.
        </p>
      ) : (
        <div className="space-y-4">
          {sortedModules.map((module, moduleIndex) => (
            <Card key={module.id}>
              <CardContent className="space-y-3 pt-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="font-semibold text-foreground">{module.name}</p>
                    {module.description ? (
                      <p className="text-sm text-muted-foreground">{module.description}</p>
                    ) : null}
                  </div>
                  <div className="flex shrink-0 gap-1">
                    <Button
                      variant="ghost" size="icon-sm" title="Move up" disabled={moduleIndex === 0}
                      onClick={() => moveModule(module, -1)}
                    >
                      <ArrowUp className="size-4" />
                    </Button>
                    <Button
                      variant="ghost" size="icon-sm" title="Move down" disabled={moduleIndex === sortedModules.length - 1}
                      onClick={() => moveModule(module, 1)}
                    >
                      <ArrowDown className="size-4" />
                    </Button>
                    <Button
                      variant="ghost" size="icon-sm" title="Rename"
                      onClick={() => setModuleForm({ id: module.id, name: module.name, description: module.description ?? "" })}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon-sm" title="Delete" onClick={() => setPendingDeleteModule(module)}>
                      <Trash2 className="size-4 text-destructive" />
                    </Button>
                  </div>
                </div>

                <div className="space-y-1.5">
                  {[...module.items].sort((a, b) => a.displayOrder - b.displayOrder).map((item, itemIndex, items) => {
                    const Icon = ITEM_ICON[item.itemType];
                    return (
                      <div key={item.id} className="flex items-center gap-3 rounded-lg border border-border bg-card p-2.5">
                        <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-accent">
                          <Icon className="size-3.5" />
                        </span>
                        <div className="min-w-0 flex-1">
                          <p className="truncate text-sm text-foreground">{item.title}</p>
                          <p className="text-xs text-muted-foreground">
                            {MODULE_ITEM_TYPE_LABEL[item.itemType]}
                            {!item.required ? " · Optional" : ""}
                          </p>
                        </div>
                        <div className="flex shrink-0 gap-0.5">
                          <Button
                            variant="ghost" size="icon-sm" title="Move up" disabled={itemIndex === 0}
                            onClick={() => moveItem(module, item.id, -1)}
                          >
                            <ArrowUp className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost" size="icon-sm" title="Move down" disabled={itemIndex === items.length - 1}
                            onClick={() => moveItem(module, item.id, 1)}
                          >
                            <ArrowDown className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost" size="icon-sm" title="Remove"
                            onClick={() => setPendingRemoveItem({ id: item.id, label: item.title })}
                          >
                            <Trash2 className="size-3.5 text-destructive" />
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                  {module.items.length === 0 ? (
                    <p className="rounded-lg border border-dashed border-border px-3 py-4 text-center text-xs text-muted-foreground">
                      No content yet.
                    </p>
                  ) : null}
                </div>

                <Button
                  variant="outline" size="sm"
                  onClick={() => { setPickerModuleId(module.id); setPickerTab("question"); setPickerSearch(""); }}
                >
                  <Plus className="size-4" /> Add Content
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={moduleForm !== null} onOpenChange={(open) => !open && setModuleForm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{moduleForm?.id ? "Rename Module" : "Add Module"}</DialogTitle>
          </DialogHeader>
          {moduleForm ? (
            <div className="space-y-4">
              <FormField label="Name" htmlFor="module-name">
                <Input
                  id="module-name"
                  value={moduleForm.name}
                  onChange={(e) => setModuleForm({ ...moduleForm, name: e.target.value })}
                  disabled={moduleSaving}
                  placeholder="e.g. SQL"
                />
              </FormField>
              <FormField label="Description" htmlFor="module-description" hint="Optional">
                <Textarea
                  id="module-description"
                  value={moduleForm.description}
                  onChange={(e) => setModuleForm({ ...moduleForm, description: e.target.value })}
                  disabled={moduleSaving}
                  rows={2}
                />
              </FormField>
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setModuleForm(null)} disabled={moduleSaving}>
              Cancel
            </Button>
            <Button onClick={handleSaveModule} disabled={moduleSaving}>
              {moduleSaving ? "Saving..." : moduleForm?.id ? "Save Changes" : "Add Module"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={pickerModuleId !== null} onOpenChange={(open) => { if (!open) { setPickerModuleId(null); setPickerSearch(""); } }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Add Content to {pickerModule?.name}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <SegmentedControl
              value={pickerTab}
              onChange={setPickerTab}
              options={[
                { value: "question", label: "Question Bank" },
                { value: "practical", label: "Practical / Coding" },
                { value: "resource", label: "Resource" },
              ]}
            />
            <Input
              value={pickerSearch}
              onChange={(e) => setPickerSearch(e.target.value)}
              placeholder={`Search published ${pickerTab === "question" ? "questions" : pickerTab === "practical" ? "practical assessments" : "resources"}...`}
            />
            <div className="max-h-80 space-y-1.5 overflow-y-auto">
              {pickerTab === "question"
                ? (questionResults.data?.content ?? []).map((q) => (
                    <button
                      key={q.id} type="button" disabled={linkedTargetIds.has(q.id)} onClick={() => handleAddQuestion(q)}
                      className="flex w-full items-start justify-between gap-2 rounded-lg border border-border p-2.5 text-left text-sm hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <span className="min-w-0 flex-1 truncate">{q.questionTextPreview}</span>
                      <Badge variant="outline">{q.skillName}</Badge>
                    </button>
                  ))
                : pickerTab === "practical"
                ? (practicalResults.data?.content ?? []).map((p) => (
                    <button
                      key={p.id} type="button" disabled={linkedTargetIds.has(p.id)} onClick={() => handleAddPractical(p)}
                      className="flex w-full items-start justify-between gap-2 rounded-lg border border-border p-2.5 text-left text-sm hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <span className="min-w-0 flex-1 truncate">{p.title}</span>
                      <Badge variant="outline">{p.skillName}</Badge>
                    </button>
                  ))
                : (resourceResults.data?.content ?? []).map((r) => (
                    <button
                      key={r.id} type="button" disabled={linkedTargetIds.has(r.id)} onClick={() => handleAddResource(r)}
                      className="flex w-full items-start justify-between gap-2 rounded-lg border border-border p-2.5 text-left text-sm hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      <span className="min-w-0 flex-1 truncate">{r.title}</span>
                      <Badge variant="outline">{r.skillName}</Badge>
                    </button>
                  ))}
              {pickerTab === "question" && (questionResults.data?.content ?? []).length === 0 && !questionResults.loading ? (
                <p className="py-4 text-center text-sm text-muted-foreground">No published questions found.</p>
              ) : null}
              {pickerTab === "practical" && (practicalResults.data?.content ?? []).length === 0 && !practicalResults.loading ? (
                <p className="py-4 text-center text-sm text-muted-foreground">No published practical assessments found.</p>
              ) : null}
              {pickerTab === "resource" && (resourceResults.data?.content ?? []).length === 0 && !resourceResults.loading ? (
                <p className="py-4 text-center text-sm text-muted-foreground">No published resources found.</p>
              ) : null}
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingDeleteModule !== null}
        onOpenChange={(open) => !open && setPendingDeleteModule(null)}
        title="Delete module?"
        description={`This permanently deletes "${pendingDeleteModule?.name}" and its content links. If students have already started items in it, delete will be blocked.`}
        confirmLabel="Delete"
        loading={deletingModule}
        onConfirm={handleDeleteModule}
      />

      <ConfirmDialog
        open={pendingRemoveItem !== null}
        onOpenChange={(open) => !open && setPendingRemoveItem(null)}
        title="Remove this item?"
        description={`"${pendingRemoveItem?.label}" will be removed from this module. The underlying content is not deleted.`}
        confirmLabel="Remove"
        loading={removingItem}
        onConfirm={handleRemoveItem}
      />
    </div>
  );
}
