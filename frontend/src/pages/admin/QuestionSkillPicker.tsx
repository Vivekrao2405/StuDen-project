import { useEffect, useRef, useState } from "react";

import { Input } from "@/components/ui/input";
import { SkillChip } from "@/components/shared/SkillChip";
import { SkillIcon } from "@/components/shared/SkillIcon";
import { searchSkills } from "@/lib/api/endpoints/skills";
import type { SkillResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const SEARCH_DEBOUNCE_MS = 250;

interface QuestionSkillPickerProps {
  value: SkillResponse | null;
  onChange: (skill: SkillResponse | null) => void;
  disabled?: boolean;
}

// Single-select variant of SkillPicker (@/pages/portfolio/SkillPicker) — a Question belongs to
// exactly one Skill, unlike a portfolio/service's many-skills list, so this is its own small
// component rather than constraining the existing multi-select one (which several other flows
// depend on as-is).
export function QuestionSkillPicker({ value, onChange, disabled }: QuestionSkillPickerProps) {
  const [searching, setSearching] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SkillResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        setResults(await searchSkills(query.trim()));
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
        setSearching(false);
        setQuery("");
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [searching]);

  function handleSelect(skill: SkillResponse) {
    onChange(skill);
    setSearching(false);
    setQuery("");
  }

  function openSearch() {
    setSearching(true);
    window.setTimeout(() => inputRef.current?.focus(), 0);
  }

  if (value && !searching) {
    return (
      <div className="flex items-center gap-2">
        <SkillChip skill={value} onRemove={disabled ? undefined : () => onChange(null)} removeDisabled={disabled} />
        <button
          type="button"
          onClick={openSearch}
          disabled={disabled}
          className="text-xs font-medium text-primary hover:underline disabled:pointer-events-none disabled:opacity-50"
        >
          Change
        </button>
      </div>
    );
  }

  return (
    <div ref={containerRef} className="relative">
      <Input
        ref={inputRef}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setSearching(true)}
        placeholder="Search skills..."
        className="h-10"
        disabled={disabled}
      />
      {searching && query.trim() ? (
        <div className="absolute z-10 mt-1 w-full rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-md">
          {loading ? (
            <p className="px-3 py-2 text-sm text-muted-foreground">Searching...</p>
          ) : results.length > 0 ? (
            <ul className="max-h-64 overflow-y-auto">
              {results.map((skill) => (
                <li key={skill.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(skill)}
                    className={cn(
                      "flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                    )}
                  >
                    <SkillIcon iconSlug={skill.iconSlug} iconType={skill.iconType} className="size-5 shrink-0" />
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
        </div>
      ) : null}
    </div>
  );
}
