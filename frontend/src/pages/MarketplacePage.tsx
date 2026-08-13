import { ArrowUpDown, Plus, Search, SearchX, SlidersHorizontal } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { searchMarketplace } from "@/lib/api/endpoints/marketplace";
import type { MarketplaceResultType, MarketplaceSearchParams, MarketplaceSort } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { MARKETPLACE_RESULT_TYPE_OPTIONS, MARKETPLACE_SORT_OPTIONS } from "@/lib/marketplaceOptions";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";
import { EMPTY_FILTERS, hasActiveFilters, type MarketplaceFilterState } from "@/pages/marketplace/filterState";
import { MarketplaceFilters } from "@/pages/marketplace/MarketplaceFilters";
import { MarketplaceFilterSheet } from "@/pages/marketplace/MarketplaceFilterSheet";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";
import { ServiceResultCard } from "@/pages/marketplace/ServiceResultCard";
import { StudentResultCard } from "@/pages/marketplace/StudentResultCard";

const PAGE_SIZE = 12;
const SEARCH_DEBOUNCE_MS = 300;

type ResultTab = "ALL" | MarketplaceResultType;

export function MarketplacePage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [filters, setFilters] = useState<MarketplaceFilterState>(EMPTY_FILTERS);
  const [sort, setSort] = useState<MarketplaceSort>("recommended");
  const [tab, setTab] = useState<ResultTab>("ALL");
  const [page, setPage] = useState(0);
  const [sheetOpen, setSheetOpen] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const showServiceFilters = tab !== "STUDENT";

  const searchParams: MarketplaceSearchParams = {
    q: debouncedQuery || undefined,
    category: filters.category || undefined,
    location: filters.location || undefined,
    availability: filters.availability || undefined,
    skill: filters.skill || undefined,
    type: tab === "ALL" ? undefined : tab,
    minPrice: showServiceFilters && filters.minPrice ? Number(filters.minPrice) : undefined,
    maxPrice: showServiceFilters && filters.maxPrice ? Number(filters.maxPrice) : undefined,
    maxDeliveryDays: showServiceFilters && filters.maxDeliveryDays ? Number(filters.maxDeliveryDays) : undefined,
    sort,
    page,
    size: PAGE_SIZE,
  };

  const { data, error, loading, refetch } = useAsync(
    () => searchMarketplace(searchParams),
    [
      debouncedQuery,
      filters.category,
      filters.location,
      filters.availability,
      filters.skill,
      filters.minPrice,
      filters.maxPrice,
      filters.maxDeliveryDays,
      tab,
      sort,
      page,
    ]
  );

  function handleFiltersApply(next: MarketplaceFilterState) {
    setFilters(next);
    setPage(0);
  }

  function handleSortChange(next: MarketplaceSort) {
    setSort(next);
    setPage(0);
  }

  function handleTabChange(next: ResultTab) {
    setTab(next);
    setPage(0);
  }

  function handleClearFilters() {
    setFilters(EMPTY_FILTERS);
    setQuery("");
    setDebouncedQuery("");
    setPage(0);
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Marketplace</h1>
          <p className="text-sm text-muted-foreground">Discover students, skills and opportunities.</p>
        </div>
        <Button size="sm" onClick={() => navigate(ROUTES.createService)}>
          <Plus className="size-4" /> Create Service
        </Button>
      </div>

      <SegmentedControl value={tab} onChange={handleTabChange} options={MARKETPLACE_RESULT_TYPE_OPTIONS} />

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search students, services, or skills..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      <MarketplaceFilters
        filters={filters}
        onFiltersChange={handleFiltersApply}
        sort={sort}
        onSortChange={handleSortChange}
        showServiceFilters={showServiceFilters}
      />

      <div className="flex gap-2 lg:hidden">
        <Button variant="outline" size="sm" className="flex-1" onClick={() => setSheetOpen(true)}>
          <SlidersHorizontal className="size-3.5" />
          Filters
          {hasActiveFilters(filters) ? <span className="size-1.5 rounded-full bg-primary" /> : null}
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger className={cn(buttonVariants({ variant: "outline", size: "sm" }), "flex-1")}>
            <ArrowUpDown className="size-3.5" /> Sort
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {MARKETPLACE_SORT_OPTIONS.map((option) => (
              <DropdownMenuItem key={option.value} onClick={() => handleSortChange(option.value)}>
                {option.label}
                {sort === option.value ? <span className="ml-auto text-primary">✓</span> : null}
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {loading ? (
        <LoadingState label="Searching the marketplace..." />
      ) : error ? (
        <ErrorState message={error.message} onRetry={refetch} />
      ) : data && data.content.length > 0 ? (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {data.content.map((result) =>
              result.type === "STUDENT" ? (
                <StudentResultCard key={`student-${result.publicSlug}`} result={result} />
              ) : (
                <ServiceResultCard key={`service-${result.id}`} result={result} />
              )
            )}
          </div>
          <MarketplacePagination page={data.page} totalPages={data.totalPages} onPageChange={setPage} />
        </>
      ) : tab === "SERVICE" ? (
        <EmptyState
          icon={SearchX}
          title="No services yet"
          description="Be the first to offer your skills on StuDen."
          action={
            <Button onClick={() => navigate(ROUTES.createService)}>
              <Plus className="size-4" /> Create a Service
            </Button>
          }
        />
      ) : (
        <EmptyState
          icon={SearchX}
          title="No students or services found"
          description="Try another skill, category, or location."
          action={
            <Button variant="outline" onClick={handleClearFilters}>
              Clear Filters
            </Button>
          }
        />
      )}

      <MarketplaceFilterSheet
        open={sheetOpen}
        onClose={() => setSheetOpen(false)}
        filters={filters}
        onApply={handleFiltersApply}
        showServiceFilters={showServiceFilters}
      />
    </div>
  );
}
