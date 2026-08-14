import { FileQuestion, Plus, Search } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { getQuestionBankStats, listQuestions } from "@/lib/api/endpoints/questionBank";
import type { Difficulty, QuestionStatus, QuestionType } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import {
  DIFFICULTY_OPTIONS,
  QUESTION_STATUS_OPTIONS,
  QUESTION_TYPE_OPTIONS,
  difficultyBadgeVariant,
  difficultyLabel,
  questionTypeLabel,
  statusBadgeVariant,
  statusLabel,
} from "@/pages/admin/questionBankOptions";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

export function QuestionBankPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [difficulty, setDifficulty] = useState<Difficulty | "">("");
  const [type, setType] = useState<QuestionType | "">("");
  const [status, setStatus] = useState<QuestionStatus | "">("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const stats = useAsync(getQuestionBankStats, []);
  const list = useAsync(
    () =>
      listQuestions({
        search: debouncedQuery || undefined,
        difficulty: difficulty || undefined,
        type: type || undefined,
        status: status || undefined,
        page,
        size: PAGE_SIZE,
      }),
    [debouncedQuery, difficulty, type, status, page]
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
          <h1 className="text-2xl font-bold text-foreground">Question Bank</h1>
          <p className="text-sm text-muted-foreground">Create, review and publish assessment questions.</p>
        </div>
        <Button size="sm" onClick={() => navigate(ROUTES.createQuestion)}>
          <Plus className="size-4" /> Create Question
        </Button>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {stats.loading ? (
          Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-20 rounded-xl" />)
        ) : stats.data ? (
          <>
            <StatTile label="Total" value={stats.data.total} />
            <StatTile label="Published" value={stats.data.published} />
            <StatTile label="Draft" value={stats.data.draft} />
            <StatTile label="In Review" value={stats.data.review} />
            <StatTile label="Archived" value={stats.data.archived} />
          </>
        ) : null}
      </div>

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search question text..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <select
          value={difficulty}
          onChange={(e) => handleFilterChange(setDifficulty)(e.target.value as Difficulty | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All difficulties</option>
          {DIFFICULTY_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          value={type}
          onChange={(e) => handleFilterChange(setType)(e.target.value as QuestionType | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All types</option>
          {QUESTION_TYPE_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => handleFilterChange(setStatus)(e.target.value as QuestionStatus | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All statuses</option>
          {QUESTION_STATUS_OPTIONS.map((o) => (
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
            {list.data.content.map((q) => (
              <button
                key={q.id}
                type="button"
                onClick={() => navigate(ROUTES.questionDetail(q.id))}
                className="flex w-full flex-col gap-2 rounded-xl border border-border bg-card p-4 text-left transition-colors hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <p className="truncate text-sm font-medium text-foreground">{q.questionTextPreview}</p>
                  <p className="text-xs text-muted-foreground">
                    {q.skillName}
                    {q.topicName ? ` · ${q.topicName}` : ""} · {questionTypeLabel(q.questionType)}
                    {q.version > 1 ? ` · v${q.version}` : ""}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={difficultyBadgeVariant(q.difficulty)}>{difficultyLabel(q.difficulty)}</Badge>
                  <Badge variant={statusBadgeVariant(q.status)}>{statusLabel(q.status)}</Badge>
                </div>
              </button>
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={FileQuestion}
          title="No questions found"
          description="Try adjusting your search or filters, or create a new question."
          action={
            <Button onClick={() => navigate(ROUTES.createQuestion)}>
              <Plus className="size-4" /> Create Question
            </Button>
          }
        />
      )}
    </div>
  );
}

function StatTile({ label, value }: { label: string; value: number }) {
  return (
    <Card size="sm">
      <CardContent className="space-y-0.5">
        <p className="text-2xl font-bold text-foreground">{value}</p>
        <p className="text-xs text-muted-foreground">{label}</p>
      </CardContent>
    </Card>
  );
}
