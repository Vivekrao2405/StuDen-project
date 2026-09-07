import { Plus, Save, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

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
import { getAdminRole, listAdminRoles, replaceRoleSkills } from "@/lib/api/endpoints/adminPlacement";
import type { RoleSkillRequest, RoleSkillResponse } from "@/lib/api/placementTypes";
import type { AssessmentLevel, SkillResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { QuestionSkillPicker } from "@/pages/admin/QuestionSkillPicker";
import { PlacementTabs } from "@/pages/admin/placement/PlacementTabs";

const PROFICIENCY_OPTIONS: { value: AssessmentLevel; label: string }[] = [
  { value: "BEGINNER", label: "Beginner" },
  { value: "DEVELOPING", label: "Developing" },
  { value: "INTERMEDIATE", label: "Intermediate" },
  { value: "ADVANCED", label: "Advanced" },
  { value: "EXPERT", label: "Expert" },
];

type DraftRow = Pick<
  RoleSkillResponse,
  "skillId" | "skillName" | "skillCategory" | "skillIconSlug" | "skillIconType" | "weight" | "requiredProficiency" | "priority"
>;

function toDraft(skills: RoleSkillResponse[]): DraftRow[] {
  return skills.map((s) => ({ ...s }));
}

export function PlacementRoleSkillsPage() {
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const roleId = searchParams.get("roleId") ?? "";

  const roles = useAsync(() => listAdminRoles(), []);
  const roleDetail = useAsync(() => (roleId ? getAdminRole(roleId) : Promise.resolve(null)), [roleId]);

  const [draft, setDraft] = useState<DraftRow[] | null>(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [newSkill, setNewSkill] = useState<SkillResponse | null>(null);
  const [newWeight, setNewWeight] = useState("1");
  const [newProficiency, setNewProficiency] = useState<AssessmentLevel | "">("");
  const [newPriority, setNewPriority] = useState("0");

  useEffect(() => {
    if (roleDetail.data) {
      setDraft(toDraft(roleDetail.data.skills));
      setDirty(false);
    } else if (!roleId) {
      setDraft(null);
      setDirty(false);
    }
  }, [roleDetail.data, roleId]);

  function selectRole(id: string) {
    setSearchParams(id ? { roleId: id } : {});
  }

  function updateRow(skillId: string, patch: Partial<DraftRow>) {
    setDraft((prev) => (prev ? prev.map((row) => (row.skillId === skillId ? { ...row, ...patch } : row)) : prev));
    setDirty(true);
  }

  function removeRow(skillId: string) {
    setDraft((prev) => (prev ? prev.filter((row) => row.skillId !== skillId) : prev));
    setDirty(true);
  }

  function openAddDialog() {
    setNewSkill(null);
    setNewWeight("1");
    setNewProficiency("");
    setNewPriority("0");
    setAddOpen(true);
  }

  function handleAddSkill() {
    if (!newSkill) {
      toast.error("Choose a skill first.");
      return;
    }
    if (draft?.some((row) => row.skillId === newSkill.id)) {
      toast.error("This skill is already mapped to this role.");
      return;
    }
    const row: DraftRow = {
      skillId: newSkill.id,
      skillName: newSkill.name,
      skillCategory: newSkill.category,
      skillIconSlug: newSkill.iconSlug,
      skillIconType: newSkill.iconType,
      weight: Number(newWeight) || 1,
      requiredProficiency: newProficiency || null,
      priority: Number(newPriority) || 0,
    };
    setDraft((prev) => [...(prev ?? []), row]);
    setDirty(true);
    setAddOpen(false);
  }

  async function handleSave() {
    if (!roleId || !draft) return;
    setSaving(true);
    try {
      const payload: RoleSkillRequest[] = draft.map((row) => ({
        skillId: row.skillId,
        weight: row.weight,
        requiredProficiency: row.requiredProficiency,
        priority: row.priority,
      }));
      await replaceRoleSkills(roleId, payload);
      toast.success("Required skills saved.");
      setDirty(false);
      roleDetail.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Placement Management</h1>
          <p className="text-sm text-muted-foreground">Configure which skills each role requires, and how much each one matters.</p>
        </div>
      </div>

      <PlacementTabs active="role-skills" />

      <FormField label="Role" htmlFor="rsm-role">
        <select
          id="rsm-role"
          value={roleId}
          onChange={(e) => selectRole(e.target.value)}
          className={QB_SELECT_CLASS}
          disabled={roles.loading}
        >
          <option value="">Select a role...</option>
          {(roles.data ?? []).map((role) => (
            <option key={role.id} value={role.id}>
              {role.name}
            </option>
          ))}
        </select>
      </FormField>

      {!roleId ? (
        <EmptyState title="Pick a role" description="Select a role above to view and configure its required skills." />
      ) : roleDetail.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-14 w-full rounded-xl" />
          ))}
        </div>
      ) : roleDetail.error ? (
        <ErrorState message={roleDetail.error.message} onRetry={roleDetail.refetch} />
      ) : (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-foreground">Required Skills</h2>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={openAddDialog}>
                <Plus className="size-4" /> Add Skill
              </Button>
              <Button size="sm" onClick={handleSave} disabled={!dirty || saving}>
                <Save className="size-4" /> {saving ? "Saving..." : "Save Changes"}
              </Button>
            </div>
          </div>

          {draft && draft.length > 0 ? (
            <div className="overflow-x-auto rounded-xl border border-border">
              <table className="w-full min-w-[720px] text-sm">
                <thead className="bg-muted/50 text-left text-xs font-medium text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Skill</th>
                    <th className="px-4 py-3">Weight</th>
                    <th className="px-4 py-3">Required Proficiency</th>
                    <th className="px-4 py-3">Priority</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {draft.map((row) => (
                    <tr key={row.skillId}>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <SkillIcon iconSlug={row.skillIconSlug} iconType={row.skillIconType} className="size-5 shrink-0" />
                          <div>
                            <p className="font-medium text-foreground">{row.skillName}</p>
                            <p className="text-xs text-muted-foreground">{row.skillCategory}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <Input
                          type="number"
                          min={1}
                          value={row.weight}
                          onChange={(e) => updateRow(row.skillId, { weight: Number(e.target.value) || 1 })}
                          className="h-8 w-20"
                        />
                      </td>
                      <td className="px-4 py-3">
                        <select
                          value={row.requiredProficiency ?? ""}
                          onChange={(e) =>
                            updateRow(row.skillId, { requiredProficiency: (e.target.value as AssessmentLevel) || null })
                          }
                          className={QB_SELECT_CLASS}
                        >
                          <option value="">Not set</option>
                          {PROFICIENCY_OPTIONS.map((o) => (
                            <option key={o.value} value={o.value}>
                              {o.label}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="px-4 py-3">
                        <Input
                          type="number"
                          min={0}
                          value={row.priority}
                          onChange={(e) => updateRow(row.skillId, { priority: Number(e.target.value) || 0 })}
                          className="h-8 w-20"
                        />
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Button variant="ghost" size="icon-sm" onClick={() => removeRow(row.skillId)} title="Remove">
                          <Trash2 className="size-4 text-destructive" />
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState
              title="No required skills yet"
              description="Add the skills this role requires, along with weight, required proficiency, and priority."
              action={
                <Button onClick={openAddDialog}>
                  <Plus className="size-4" /> Add Skill
                </Button>
              }
            />
          )}
          {dirty ? <p className="text-xs text-muted-foreground">You have unsaved changes.</p> : null}
        </div>
      )}

      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add Required Skill</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <FormField label="Skill" htmlFor="rsm-new-skill">
              <QuestionSkillPicker value={newSkill} onChange={setNewSkill} />
            </FormField>
            <div className="grid grid-cols-2 gap-4">
              <FormField label="Weight" htmlFor="rsm-new-weight">
                <Input id="rsm-new-weight" type="number" min={1} value={newWeight} onChange={(e) => setNewWeight(e.target.value)} />
              </FormField>
              <FormField label="Priority" htmlFor="rsm-new-priority">
                <Input id="rsm-new-priority" type="number" min={0} value={newPriority} onChange={(e) => setNewPriority(e.target.value)} />
              </FormField>
            </div>
            <FormField label="Required Proficiency" htmlFor="rsm-new-proficiency" hint="Optional">
              <select
                id="rsm-new-proficiency"
                value={newProficiency}
                onChange={(e) => setNewProficiency((e.target.value as AssessmentLevel) || "")}
                className={QB_SELECT_CLASS}
              >
                <option value="">Not set</option>
                {PROFICIENCY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddOpen(false)}>
              Cancel
            </Button>
            <Button onClick={handleAddSkill}>Add</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
