import { Pencil, Plus, Search, Sparkles, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { SkillIcon } from "@/components/shared/SkillIcon";
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
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { createAdminSkill, deleteAdminSkill, listAdminSkills, updateAdminSkill } from "@/lib/api/endpoints/adminSkills";
import { getSkillCategories } from "@/lib/api/endpoints/skills";
import type { AdminSkillResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";
import { PlacementTabs } from "@/pages/admin/placement/PlacementTabs";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

interface SkillFormState {
  id: string | null;
  name: string;
  category: string;
}

const EMPTY_FORM: SkillFormState = { id: null, name: "", category: "" };

export function PlacementSkillsPage() {
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [category, setCategory] = useState("");
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<SkillFormState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<AdminSkillResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const list = useAsync(
    () => listAdminSkills({ search: debouncedQuery || undefined, category: category || undefined, page, size: PAGE_SIZE }),
    [debouncedQuery, category, page]
  );
  const categories = useAsync(() => getSkillCategories(), []);

  function openCreate() {
    setForm({ ...EMPTY_FORM });
  }

  function openEdit(skill: AdminSkillResponse) {
    setForm({ id: skill.id, name: skill.name, category: skill.category });
  }

  async function handleSave() {
    if (!form) return;
    if (!form.name.trim() || !form.category.trim()) {
      toast.error("Name and category are required.");
      return;
    }
    setSubmitting(true);
    try {
      if (form.id) {
        await updateAdminSkill(form.id, form.name.trim(), form.category.trim());
        toast.success("Skill updated.");
      } else {
        await createAdminSkill(form.name.trim(), form.category.trim());
        toast.success("Skill created.");
      }
      setForm(null);
      list.refetch();
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
      await deleteAdminSkill(pendingDelete.id);
      toast.success(`${pendingDelete.name} deleted.`);
      setPendingDelete(null);
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setDeleting(false);
    }
  }

  function handleFilterChange<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Placement Management</h1>
          <p className="text-sm text-muted-foreground">
            Manage the shared skill catalog used across roles, assessments, and learning resources.
          </p>
        </div>
        <Button size="sm" onClick={openCreate}>
          <Plus className="size-4" /> Add Skill
        </Button>
      </div>

      <PlacementTabs active="skills" />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="relative sm:col-span-2">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search skills..."
            className="h-11 rounded-full pl-10"
          />
        </div>
        <select
          value={category}
          onChange={(e) => handleFilterChange(setCategory)(e.target.value)}
          className={QB_SELECT_CLASS}
        >
          <option value="">All categories</option>
          {(categories.data ?? []).map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
      </div>

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full rounded-xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : list.data && list.data.content.length > 0 ? (
        <>
          <div className="space-y-2">
            {list.data.content.map((skill) => (
              <div
                key={skill.id}
                className="flex items-center justify-between gap-3 rounded-xl border border-border bg-card p-4"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <SkillIcon iconSlug={skill.iconSlug} iconType={skill.iconType} className="size-6 shrink-0" />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{skill.name}</p>
                    <p className="text-xs text-muted-foreground">{skill.category}</p>
                  </div>
                </div>
                <div className="flex shrink-0 gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={() => openEdit(skill)} title="Edit">
                    <Pencil className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => setPendingDelete(skill)} title="Delete">
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={Sparkles}
          title="No skills found"
          description="Try adjusting your search or filters, or add a new skill."
          action={
            <Button onClick={openCreate}>
              <Plus className="size-4" /> Add Skill
            </Button>
          }
        />
      )}

      <Dialog open={form !== null} onOpenChange={(open) => !open && setForm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{form?.id ? "Edit Skill" : "Add Skill"}</DialogTitle>
          </DialogHeader>
          {form ? (
            <div className="space-y-4">
              <FormField label="Name" htmlFor="skill-name">
                <Input
                  id="skill-name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  disabled={submitting}
                  placeholder="e.g. SQL"
                />
              </FormField>
              <FormField label="Category" htmlFor="skill-category" hint="Groups related skills, e.g. Database, Programming">
                <Input
                  id="skill-category"
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value })}
                  disabled={submitting}
                  list="skill-category-options"
                  placeholder="e.g. Database"
                />
                <datalist id="skill-category-options">
                  {(categories.data ?? []).map((c) => (
                    <option key={c} value={c} />
                  ))}
                </datalist>
              </FormField>
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setForm(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={submitting}>
              {submitting ? "Saving..." : form?.id ? "Save Changes" : "Create Skill"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete skill?"
        description={`This permanently deletes "${pendingDelete?.name}" from the shared catalog. If it's in use anywhere (questions, resources, role mappings, portfolios), delete will be blocked.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
