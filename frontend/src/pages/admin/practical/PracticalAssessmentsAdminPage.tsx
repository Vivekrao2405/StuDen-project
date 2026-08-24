import { ClipboardList, FileQuestion, Plus, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Skeleton } from "@/components/ui/skeleton";
import { listAdminPracticalAssessments } from "@/lib/api/endpoints/adminPracticalAssessments";
import type { PracticalAssessmentStatus, PracticalType } from "@/lib/api/practicalTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import {
  assessmentStatusBadgeVariant,
  difficultyBadgeVariant,
  PRACTICAL_TYPE_LABEL,
  PRACTICAL_TYPE_OPTIONS,
} from "@/pages/practical/practicalDisplay";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const STATUS_OPTIONS: { value: PracticalAssessmentStatus; label: string }[] = [
  { value: "DRAFT", label: "Draft" },
  { value: "REVIEW", label: "In Review" },
  { value: "PUBLISHED", label: "Published" },
  { value: "ARCHIVED", label: "Archived" },
];

export function PracticalAssessmentsAdminPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [practicalType, setPracticalType] = useState<PracticalType | "">("");
  const [status, setStatus] = useState<PracticalAssessmentStatus | "">("");
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
      listAdminPracticalAssessments({
        search: debouncedQuery || undefined,
        practicalType: practicalType || undefined,
        status: status || undefined,
        page,
        size: PAGE_SIZE,
      }),
    [debouncedQuery, practicalType, status, page]
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
          <h1 className="text-2xl font-bold text-foreground">Practical Assessments</h1>
          <p className="text-sm text-muted-foreground">Create and manage coding, web, SQL, and design assessments.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.adminPracticalAttempts)}>
            <ClipboardList className="size-4" /> Evaluation Queue
          </Button>
          <Button size="sm" onClick={() => navigate(ROUTES.adminCreatePracticalAssessment)}>
            <Plus className="size-4" /> Create Assessment
          </Button>
        </div>
      </div>

      <SegmentedControl
        value="practical"
        onChange={(value) => {
          if (value === "knowledge") navigate(ROUTES.questionBank);
        }}
        options={[
          { value: "knowledge", label: "Knowledge Questions" },
          { value: "practical", label: "Practical Assessments" },
        ]}
      />

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by title..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <select
          value={practicalType}
          onChange={(e) => handleFilterChange(setPracticalType)(e.target.value as PracticalType | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All types</option>
          {PRACTICAL_TYPE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => handleFilterChange(setStatus)(e.target.value as PracticalAssessmentStatus | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All statuses</option>
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
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
            {list.data.content.map((a) => (
              <button
                key={a.id}
                type="button"
                onClick={() => navigate(ROUTES.adminPracticalAssessmentDetail(a.id))}
                className="flex w-full flex-col gap-2 rounded-xl border border-border bg-card p-4 text-left transition-colors hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <p className="truncate text-sm font-medium text-foreground">{a.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {a.skillName} · {PRACTICAL_TYPE_LABEL[a.practicalType]} · {a.timeLimitMinutes} min · {a.questionCount} question
                    {a.questionCount === 1 ? "" : "s"}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={difficultyBadgeVariant(a.difficulty)}>{a.difficulty}</Badge>
                  <Badge variant={assessmentStatusBadgeVariant(a.status)}>{a.status}</Badge>
                </div>
              </button>
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={FileQuestion}
          title="No practical assessments found"
          description="Try adjusting your search or filters, or create a new assessment."
          action={
            <Button onClick={() => navigate(ROUTES.adminCreatePracticalAssessment)}>
              <Plus className="size-4" /> Create Assessment
            </Button>
          }
        />
      )}
    </div>
  );
}
