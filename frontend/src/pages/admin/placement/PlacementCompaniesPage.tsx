import { Building2, Pencil, Plus, Power, PowerOff, Search, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  activateCompany,
  createCompany,
  deactivateCompany,
  deleteCompany,
  listAdminCompanies,
  updateCompany,
} from "@/lib/api/endpoints/adminPlacement";
import type { CompanyType, PlacementCatalogStatus, PlacementCompanyResponse } from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";
import { PlacementTabs } from "@/pages/admin/placement/PlacementTabs";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;

const COMPANY_TYPE_LABEL: Record<CompanyType, string> = {
  SERVICE_BASED: "Service-based",
  PRODUCT_BASED: "Product-based",
  STARTUP: "Startup",
  CONSULTING: "Consulting",
  GCC: "GCC",
  OTHER: "Other",
};
const COMPANY_TYPE_OPTIONS = Object.entries(COMPANY_TYPE_LABEL) as [CompanyType, string][];

interface CompanyFormState {
  id: string | null;
  name: string;
  companyType: CompanyType;
  description: string;
  logoUrl: string;
}

const EMPTY_FORM: CompanyFormState = { id: null, name: "", companyType: "SERVICE_BASED", description: "", logoUrl: "" };

export function PlacementCompaniesPage() {
  const toast = useToast();
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [companyType, setCompanyType] = useState<CompanyType | "">("");
  const [status, setStatus] = useState<PlacementCatalogStatus | "">("");
  const [page, setPage] = useState(0);
  const [form, setForm] = useState<CompanyFormState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<PlacementCompanyResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const list = useAsync(
    () =>
      listAdminCompanies({
        search: debouncedQuery || undefined,
        companyType: companyType || undefined,
        status: status || undefined,
        page,
        size: PAGE_SIZE,
      }),
    [debouncedQuery, companyType, status, page]
  );

  function openCreate() {
    setForm({ ...EMPTY_FORM });
  }

  function openEdit(company: PlacementCompanyResponse) {
    setForm({
      id: company.id,
      name: company.name,
      companyType: company.companyType,
      description: company.description ?? "",
      logoUrl: company.logoUrl ?? "",
    });
  }

  async function handleSave() {
    if (!form) return;
    if (!form.name.trim()) {
      toast.error("Company name is required.");
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        name: form.name.trim(),
        companyType: form.companyType,
        description: form.description.trim() || null,
        logoUrl: form.logoUrl.trim() || null,
      };
      if (form.id) {
        await updateCompany(form.id, payload);
        toast.success("Company updated.");
      } else {
        await createCompany(payload);
        toast.success("Company created.");
      }
      setForm(null);
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleToggleStatus(company: PlacementCompanyResponse) {
    try {
      if (company.status === "ACTIVE") {
        await deactivateCompany(company.id);
        toast.success(`${company.name} deactivated.`);
      } else {
        await activateCompany(company.id);
        toast.success(`${company.name} activated.`);
      }
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    }
  }

  async function handleDelete() {
    if (!pendingDelete) return;
    setDeleting(true);
    try {
      await deleteCompany(pendingDelete.id);
      toast.success(`${pendingDelete.name} deleted.`);
      setPendingDelete(null);
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setDeleting(false);
    }
  }

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
          <h1 className="text-2xl font-bold text-foreground">Placement Management</h1>
          <p className="text-sm text-muted-foreground">Manage the companies students can target for placement prep.</p>
        </div>
        <Button size="sm" onClick={openCreate}>
          <Plus className="size-4" /> Add Company
        </Button>
      </div>

      <PlacementTabs active="companies" />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="relative sm:col-span-1">
          <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search companies..."
            className="h-11 rounded-full pl-10"
          />
        </div>
        <select
          value={companyType}
          onChange={(e) => handleFilterChange(setCompanyType)(e.target.value as CompanyType | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All types</option>
          {COMPANY_TYPE_OPTIONS.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select
          value={status}
          onChange={(e) => handleFilterChange(setStatus)(e.target.value as PlacementCatalogStatus | "")}
          className={QB_SELECT_CLASS}
        >
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
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
            {list.data.content.map((company) => (
              <div
                key={company.id}
                className="flex flex-col gap-2 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="flex min-w-0 items-center gap-3">
                  {company.logoUrl ? (
                    <img src={company.logoUrl} alt="" className="size-9 shrink-0 rounded-lg border border-border object-contain" />
                  ) : (
                    <div className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-muted">
                      <Building2 className="size-4 text-muted-foreground" />
                    </div>
                  )}
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{company.name}</p>
                    <p className="text-xs text-muted-foreground">{COMPANY_TYPE_LABEL[company.companyType]}</p>
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={company.status === "ACTIVE" ? "default" : "outline"}>{company.status}</Badge>
                  <Button variant="ghost" size="icon-sm" onClick={() => openEdit(company)} title="Edit">
                    <Pencil className="size-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={() => handleToggleStatus(company)}
                    title={company.status === "ACTIVE" ? "Deactivate" : "Activate"}
                  >
                    {company.status === "ACTIVE" ? <PowerOff className="size-4" /> : <Power className="size-4" />}
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => setPendingDelete(company)} title="Delete">
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={Building2}
          title="No companies found"
          description="Try adjusting your search or filters, or add a new company."
          action={
            <Button onClick={openCreate}>
              <Plus className="size-4" /> Add Company
            </Button>
          }
        />
      )}

      <Dialog open={form !== null} onOpenChange={(open) => !open && setForm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{form?.id ? "Edit Company" : "Add Company"}</DialogTitle>
          </DialogHeader>
          {form ? (
            <div className="space-y-4">
              <FormField label="Name" htmlFor="company-name">
                <Input
                  id="company-name"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  disabled={submitting}
                  placeholder="e.g. Acme Corp"
                />
              </FormField>
              <FormField label="Type" htmlFor="company-type">
                <select
                  id="company-type"
                  value={form.companyType}
                  onChange={(e) => setForm({ ...form, companyType: e.target.value as CompanyType })}
                  className={QB_SELECT_CLASS}
                  disabled={submitting}
                >
                  {COMPANY_TYPE_OPTIONS.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </FormField>
              <FormField label="Description" htmlFor="company-description" hint="Optional">
                <Textarea
                  id="company-description"
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  disabled={submitting}
                  rows={3}
                />
              </FormField>
              <FormField label="Logo URL" htmlFor="company-logo" hint="Optional — a hosted image URL">
                <Input
                  id="company-logo"
                  value={form.logoUrl}
                  onChange={(e) => setForm({ ...form, logoUrl: e.target.value })}
                  disabled={submitting}
                  placeholder="https://..."
                />
              </FormField>
            </div>
          ) : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setForm(null)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={submitting}>
              {submitting ? "Saving..." : form?.id ? "Save Changes" : "Create Company"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={pendingDelete !== null}
        onOpenChange={(open) => !open && setPendingDelete(null)}
        title="Delete company?"
        description={`This permanently deletes "${pendingDelete?.name}". If students already target it, delete will be blocked — deactivate it instead.`}
        confirmLabel="Delete"
        loading={deleting}
        onConfirm={handleDelete}
      />
    </div>
  );
}
