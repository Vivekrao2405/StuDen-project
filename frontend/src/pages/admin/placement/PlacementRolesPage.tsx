import { Briefcase, Pencil, Plus, Power, PowerOff, Search, Trash2 } from "lucide-react";
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
import {
  activateRole,
  createRole,
  deactivateRole,
  deleteRole,
  listAdminRoles,
  updateRole,
} from "@/lib/api/endpoints/adminPlacement";
import type { PlacementRoleResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { PlacementTabs } from "@/pages/admin/placement/PlacementTabs";

interface RoleFormState {
  id: string | null;
  name: string;
  description: string;
  displayOrder: string;
}

const EMPTY_FORM: RoleFormState = { id: null, name: "", description: "", displayOrder: "" };

export function PlacementRolesPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [form, setForm] = useState<RoleFormState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<PlacementRoleResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const list = useAsync(() => listAdminRoles(), []);

  const filtered = useMemo(() => {
    const roles = list.data ?? [];
    const term = query.trim().toLowerCase();
    if (!term) return roles;
    return roles.filter(
      (r) => r.name.toLowerCase().includes(term) || (r.description ?? "").toLowerCase().includes(term)
    );
  }, [list.data, query]);

  function openCreate() {
    setForm({ ...EMPTY_FORM });
  }

  function openEdit(role: PlacementRoleResponse) {
    setForm({
      id: role.id,
      name: role.name,
      description: role.description ?? "",
      displayOrder: String(role.displayOrder),
    });
  }

  async function handleSave() {
    if (!form) return;
    if (!form.name.trim()) {
      toast.error("Role name is required.");
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        name: form.name.trim(),
        description: form.description.trim() || null,
        displayOrder: form.displayOrder.trim() ? Number(form.displayOrder) : null,
      };
      if (form.id) {
        await updateRole(form.id, payload);
        toast.success("Role updated.");
      } else {
        await createRole(payload);
        toast.success("Role created.");
      }
      setForm(null);
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(role: PlacementRoleResponse) {
    try {
      if (role.status === "ACTIVE") {
        await deactivateRole(role.id);
        toast.success(`${role.name} deactivated.`);
      } else {
        await activateRole(role.id);
        toast.success(`${role.name} activated.`);
      }
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteRole(pendingDelete.id);
      toast.success(`${pendingDelete.name} deleted.`);
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
            Manage the target roles students can pursue, and the skills each one requires.
          </p>
        </div>
        <Button size="sm" onClick={openCreate}>
          <Plus className="size-4" /> Add Role
        </Button>
      </div>

      <PlacementTabs active="roles" />

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search roles..."
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
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Required Skills</th>
                <th className="px-4 py-3">Updated</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {filtered.map((role) => (
                <tr key={role.id} className="hover:bg-muted/30">
                  <td className="px-4 py-3">
                    <p className="font-medium text-foreground">{role.name}</p>
                    {role.description ? (
                      <p className="max-w-md truncate text-xs text-muted-foreground">{role.description}</p>
                    ) : null}
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={role.status === "ACTIVE" ? "default" : "outline"}>{role.status}</Badge>
                  </td>
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      className="text-primary hover:underline"
                      onClick={() => navigate(`${ROUTES.adminPlacementRoleSkills}?roleId=${role.id}`)}
                    >
                      {role.skillCount} skill{role.skillCount === 1 ? "" : "s"}
                    </button>
                  </td>
                  <td className="px-4 py-3 text-muted-foreground">
                    {new Date(role.updatedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="icon-sm" onClick={() => openEdit(role)} title="Edit">
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => handleToggleStatus(role)}
                        title={role.status === "ACTIVE" ? "Deactivate" : "Activate"}
                      >
                        {role.status === "ACTIVE" ? <PowerOff className="size-4" /> : <Power className="size-4" />}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => setPendingDelete(role)}
                        title="Delete"
                      >
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
          icon={Briefcase}
          title="No roles found"
          description="Try a different search, or add the first placement role."
          action={
            <Button onClick={openCreate}>
              <Plus className="size-4" /> Add Role
            </Button>
          }
        />
      )}

      <Dialog open={form !== null} onOpenChange={(open) => !open && setForm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{form?.id ? "Edit Role" : "Add Role"}</DialogTitle>
          </DialogHeader>
          {form ? (
            <div className="space-y-4">
              <FormField label="Name" htmlFor="role-name">
                <Input
                  id="role-name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  disabled={submitting}
                  placeholder="e.g. Data Analyst"
                />
              </FormField>
              <FormField label="Description" htmlFor="role-description" hint="Optional">
                <Textarea
                  id="role-description"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  disabled={submitting}
                  rows={3}
                />
              </FormField>
              <FormField label="Display Order" htmlFor="role-order" hint="Optional — lower numbers show first">
                <Input
                  id="role-order"
                  type="number"
                  value={form.displayOrder}
                  onChange={(e) => setForm({ ...form, displayOrder: e.target.value })}
                  disabled={submitting}
                />
              </FormField>
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setForm(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={submitting}>
              {submitting ? "Saving..." : form?.id ? "Save Changes" : "Create Role"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete role?"
        description={`This permanently deletes "${pendingDelete?.name}". If students or content already reference it, delete will be blocked — deactivate it instead.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
