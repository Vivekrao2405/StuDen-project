import { Briefcase } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { deletePortfolio, getMyPortfolio } from "@/lib/api/endpoints/portfolio";
import type { PortfolioResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { CertificateSection } from "@/pages/portfolio/CertificateSection";
import { EducationSection } from "@/pages/portfolio/EducationSection";
import { PortfolioForm } from "@/pages/portfolio/PortfolioForm";
import { PortfolioSummaryCard } from "@/pages/portfolio/PortfolioSummaryCard";

export function PortfolioDashboardPage() {
  const { data, error, loading, refetch } = useAsync(getMyPortfolio, []);
  const [portfolio, setPortfolio] = useState<PortfolioResponse | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const toast = useToast();

  useEffect(() => {
    if (data) setPortfolio(data);
  }, [data]);

  const notFound = error instanceof ApiError && error.status === 404;

  async function handleDeletePortfolio() {
    await deletePortfolio();
    setPortfolio(null);
    toast.success("Portfolio deleted.");
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">My Portfolio</h1>
        <p className="text-sm text-muted-foreground">
          This is your professional identity on StuDen — visible on your public profile.
        </p>
      </div>

      {loading ? (
        <LoadingState label="Loading your portfolio..." />
      ) : error && !notFound ? (
        <ErrorState message={error.message} onRetry={refetch} />
      ) : !portfolio ? (
        showForm ? (
          <PortfolioForm
            mode="create"
            onSaved={(p) => {
              setPortfolio(p);
              setShowForm(false);
              toast.success("Portfolio created!");
            }}
            onCancel={() => setShowForm(false)}
          />
        ) : (
          <EmptyState
            icon={Briefcase}
            title="You haven't created a portfolio yet"
            description="Create a portfolio to showcase your skills, education and certificates — and get a shareable public profile."
            action={<Button onClick={() => setShowForm(true)}>Create Portfolio</Button>}
          />
        )
      ) : (
        <div className="space-y-6">
          {showForm ? (
            <PortfolioForm
              mode="edit"
              initial={portfolio}
              onSaved={(p) => {
                setPortfolio(p);
                setShowForm(false);
                toast.success("Portfolio updated.");
              }}
              onCancel={() => setShowForm(false)}
            />
          ) : (
            <PortfolioSummaryCard
              portfolio={portfolio}
              onEdit={() => setShowForm(true)}
              onDeleteClick={() => setShowDeleteDialog(true)}
            />
          )}

          <EducationSection />
          <CertificateSection />
        </div>
      )}

      <ConfirmDeleteDialog
        open={showDeleteDialog}
        onOpenChange={setShowDeleteDialog}
        title="Delete your portfolio?"
        description="This permanently deletes your portfolio, education, certificates, and public profile link. This cannot be undone."
        onConfirm={handleDeletePortfolio}
      />
    </div>
  );
}
