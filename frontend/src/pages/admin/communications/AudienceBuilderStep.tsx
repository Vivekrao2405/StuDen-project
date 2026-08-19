import { Loader2, Plus, Trash2, Users as UsersIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SkillChip } from "@/components/shared/SkillChip";
import { searchSkills } from "@/lib/api/endpoints/skills";
import { previewAudience } from "@/lib/api/endpoints/communications";
import type { AudienceCondition, AudienceFilterField, SkillResponse } from "@/lib/api/types";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { ASSESSMENT_LEVEL_OPTIONS, AUDIENCE_FIELD_GROUPS, AUDIENCE_FIELD_META } from "@/pages/admin/communications/communicationsDisplay";

const PREVIEW_DEBOUNCE_MS = 400;

// MVP scope (disclosed): a single flat AND-group of conditions, not full nested AND/OR tree
// editing — the backend supports arbitrary nesting (AudienceFilterGroup/AudienceFilterNode) but
// this builder only ever produces/reads one flat AND list. A filter created another way with
// nested groups would have its nested children silently dropped on re-edit here (filtered out
// below since they lack a `field` key) — acceptable because nothing else in this app writes
// filter_json, so that scenario never actually occurs today.
export function serializeAudienceFilter(conditions: AudienceCondition[]): string {
  return JSON.stringify({
    operator: "AND",
    children: conditions.map((c) => ({ field: c.field, params: c.params })),
  });
}

export function parseAudienceFilter(filterJson: string | null | undefined): AudienceCondition[] {
  if (!filterJson) return [];
  try {
    const root = JSON.parse(filterJson) as { children?: unknown };
    const children = Array.isArray(root?.children) ? root.children : [];
    return children
      .filter((c): c is { field: string; params?: Record<string, string> } => typeof (c as { field?: unknown })?.field === "string")
      .map((c, i) => ({ id: `existing-${i}-${c.field}`, field: c.field as AudienceFilterField, params: c.params ?? {} }));
  } catch {
    return [];
  }
}

let nextConditionId = 0;

function newConditionId() {
  nextConditionId += 1;
  return `new-${Date.now()}-${nextConditionId}`;
}

interface AudienceBuilderStepProps {
  conditions: AudienceCondition[];
  onChange: (conditions: AudienceCondition[]) => void;
}

export function AudienceBuilderStep({ conditions, onChange }: AudienceBuilderStepProps) {
  const [preview, setPreview] = useState<{ count: number; sampleFirstNames: string[] } | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(async () => {
      setPreviewLoading(true);
      setPreviewError(null);
      try {
        const result = await previewAudience(serializeAudienceFilter(conditions));
        setPreview(result);
      } catch {
        setPreview(null);
        setPreviewError("Could not estimate audience size.");
      } finally {
        setPreviewLoading(false);
      }
    }, PREVIEW_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(conditions)]);

  function addCondition() {
    const firstField = AUDIENCE_FIELD_GROUPS[0].fields[0].field;
    onChange([...conditions, { id: newConditionId(), field: firstField, params: {} }]);
  }

  function updateCondition(id: string, patch: Partial<AudienceCondition>) {
    onChange(conditions.map((c) => (c.id === id ? { ...c, ...patch } : c)));
  }

  function removeCondition(id: string) {
    onChange(conditions.filter((c) => c.id !== id));
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 rounded-xl border border-border bg-card px-4 py-3">
        <UsersIcon className="size-4 shrink-0 text-muted-foreground" />
        {previewLoading ? (
          <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
            <Loader2 className="size-3.5 animate-spin" /> Estimating audience...
          </span>
        ) : previewError ? (
          <span className="text-sm text-destructive">{previewError}</span>
        ) : preview ? (
          <span className="text-sm text-foreground">
            <span className="font-semibold">Estimated audience: {preview.count.toLocaleString()} students</span>
            {preview.sampleFirstNames.length > 0 ? (
              <span className="text-muted-foreground"> — e.g. {preview.sampleFirstNames.join(", ")}</span>
            ) : null}
          </span>
        ) : null}
      </div>

      {conditions.length === 0 ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
          No conditions yet — this campaign will target every student. Add a condition to narrow it down.
        </p>
      ) : (
        <div className="space-y-3">
          {conditions.map((condition, index) => (
            <div key={condition.id} className="space-y-2 rounded-xl border border-border bg-card p-3">
              <div className="flex items-center gap-2">
                {index > 0 ? <span className="shrink-0 text-xs font-medium text-muted-foreground">AND</span> : null}
                <select
                  value={condition.field}
                  onChange={(e) =>
                    updateCondition(condition.id, { field: e.target.value as AudienceFilterField, params: {} })
                  }
                  className={QB_SELECT_CLASS}
                >
                  {AUDIENCE_FIELD_GROUPS.map((group) => (
                    <optgroup key={group.label} label={group.label}>
                      {group.fields.map((f) => (
                        <option key={f.field} value={f.field}>
                          {f.label}
                        </option>
                      ))}
                    </optgroup>
                  ))}
                </select>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                  onClick={() => removeCondition(condition.id)}
                  aria-label="Remove condition"
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>
              <ConditionParamsInput
                condition={condition}
                onParamsChange={(params) => updateCondition(condition.id, { params })}
              />
            </div>
          ))}
        </div>
      )}

      <Button type="button" variant="outline" size="sm" onClick={addCondition}>
        <Plus className="size-4" /> Add condition
      </Button>
    </div>
  );
}

function ConditionParamsInput({
  condition,
  onParamsChange,
}: {
  condition: AudienceCondition;
  onParamsChange: (params: Record<string, string>) => void;
}) {
  const meta = AUDIENCE_FIELD_META[condition.field];
  const params = condition.params;

  function setParam(key: string, value: string) {
    onParamsChange({ ...params, [key]: value });
  }

  switch (meta.inputKind) {
    case "none":
      return null;

    case "days":
      return (
        <NumberField label="Days" value={params.days ?? ""} onChange={(v) => setParam("days", v)} />
      );

    case "singleSkill":
      return <SingleSkillField skillId={params.skillId} onChange={(id) => setParam("skillId", id)} />;

    case "requiredSkill":
      return (
        <SingleSkillField skillId={params.skillId} onChange={(id) => setParam("skillId", id)} label="Skill" />
      );

    case "optionalSkill":
      return (
        <SingleSkillField
          skillId={params.skillId}
          onChange={(id) => setParam("skillId", id)}
          label="Skill (optional — any skill if left blank)"
          optional
        />
      );

    case "multiSkill":
      return <MultiSkillField skillIds={params.skillIds ?? ""} onChange={(v) => setParam("skillIds", v)} />;

    case "optionalSkillAndMinScore":
      return (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <SingleSkillField
            skillId={params.skillId}
            onChange={(id) => setParam("skillId", id)}
            label="Skill (optional)"
            optional
          />
          <NumberField label="Minimum score %" value={params.minScore ?? ""} onChange={(v) => setParam("minScore", v)} />
        </div>
      );

    case "optionalSkillAndScoreRange":
      return (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          <SingleSkillField
            skillId={params.skillId}
            onChange={(id) => setParam("skillId", id)}
            label="Skill (optional)"
            optional
          />
          <NumberField label="Min score %" value={params.minScore ?? ""} onChange={(v) => setParam("minScore", v)} />
          <NumberField label="Max score %" value={params.maxScore ?? ""} onChange={(v) => setParam("maxScore", v)} />
        </div>
      );

    case "requiredSkillAndLevel":
      return (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <SingleSkillField skillId={params.skillId} onChange={(id) => setParam("skillId", id)} label="Skill" />
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Level</label>
            <select
              value={params.level ?? ""}
              onChange={(e) => setParam("level", e.target.value)}
              className={QB_SELECT_CLASS}
            >
              <option value="" disabled>
                Select a level
              </option>
              {ASSESSMENT_LEVEL_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      );

    case "dateRange":
      return (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          <DateField
            label="From"
            value={params.from ?? ""}
            onChange={(v) => setParam("from", v ? new Date(`${v}T00:00:00Z`).toISOString() : "")}
          />
          <DateField
            label="To"
            value={params.to ?? ""}
            onChange={(v) => setParam("to", v ? new Date(`${v}T23:59:59Z`).toISOString() : "")}
          />
        </div>
      );

    case "userIds":
      return (
        <div className="space-y-1">
          <label className="text-xs font-medium text-muted-foreground">User IDs (comma-separated)</label>
          <Input
            value={params.userIds ?? ""}
            onChange={(e) => setParam("userIds", e.target.value)}
            placeholder="uuid-1, uuid-2, ..."
            className="h-9 font-mono text-xs"
          />
        </div>
      );
  }
}

function NumberField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  return (
    <div className="space-y-1">
      <label className="text-xs font-medium text-muted-foreground">{label}</label>
      <Input
        type="number"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-9"
        min={0}
      />
    </div>
  );
}

function DateField({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  // value is stored as an ISO instant; the <input type="date"> itself only ever shows the date part.
  const dateOnly = value ? value.slice(0, 10) : "";
  return (
    <div className="space-y-1">
      <label className="text-xs font-medium text-muted-foreground">{label}</label>
      <Input type="date" value={dateOnly} onChange={(e) => onChange(e.target.value)} className="h-9" />
    </div>
  );
}

// No GET /skills/{id} exists in this codebase (only search), so a skill id loaded from an
// existing campaign/segment can't be resolved to a name until the admin searches for it again.
// Disclosed MVP limitation — shows the raw id as a fallback rather than fabricating a name.
function SingleSkillField({
  skillId,
  onChange,
  label,
  optional,
}: {
  skillId: string | undefined;
  onChange: (skillId: string) => void;
  label?: string;
  optional?: boolean;
}) {
  const [resolved, setResolved] = useState<SkillResponse | null>(null);
  const [searching, setSearching] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillResponse[]>([]);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        setResults(await searchSkills(query.trim()));
      } catch {
        setResults([]);
      }
    }, 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (!searching) return;
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setSearching(false);
        setQuery("");
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [searching]);

  function select(skill: SkillResponse) {
    setResolved(skill);
    onChange(skill.id);
    setSearching(false);
    setQuery("");
  }

  return (
    <div className="space-y-1">
      {label ? <label className="text-xs font-medium text-muted-foreground">{label}</label> : null}
      <div ref={containerRef} className="relative">
        {skillId && resolved ? (
          <div className="flex items-center gap-2">
            <SkillChip skill={resolved} variant="compact" onRemove={optional ? () => onChange("") : undefined} />
            <button type="button" onClick={() => setSearching(true)} className="text-xs font-medium text-primary hover:underline">
              Change
            </button>
          </div>
        ) : skillId ? (
          <div className="flex items-center gap-2">
            <span className="rounded-full border border-border bg-card px-2.5 py-1 font-mono text-xs text-muted-foreground">
              Skill: {skillId.slice(0, 8)}…
            </span>
            <button type="button" onClick={() => setSearching(true)} className="text-xs font-medium text-primary hover:underline">
              Change
            </button>
          </div>
        ) : (
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onFocus={() => setSearching(true)}
            placeholder="Search skills..."
            className="h-9"
          />
        )}
        {searching && query.trim() ? (
          <div className="absolute z-10 mt-1 w-full min-w-56 rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-md">
            {results.length > 0 ? (
              <ul className="max-h-56 overflow-y-auto">
                {results.map((skill) => (
                  <li key={skill.id}>
                    <button
                      type="button"
                      onClick={() => select(skill)}
                      className="block w-full rounded-md px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                    >
                      {skill.name}
                      <span className="ml-1.5 text-xs text-muted-foreground">{skill.category}</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="px-3 py-2 text-sm text-muted-foreground">No matching skills.</p>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function MultiSkillField({ skillIds, onChange }: { skillIds: string; onChange: (skillIds: string) => void }) {
  const selectedIds = skillIds ? skillIds.split(",").map((s) => s.trim()).filter(Boolean) : [];
  const [resolved, setResolved] = useState<Record<string, SkillResponse>>({});
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillResponse[]>([]);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        setResults(await searchSkills(query.trim()));
      } catch {
        setResults([]);
      }
    }, 250);
    return () => window.clearTimeout(timer);
  }, [query]);

  function add(skill: SkillResponse) {
    if (selectedIds.includes(skill.id)) return;
    setResolved((prev) => ({ ...prev, [skill.id]: skill }));
    onChange([...selectedIds, skill.id].join(","));
    setQuery("");
  }

  function remove(id: string) {
    onChange(selectedIds.filter((s) => s !== id).join(","));
  }

  return (
    <div className="space-y-2">
      <label className="text-xs font-medium text-muted-foreground">Skills</label>
      {selectedIds.length > 0 ? (
        <div className="flex flex-wrap gap-1.5">
          {selectedIds.map((id) =>
            resolved[id] ? (
              <SkillChip key={id} skill={resolved[id]} variant="compact" onRemove={() => remove(id)} />
            ) : (
              <span
                key={id}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-2.5 py-1 font-mono text-xs text-muted-foreground"
              >
                {id.slice(0, 8)}…
                <button type="button" onClick={() => remove(id)} className="text-muted-foreground hover:text-foreground">
                  ×
                </button>
              </span>
            )
          )}
        </div>
      ) : null}
      <div className="relative">
        <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Add a skill..." className="h-9" />
        {query.trim() ? (
          <div className="absolute z-10 mt-1 w-full min-w-56 rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-md">
            {results.filter((r) => !selectedIds.includes(r.id)).length > 0 ? (
              <ul className="max-h-56 overflow-y-auto">
                {results
                  .filter((r) => !selectedIds.includes(r.id))
                  .map((skill) => (
                    <li key={skill.id}>
                      <button
                        type="button"
                        onClick={() => add(skill)}
                        className="block w-full rounded-md px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                      >
                        {skill.name}
                        <span className="ml-1.5 text-xs text-muted-foreground">{skill.category}</span>
                      </button>
                    </li>
                  ))}
              </ul>
            ) : (
              <p className="px-3 py-2 text-sm text-muted-foreground">No matching skills.</p>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}
