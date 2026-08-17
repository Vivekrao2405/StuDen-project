import { ClipboardList } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import { listPracticalAttemptQueue } from "@/lib/api/endpoints/adminPracticalAssessments";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";

export function PracticalAttemptQueuePage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const list = useAsync(() => listPracticalAttemptQueue("UNDER_REVIEW", page, 20), [page]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Practical Evaluation Queue</h1>
        <p className="text-sm text-muted-foreground">Attempts awaiting manual review.</p>
      </div>

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 5 }).map((_, i) => (
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
                onClick={() => navigate(ROUTES.adminPracticalAttemptDetail(a.id))}
                className="flex w-full flex-col gap-2 rounded-xl border border-border bg-card p-4 text-left transition-colors hover:bg-muted/50 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="min-w-0 space-y-1">
                  <p className="truncate text-sm font-medium text-foreground">{a.assessmentTitle}</p>
                  <p className="text-xs text-muted-foreground">{a.studentName}</p>
                </div>
                <Badge variant="secondary">{a.status}</Badge>
              </button>
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState icon={ClipboardList} title="Nothing to review" description="No attempts are currently awaiting evaluation." />
      )}
    </div>
  );
}
