import { Layers, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { createSeries, deleteSeries, listAdminCompanies, listAdminRoles, listAdminSeries } from "@/lib/api/endpoints/adminPlacement";
import type { PlacementSeriesResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { DIFFICULTY_OPTIONS } from "@/pages/admin/questionBankOptions";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { PlacementTabs } from "@/pages/admin/placement/PlacementTabs";
import { PREPARATION_TYPE_OPTIONS } from "@/pages/placement/placementPrepDisplay";

interface CreateFormState {
  name: string;
  description: string;
  roleId: string;
  companyId: string;
  difficulty: string;
  preparationType: string;
  estimatedDurationHours: string;
}

const EMPTY_FORM: CreateFormState = {
  name: "",
  description: "",
  roleId: "",
  companyId: "",
  difficulty: "MEDIUM",
  preparationType: "",
  estimatedDurationHours: "",
};

const STATUS_VARIANT: Record<string, "default" | "secondary" | "outline"> = {
  PUBLISHED: "default",
  DRAFT: "secondary",
  ARCHIVED: "outline",
};

/** Admin authoring entry point for Placement Prep (Phase 5): create/list/delete series here;
 * module + question management lives on the detail page, same split as Readiness Assessments. */
export function PlacementSeriesPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<CreateFormState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<PlacementSeriesResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const list = useAsync(() => listAdminSeries({ size: 100 }), []);
  const roles = useAsync(() => listAdminRoles(), []);
  const companies = useAsync(() => listAdminCompanies({ size: 100 }), []);

  const filtered = useMemo(() => {
    const series = list.data?.content ?? [];
    const term = query.trim().toLowerCase();
    if (!term) return series;
    return series.filter(
      (s) => s.name.toLowerCase().includes(term) || s.targetRoleName.toLowerCase().includes(term)
        || (s.companyName ?? "").toLowerCase().includes(term)
    );
  }, [list.data, query]);

  function openCreate() {
    setForm({ ...EMPTY_FORM, roleId: roles.data?.[0]?.id ?? "" });
  }

  async function handleCreate() {
    if (!form) return;
    if (!form.name.trim()) {
      toast.error("Series name is required.");
      return;
    }
    if (!form.roleId) {
      toast.error("Select a target role.");
      return;
    }
    setSubmitting(true);
    try {
      const created = await createSeries({
        name: form.name.trim(),
        description: form.description.trim() || null,
        companyId: form.companyId || null,
        targetRoleId: form.roleId,
        companyType: null,
        difficulty: form.difficulty as never,
        estimatedDurationHours: form.estimatedDurationHours.trim() ? Number(form.estimatedDurationHours) : null,
        thumbnailUrl: null,
        skillIds: null,
        preparationType: (form.preparationType || null) as never,
      });
      toast.success("Series created as a draft.");
      setForm(null);
      navigate(ROUTES.adminPlacementSeriesDetail(created.id));
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
      await deleteSeries(pendingDelete.id);
      toast.success(`"${pendingDelete.name}" deleted.`);
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
            Build role/company-specific preparation series students work through under Placement Prep.
          </p>
        </div>
        <Button size="sm" onClick={openCreate}>
          <Plus className="size-4" /> New Series
        </Button>
      </div>

      <PlacementTabs active="series" />

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search series..."
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
          <table className="w-full min-w-[840px] text-sm">
            <thead className="bg-muted/50 text-left text-xs font-medium text-muted-foreground">
              <tr>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Company</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Modules</th>
                <th className="px-4 py-3">Updated</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {filtered.map((s) => (
                <tr key={s.id} className="hover:bg-muted/30">
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      className="font-medium text-foreground hover:underline"
                      onClick={() => navigate(ROUTES.adminPlacementSeriesDetail(s.id))}
                    >
                      {s.name}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{s.targetRoleName}</td>
                  <td className="px-4 py-3 text-muted-foreground">{s.companyName ?? "—"}</td>
                  <td className="px-4 py-3">
                    <Badge variant={STATUS_VARIANT[s.status] ?? "outline"}>{s.status}</Badge>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">{s.moduleCount}</td>
                  <td className="px-4 py-3 text-muted-foreground">{new Date(s.updatedAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => navigate(ROUTES.adminPlacementSeriesDetail(s.id))}
                        title="Edit"
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => setPendingDelete(s)} title="Delete">
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
          icon={Layers}
          title="No placement series found"
          description="Try a different search, or create the first preparation series."
          action={
            <Button onClick={openCreate}>
              <Plus className="size-4" /> New Series
            </Button>
          }
        />
      )}

      <Dialog open={form !== null} onOpenChange={(open) => !open && setForm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New Placement Series</DialogTitle>
          </DialogHeader>
          {form ? (
            <div className="space-y-4">
              <FormField label="Name" htmlFor="series-name">
                <Input
                  id="series-name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  disabled={submitting}
                  placeholder="e.g. Virtusa Data Analyst Preparation"
                />
              </FormField>
              <div className="grid grid-cols-2 gap-3">
                <FormField label="Target Role" htmlFor="series-role">
                  <select
                    id="series-role"
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
                <FormField label="Company" htmlFor="series-company" hint="Optional">
                  <select
                    id="series-company"
                    className={QB_SELECT_CLASS}
                    value={form.companyId}
                    onChange={(e) => setForm({ ...form, companyId: e.target.value })}
                    disabled={submitting || companies.loading}
                  >
                    <option value="">Not company-specific</option>
                    {(companies.data?.content ?? []).map((company) => (
                      <option key={company.id} value={company.id}>
                        {company.name}
                      </option>
                    ))}
                  </select>
                </FormField>
              </div>
              <FormField label="Description" htmlFor="series-description" hint="Optional">
                <Textarea
                  id="series-description"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  disabled={submitting}
                  rows={2}
                />
              </FormField>
              <div className="grid grid-cols-3 gap-3">
                <FormField label="Difficulty" htmlFor="series-difficulty">
                  <select
                    id="series-difficulty"
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
                <FormField label="Preparation Type" htmlFor="series-prep-type" hint="Optional">
                  <select
                    id="series-prep-type"
                    className={QB_SELECT_CLASS}
                    value={form.preparationType}
                    onChange={(e) => setForm({ ...form, preparationType: e.target.value })}
                    disabled={submitting}
                  >
                    <option value="">Not narrowed</option>
                    {PREPARATION_TYPE_OPTIONS.map(([value, label]) => (
                      <option key={value} value={value}>
                        {label}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="Duration (hrs)" htmlFor="series-duration" hint="Optional">
                  <Input
                    id="series-duration"
                    type="number"
                    value={form.estimatedDurationHours}
                    onChange={(e) => setForm({ ...form, estimatedDurationHours: e.target.value })}
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
              {submitting ? "Creating..." : "Create & Add Modules"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete series?"
        description={`This permanently deletes "${pendingDelete?.name}". If students have already started it, delete will be blocked — archive it instead.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
