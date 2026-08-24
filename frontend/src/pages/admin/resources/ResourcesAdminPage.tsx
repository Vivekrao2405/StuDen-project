import { BookOpen, Plus, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { listAdminResources } from "@/lib/api/endpoints/adminResources";
import type { ResourceStatus, ResourceType } from "@/lib/api/resourceTypes";
import type { SkillResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { QuestionSkillPicker } from "@/pages/admin/QuestionSkillPicker";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";
import { difficultyBadgeVariant } from "@/pages/practical/practicalDisplay";
import { RESOURCE_TYPE_LABEL, RESOURCE_TYPE_OPTIONS, resourceStatusBadgeVariant } from "@/pages/learning/resourceDisplay";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const STATUS_OPTIONS: { value: ResourceStatus; label: string }[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "PUBLISHED", label: "Published" },
  { value: "ARCHIVED", label: "Archived" },
];

export function ResourcesAdminPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [resourceType, setResourceType] = useState<ResourceType | "">("");
  const [status, setStatus] = useState<ResourceStatus | "">("");
  const [skill, setSkill] = useState<SkillResponse | null>(null);
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const list = useAsync(
    () =>
      listAdminResources({
        search: debouncedQuery || undefined,
        resourceType: resourceType || undefined,
        status: status || undefined,
        skillId: skill?.id,
        page,
        size: PAGE_SIZE,
      }),
    [debouncedQuery, resourceType, status, skill, page]
  );

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
          <h1 className="text-2xl font-bold text-foreground">Resources</h1>
          <p className="text-sm text-muted-foreground">Curate learning resources that power students' My Learning.</p>
        </div>
        <Button size="sm" onClick={() => navigate(ROUTES.adminCreateResource)}>
          <Plus className="size-4" /> Create Resource
        </Button>
      </div>

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by title..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <select
          value={resourceType}
          onChange={(e) => handleFilterChange(setResourceType)(e.target.value as ResourceType | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All types</option>
          {RESOURCE_TYPE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => handleFilterChange(setStatus)(e.target.value as ResourceStatus | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <QuestionSkillPicker value={skill} onChange={handleFilterChange(setSkill)} />
      </div>

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : list.data && list.data.content.length > 0 ? (
        <>
          <div className="space-y-2">
            {list.data.content.map((r) => (
              <button
                key={r.id}
                type="button"
                onClick={() => navigate(ROUTES.adminResourceDetail(r.id))}
                className="flex w-full flex-col gap-2 rounded-xl border border-border bg-card p-4 text-left transition-colors hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <p className="truncate text-sm font-medium text-foreground">{r.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {r.skillName} · {RESOURCE_TYPE_LABEL[r.resourceType]}
                    {r.estimatedMinutes ? ` · ${r.estimatedMinutes} min` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  {r.difficulty ? <Badge variant={difficultyBadgeVariant(r.difficulty)}>{r.difficulty}</Badge> : null}
                  <Badge variant={resourceStatusBadgeVariant(r.status)}>{r.status}</Badge>
                </div>
              </button>
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={BookOpen}
          title="No resources found"
          description="Try adjusting your search or filters, or create a new resource."
          action={
            <Button onClick={() => navigate(ROUTES.adminCreateResource)}>
              <Plus className="size-4" /> Create Resource
            </Button>
          }
        />
      )}
    </div>
  );
}
