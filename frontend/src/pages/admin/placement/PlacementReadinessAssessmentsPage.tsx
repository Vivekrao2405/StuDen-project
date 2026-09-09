import { ClipboardList, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { createAssessment, deleteAssessment, listAdminAssessments, listAdminRoles } from "@/lib/api/endpoints/adminPlacement";
import type { PlacementAssessmentResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { DIFFICULTY_OPTIONS } from "@/pages/admin/questionBankOptions";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { PlacementTabs } from "@/pages/admin/placement/PlacementTabs";

interface CreateFormState {
  title: string;
  description: string;
  roleId: string;
  difficulty: string;
  durationMinutes: string;
  passingScore: string;
}

const EMPTY_FORM: CreateFormState = {
  title: "",
  description: "",
  roleId: "",
  difficulty: "MEDIUM",
  durationMinutes: "",
  passingScore: "",
};

const STATUS_VARIANT: Record<string, "default" | "secondary" | "outline"> = {
  PUBLISHED: "default",
  DRAFT: "secondary",
  ARCHIVED: "outline",
};

/** Admin list of role-specific Placement Readiness assessments — the missing piece that let a
 * student's Placement Readiness page reach "Start Assessment" instead of "Not available yet".
 * Metadata + question management live on the detail page; this page only creates/lists/deletes. */
export function PlacementReadinessAssessmentsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<CreateFormState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<PlacementAssessmentResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const list = useAsync(() => listAdminAssessments({ size: 100 }), []);
  const roles = useAsync(() => listAdminRoles(), []);

  const filtered = useMemo(() => {
    const assessments = list.data?.content ?? [];
    const term = query.trim().toLowerCase();
    if (!term) return assessments;
    return assessments.filter(
      (a) => a.title.toLowerCase().includes(term) || a.roleName.toLowerCase().includes(term)
    );
  }, [list.data, query]);

  function openCreate() {
    setForm({ ...EMPTY_FORM, roleId: roles.data?.[0]?.id ?? "" });
  }

  async function handleCreate() {
    if (!form) return;
    if (!form.title.trim()) {
      toast.error("Title is required.");
      return;
    }
    if (!form.roleId) {
      toast.error("Select a target role.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createAssessment({
        title: form.title.trim(),
        description: form.description.trim() || null,
        roleId: form.roleId,
        difficulty: form.difficulty as never,
        durationMinutes: form.durationMinutes.trim() ? Number(form.durationMinutes) : null,
        passingScore: form.passingScore.trim() ? Number(form.passingScore) : null,
      });
      toast.success("Readiness assessment created.");
      setForm(null);
      navigate(ROUTES.adminPlacementAssessmentDetail(created.id));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteAssessment(pendingDelete.id);
      toast.success(`"${pendingDelete.title}" deleted.`);
      setPendingDelete(null);
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Placement Management</h1>
          <p className="text-sm text-muted-foreground">
            Configure the role-specific readiness assessments students take under Placement Readiness.
          </p>
        </div>
        <Button size="sm" onClick={openCreate}>
          <Plus className="size-4" /> New Assessment
        </Button>
      </div>

      <PlacementTabs active="assessments" />

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search assessments..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : filtered.length > 0 ? (
        <div className="overflow-x-auto rounded-xl border border-border">
          <table className="w-full min-w-[720px] text-sm">
            <thead className="bg-muted/50 text-left text-xs font-medium text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Target Role</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Questions</th>
                <th className="px-4 py-3">Updated</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {filtered.map((a) => (
                <tr key={a.id} className="hover:bg-muted/30">
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      className="font-medium text-foreground hover:underline"
                      onClick={() => navigate(ROUTES.adminPlacementAssessmentDetail(a.id))}
                    >
                      {a.title}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{a.roleName}</td>
                  <td className="px-4 py-3">
                    <Badge variant={STATUS_VARIANT[a.status] ?? "outline"}>{a.status}</Badge>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{a.questionCount}</td>
                  <td className="px-4 py-3 text-muted-foreground">{new Date(a.updatedAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => navigate(ROUTES.adminPlacementAssessmentDetail(a.id))}
                        title="Edit"
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => setPendingDelete(a)} title="Delete">
                        <Trash2 className="size-4 text-destructive" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <EmptyState
          icon={ClipboardList}
          title="No readiness assessments found"
          description="Try a different search, or create the first role-specific readiness assessment."
          action={
            <Button onClick={openCreate}>
              <Plus className="size-4" /> New Assessment
            </Button>
          }
        />
      )}

      <Dialog open={form !== null} onOpenChange={(open) => !open && setForm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New Readiness Assessment</DialogTitle>
          </DialogHeader>
          {form ? (
            <div className="space-y-4">
              <FormField label="Title" htmlFor="assessment-title">
                <Input
                  id="assessment-title"
                  value={form.title}
                  onChange={(e) => setForm({ ...form, title: e.target.value })}
                  disabled={submitting}
                  placeholder="e.g. Data Analyst Placement Readiness"
                />
              </FormField>
              <FormField label="Target Role" htmlFor="assessment-role">
                <select
                  id="assessment-role"
                  className={QB_SELECT_CLASS}
                  value={form.roleId}
                  onChange={(e) => setForm({ ...form, roleId: e.target.value })}
                  disabled={submitting || roles.loading}
                >
                  <option value="" disabled>
                    Select a role...
                  </option>
                  {(roles.data ?? []).map((role) => (
                    <option key={role.id} value={role.id}>
                      {role.name}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Description" htmlFor="assessment-description" hint="Optional">
                <Textarea
                  id="assessment-description"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  disabled={submitting}
                  rows={3}
                />
              </FormField>
              <div className="grid grid-cols-3 gap-3">
                <FormField label="Difficulty" htmlFor="assessment-difficulty">
                  <select
                    id="assessment-difficulty"
                    className={QB_SELECT_CLASS}
                    value={form.difficulty}
                    onChange={(e) => setForm({ ...form, difficulty: e.target.value })}
                    disabled={submitting}
                  >
                    {DIFFICULTY_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="Duration (min)" htmlFor="assessment-duration" hint="Optional">
                  <Input
                    id="assessment-duration"
                    type="number"
                    value={form.durationMinutes}
                    onChange={(e) => setForm({ ...form, durationMinutes: e.target.value })}
                    disabled={submitting}
                  />
                </FormField>
                <FormField label="Passing %" htmlFor="assessment-passing" hint="Optional">
                  <Input
                    id="assessment-passing"
                    type="number"
                    value={form.passingScore}
                    onChange={(e) => setForm({ ...form, passingScore: e.target.value })}
                    disabled={submitting}
                  />
                </FormField>
              </div>
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setForm(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleCreate} disabled={submitting}>
              {submitting ? "Creating..." : "Create & Add Questions"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete readiness assessment?"
        description={`This permanently deletes "${pendingDelete?.title}". If students have already attempted it, delete will be blocked — archive it instead.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
