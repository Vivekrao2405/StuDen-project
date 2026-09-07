import { Building2, Plus, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { listPlacementCompanies } from "@/lib/api/endpoints/placement";
import type { PlacementCompanyResponse } from "@/lib/api/placementTypes";
import { COMPANY_TYPE_LABEL } from "@/pages/placement/placementDisplay";

const SEARCH_DEBOUNCE_MS = 250;

interface TargetCompaniesStepProps {
  selectedCompanies: PlacementCompanyResponse[];
  onChangeCompanies: (companies: PlacementCompanyResponse[]) => void;
  manualCompanies: string[];
  onChangeManual: (names: string[]) => void;
}

export function TargetCompaniesStep({
  selectedCompanies,
  onChangeCompanies,
  manualCompanies,
  onChangeManual,
}: TargetCompaniesStepProps) {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<PlacementCompanyResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [showManualForm, setShowManualForm] = useState(false);
  const [manualName, setManualName] = useState("");
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setSearched(false);
      return;
    }
    const timer = window.setTimeout(async () => {
      setLoading(true);
      try {
        const page = await listPlacementCompanies({ search: query.trim(), size: 20 });
        setResults(page.content);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
        setSearched(true);
      }
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const selectedIds = new Set(selectedCompanies.map((c) => c.id));
  const visibleResults = results.filter((c) => !selectedIds.has(c.id));

  function handleSelect(company: PlacementCompanyResponse) {
    onChangeCompanies([...selectedCompanies, company]);
    setQuery("");
    inputRef.current?.focus();
  }

  function handleRemoveCompany(id: string) {
    onChangeCompanies(selectedCompanies.filter((c) => c.id !== id));
  }

  function handleRemoveManual(name: string) {
    onChangeManual(manualCompanies.filter((n) => n !== name));
  }

  function handleAddManual() {
    const trimmed = manualName.trim();
    if (!trimmed) return;
    if (manualCompanies.some((n) => n.toLowerCase() === trimmed.toLowerCase())) {
      setManualName("");
      return;
    }
    onChangeManual([...manualCompanies, trimmed]);
    setManualName("");
    setShowManualForm(false);
  }

  return (
    <div className="space-y-4">
      <div className="relative">
        <Input
          ref={inputRef}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search companies..."
          className="h-11"
        />
        {query.trim() ? (
          <div className="absolute z-10 mt-1 w-full rounded-lg border border-border bg-popover p-1 text-popover-foreground shadow-md">
            {loading ? (
              <p className="px-3 py-2 text-sm text-muted-foreground">Searching...</p>
            ) : visibleResults.length > 0 ? (
              <ul className="max-h-64 overflow-y-auto">
                {visibleResults.map((company) => (
                  <li key={company.id}>
                    <button
                      type="button"
                      onClick={() => handleSelect(company)}
                      className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                    >
                      {company.logoUrl ? (
                        <img src={company.logoUrl} alt="" className="size-6 shrink-0 rounded object-contain" />
                      ) : (
                        <Building2 className="size-5 shrink-0 text-muted-foreground" />
                      )}
                      <span>
                        <span className="block font-medium text-foreground">{company.name}</span>
                        <span className="block text-xs text-muted-foreground">{COMPANY_TYPE_LABEL[company.companyType]}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : searched ? (
              <p className="px-3 py-2 text-sm text-muted-foreground">No matching companies.</p>
            ) : null}
          </div>
        ) : null}
      </div>

      {!showManualForm ? (
        <button
          type="button"
          onClick={() => {
            setShowManualForm(true);
            setManualName(query.trim());
          }}
          className="text-sm font-medium text-primary hover:underline"
        >
          Can't find your company? Add it manually
        </button>
      ) : (
        <div className="flex items-center gap-2">
          <Input
            value={manualName}
            onChange={(e) => setManualName(e.target.value)}
            placeholder="Company name"
            className="h-10"
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                e.preventDefault();
                handleAddManual();
              }
            }}
          />
          <Button type="button" size="sm" onClick={handleAddManual} disabled={!manualName.trim()}>
            <Plus className="size-4" /> Add
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setShowManualForm(false)}>
            Cancel
          </Button>
        </div>
      )}

      {selectedCompanies.length > 0 || manualCompanies.length > 0 ? (
        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">Selected companies</p>
          <div className="flex flex-wrap gap-2">
            {selectedCompanies.map((company) => (
              <span
                key={company.id}
                className="inline-flex items-center gap-2 rounded-lg border border-border bg-card py-1.5 pr-2 pl-2.5 text-sm text-foreground"
              >
                {company.name}
                <button
                  type="button"
                  onClick={() => handleRemoveCompany(company.id)}
                  aria-label={`Remove ${company.name}`}
                  className="rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                >
                  <X className="size-3.5" />
                </button>
              </span>
            ))}
            {manualCompanies.map((name) => (
              <span
                key={name}
                className="inline-flex items-center gap-2 rounded-lg border border-dashed border-border bg-card py-1.5 pr-2 pl-2.5 text-sm text-foreground"
                title="Manually added — not an official StuDen-verified company"
              >
                {name}
                <span className="text-[10px] font-medium tracking-wide text-muted-foreground uppercase">Manual</span>
                <button
                  type="button"
                  onClick={() => handleRemoveManual(name)}
                  aria-label={`Remove ${name}`}
                  className="rounded-full p-0.5 text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                >
                  <X className="size-3.5" />
                </button>
              </span>
            ))}
          </div>
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          Search for a company above, or add one manually if it isn't listed. This step is optional.
        </p>
      )}
    </div>
  );
}
