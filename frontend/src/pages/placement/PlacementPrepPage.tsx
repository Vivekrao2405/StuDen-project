import { ArrowRight, Briefcase, Building2, Clock, Search, Sparkles } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  getMyPlacementProfile,
  listPlacementCompanies,
  listPlacementPrepSeries,
  listPlacementRoles,
} from "@/lib/api/endpoints/placement";
import type { StudentPlacementSeriesResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { DIFFICULTY_OPTIONS } from "@/pages/admin/questionBankOptions";
import { PREPARATION_TYPE_OPTIONS, PROGRESS_STATUS_BAR_CLASSES } from "@/pages/placement/placementPrepDisplay";

/** Placement -> Placement Prep (Phase 5): select role/company/preparation type, then discover
 * PUBLISHED series. Filters default from the student's existing Placement Profile so they never
 * have to re-enter their target role/companies just to get here. */
export function PlacementPrepPage() {
  const navigate = useNavigate();
  const profile = useAsync(() => getMyPlacementProfile(), []);
  const roles = useAsync(() => listPlacementRoles(), []);
  const companies = useAsync(() => listPlacementCompanies({ size: 100 }), []);

  const [roleId, setRoleId] = useState("");
  const [companyId, setCompanyId] = useState("");
  const [preparationType, setPreparationType] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const [search, setSearch] = useState("");
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    if (initialized || !profile.data) return;
    setRoleId(profile.data.targetRoleId);
    if (profile.data.targetCompanies.length > 0) {
      setCompanyId(profile.data.targetCompanies[0].id);
    }
    setInitialized(true);
  }, [profile.data, initialized]);

  const list = useAsync(
    () =>
      listPlacementPrepSeries({
        roleId: roleId || undefined,
        companyId: companyId || undefined,
        preparationType: (preparationType || undefined) as never,
        difficulty: (difficulty || undefined) as never,
        search: search || undefined,
        size: 50,
      }),
    [roleId, companyId, preparationType, difficulty, search]
  );

  const series = useMemo(() => list.data?.content ?? [], [list.data]);

  return (
    <div className="space-y-6">
      <div>
        <p className="text-xs font-medium text-muted-foreground">
          <Link to={ROUTES.placement} className="hover:text-foreground">
            Placement
          </Link>
          <span className="mx-1 text-muted-foreground/60">/</span> Placement Prep
        </p>
        <h1 className="mt-1 text-2xl font-bold text-foreground">Placement Prep</h1>
        <p className="text-sm text-muted-foreground">
          Prepare specifically for your target role and companies — structured series, practice, and progress.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <select
          className={QB_SELECT_CLASS}
          value={roleId}
          onChange={(e) => setRoleId(e.target.value)}
          disabled={roles.loading}
        >
          <option value="">All roles</option>
          {(roles.data ?? []).map((role) => (
            <option key={role.id} value={role.id}>
              {role.name}
            </option>
          ))}
        </select>
        <select
          className={QB_SELECT_CLASS}
          value={companyId}
          onChange={(e) => setCompanyId(e.target.value)}
          disabled={companies.loading}
        >
          <option value="">All companies</option>
          {(companies.data?.content ?? []).map((company) => (
            <option key={company.id} value={company.id}>
              {company.name}
            </option>
          ))}
        </select>
        <select className={QB_SELECT_CLASS} value={preparationType} onChange={(e) => setPreparationType(e.target.value)}>
          <option value="">All preparation types</option>
          {PREPARATION_TYPE_OPTIONS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select className={QB_SELECT_CLASS} value={difficulty} onChange={(e) => setDifficulty(e.target.value)}>
          <option value="">All difficulties</option>
          {DIFFICULTY_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      </div>

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search series..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      {list.loading ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-56 w-full rounded-2xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : series.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {series.map((item) => (
            <SeriesCard key={item.id} series={item} onOpen={() => navigate(ROUTES.placementPrepSeriesDetail(item.id))} />
          ))}
        </div>
      ) : (
        <EmptyCatalogState hasFilters={Boolean(roleId || companyId || preparationType || difficulty || search)} />
      )}
    </div>
  );
}

function EmptyCatalogState({ hasFilters }: { hasFilters: boolean }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border bg-muted/20 px-6 py-16 text-center">
      <Sparkles className="size-8 text-muted-foreground" aria-hidden="true" />
      <p className="text-base font-semibold text-foreground">
        {hasFilters ? "No preparation series available for this selection yet." : "Preparation content is being prepared."}
      </p>
      <p className="max-w-md text-sm text-muted-foreground">
        {hasFilters
          ? "Try a different role, company, or preparation type."
          : "Check back soon — StuDen is building out role and company-specific preparation series."}
      </p>
    </div>
  );
}

function SeriesCard({ series, onOpen }: { series: StudentPlacementSeriesResponse; onOpen: () => void }) {
  const started = series.progressStatus !== "NOT_STARTED";
  return (
    <Card className="flex h-full flex-col">
      <CardContent className="flex flex-1 flex-col gap-3 pt-5">
        <div className="min-w-0">
          <h3 className="line-clamp-2 text-sm leading-snug font-semibold text-foreground">{series.name}</h3>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
            <span className="inline-flex items-center gap-1">
              <Briefcase className="size-3.5" /> {series.targetRoleName}
            </span>
            {series.companyName ? (
              <span className="inline-flex items-center gap-1">
                <Building2 className="size-3.5" /> {series.companyName}
              </span>
            ) : null}
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
          <span className="capitalize">{series.difficulty.toLowerCase()}</span>
          {series.estimatedDurationHours ? (
            <span className="inline-flex items-center gap-1">
              <Clock className="size-3.5" /> {series.estimatedDurationHours}h
            </span>
          ) : null}
        </div>

        {series.skillsCovered.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {series.skillsCovered.slice(0, 4).map((skill) => (
              <span key={skill.id} className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                {skill.name}
              </span>
            ))}
            {series.skillsCovered.length > 4 ? (
              <span className="text-xs text-muted-foreground">+{series.skillsCovered.length - 4}</span>
            ) : null}
          </div>
        ) : null}

        <div className="mt-auto space-y-2 pt-1">
          <div className="flex items-center gap-2">
            <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
              <div
                className={`h-full rounded-full transition-all ${PROGRESS_STATUS_BAR_CLASSES[series.progressStatus]}`}
                style={{ width: `${series.progressPercentage}%` }}
              />
            </div>
            <span className="shrink-0 text-xs font-medium text-muted-foreground">{series.progressPercentage}%</span>
          </div>
          <Button size="sm" className="w-full" onClick={onOpen}>
            {started ? "Continue Preparation" : "Start Preparation"}
            <ArrowRight className="size-3.5" />
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
