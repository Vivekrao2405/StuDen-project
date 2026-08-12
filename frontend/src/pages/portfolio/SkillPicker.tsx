import { Plus } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SkillChip } from "@/components/shared/SkillChip";
import { SkillIcon } from "@/components/shared/SkillIcon";
import { ApiError } from "@/lib/api/ApiError";
import { createSkill, getSkillCategories, searchSkills } from "@/lib/api/endpoints/skills";
import type { SkillResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const SEARCH_DEBOUNCE_MS = 250;

const SELECT_CLASS =
  "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 md:text-sm dark:bg-input/30";

const OTHER_CATEGORY = "__other__";

interface SkillPickerProps {
  value: SkillResponse[];
  onChange: (skills: SkillResponse[]) => void;
  disabled?: boolean;
}

export function SkillPicker({ value, onChange, disabled }: SkillPickerProps) {
  const [searching, setSearching] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const [showCustomForm, setShowCustomForm] = useState(false);
  const [categories, setCategories] = useState<string[]>([]);
  const [customName, setCustomName] = useState("");
  const [customCategory, setCustomCategory] = useState("");
  const [customCategoryOther, setCustomCategoryOther] = useState("");
  const [customError, setCustomError] = useState<string | null>(null);
  const [creatingCustom, setCreatingCustom] = useState(false);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        const found = await searchSkills(query.trim());
        setResults(found);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, SEARCH_DEBOUNCE_MS);

    return () => window.clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    if (!searching) return;
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        closeSearch();
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [searching]);

  const selectedIds = new Set(value.map((s) => s.id));
  const visibleResults = results.filter((s) => !selectedIds.has(s.id));

  function handleSelect(skill: SkillResponse) {
    onChange([...value, skill]);
    setQuery("");
    inputRef.current?.focus();
  }

  function handleRemove(id: string) {
    onChange(value.filter((s) => s.id !== id));
  }

  function openSearch() {
    setSearching(true);
    window.setTimeout(() => inputRef.current?.focus(), 0);
  }

  function closeSearch() {
    setSearching(false);
    setQuery("");
    setShowCustomForm(false);
    setCustomError(null);
  }

  function openCustomForm() {
    setShowCustomForm(true);
    setCustomName(query.trim());
    setCustomCategory("");
    setCustomCategoryOther("");
    setCustomError(null);
    if (categories.length === 0) {
      getSkillCategories()
        .then(setCategories)
        .catch(() => setCategories([]));
    }
  }

  async function handleCreateCustom() {
    const name = customName.trim();
    const category = (customCategory === OTHER_CATEGORY ? customCategoryOther : customCategory).trim();

    if (!name) {
      setCustomError("Skill name is required.");
      return;
    }
    if (!category) {
      setCustomError("Category is required.");
      return;
    }

    setCreatingCustom(true);
    setCustomError(null);
    try {
      const skill = await createSkill(name, category);
      handleSelect(skill);
      setShowCustomForm(false);
    } catch (err) {
      setCustomError(err instanceof ApiError ? err.message : "Couldn't add that skill. Please try again.");
    } finally {
      setCreatingCustom(false);
    }
  }

  return (
    <div className="space-y-3">
      {value.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {value.map((skill) => (
            <SkillChip
              key={skill.id}
              skill={skill}
              onRemove={() => handleRemove(skill.id)}
              removeDisabled={disabled}
            />
          ))}
        </div>
      ) : null}

      <div ref={containerRef} className="relative">
        {searching ? (
          <Input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search skills..."
            className="h-10"
            disabled={disabled}
          />
        ) : (
          <Button type="button" variant="outline" size="sm" onClick={openSearch} disabled={disabled}>
            <Plus /> Add Skill
          </Button>
        )}

        {searching && query.trim() ? (
          <div className="absolute z-10 mt-1 w-full max-w-sm rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-md">
            {showCustomForm ? (
              <div className="space-y-2 p-2">
                <Input
                  value={customName}
                  onChange={(e) => setCustomName(e.target.value)}
                  placeholder="Skill name"
                  className="h-9"
                  autoFocus
                />
                <select
                  value={customCategory}
                  onChange={(e) => setCustomCategory(e.target.value)}
                  className={SELECT_CLASS}
                >
                  <option value="">Select a category</option>
                  {categories.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                  <option value={OTHER_CATEGORY}>Other…</option>
                </select>
                {customCategory === OTHER_CATEGORY ? (
                  <Input
                    value={customCategoryOther}
                    onChange={(e) => setCustomCategoryOther(e.target.value)}
                    placeholder="Enter category"
                    className="h-9"
                  />
                ) : null}
                {customError ? <p className="text-xs text-destructive">{customError}</p> : null}
                <div className="flex gap-2 pt-1">
                  <Button type="button" size="sm" onClick={handleCreateCustom} disabled={creatingCustom}>
                    {creatingCustom ? "Adding..." : "Add skill"}
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => setShowCustomForm(false)}
                    disabled={creatingCustom}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            ) : (
              <>
                {loading ? (
                  <p className="px-3 py-2 text-sm text-muted-foreground">Searching...</p>
                ) : visibleResults.length > 0 ? (
                  <ul className="max-h-64 overflow-y-auto">
                    {visibleResults.map((skill) => (
                      <li key={skill.id}>
                        <button
                          type="button"
                          onClick={() => handleSelect(skill)}
                          className={cn(
                            "flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                          )}
                        >
                          <SkillIcon
                            iconSlug={skill.iconSlug}
                            iconType={skill.iconType}
                            className="size-5 shrink-0"
                          />
                          <span>
                            <span className="block font-medium text-foreground">{skill.name}</span>
                            <span className="block text-xs text-muted-foreground">{skill.category}</span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p className="px-3 py-2 text-sm text-muted-foreground">No matching skills.</p>
                )}
                <button
                  type="button"
                  onClick={openCustomForm}
                  className="w-full rounded-md border-t border-border px-3 py-2 text-left text-sm font-medium text-primary hover:bg-accent"
                >
                  Can&apos;t find your skill? Add custom skill
                </button>
              </>
            )}
          </div>
        ) : null}
      </div>
    </div>
  );
}
