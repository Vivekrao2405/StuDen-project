import { Plus, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SkillIcon } from "@/components/shared/SkillIcon";
import { searchSkills } from "@/lib/api/endpoints/skills";
import type { SkillResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const SEARCH_DEBOUNCE_MS = 250;

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
        setSearching(false);
        setQuery("");
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

  return (
    <div className="space-y-3">
      {value.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {value.map((skill) => (
            <span
              key={skill.id}
              className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card py-1 pr-1.5 pl-2 text-sm text-foreground"
            >
              <SkillIcon iconSlug={skill.iconSlug} className="size-4" />
              {skill.name}
              <button
                type="button"
                onClick={() => handleRemove(skill.id)}
                disabled={disabled}
                aria-label={`Remove ${skill.name}`}
                className="rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              >
                <X className="size-3.5" />
              </button>
            </span>
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
                      <SkillIcon iconSlug={skill.iconSlug} className="size-5 shrink-0" />
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
    </div>
  );
}
