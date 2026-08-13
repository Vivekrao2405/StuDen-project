import { Briefcase, Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { ServiceCard } from "@/components/shared/ServiceCard";
import type { ServiceResponse } from "@/lib/api/types";
import { useServiceListings } from "@/lib/hooks/useServiceListings";
import { ROUTES } from "@/lib/routes";
import { ServiceForm } from "@/pages/marketplace/ServiceForm";

export function ServicesSection() {
  const navigate = useNavigate();
  const {
    items,
    error,
    loading,
    refetch,
    editingId,
    setEditingId,
    deletingId,
    setDeletingId,
    handleSaved,
    handleDelete,
  } = useServiceListings();

  function handlePublished(service: ServiceResponse) {
    handleSaved(service);
    navigate(ROUTES.serviceDetail(service.id), { state: { justPublished: true } });
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <Briefcase className="size-4" /> Services
        </CardTitle>
        <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.createService)}>
          <Plus /> {items.length > 0 ? "Add another service" : "Add Service"}
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">
        {loading ? (
          <LoadingState label="Loading your services..." />
        ) : error ? (
          <ErrorState message={error.message} onRetry={refetch} />
        ) : items.length === 0 ? (
          <EmptyState
            icon={Briefcase}
            title="No services yet"
            description="Be the first to offer your skills on StuDen."
            action={
              <Button size="sm" onClick={() => navigate(ROUTES.createService)}>
                <Plus /> Create a Service
              </Button>
            }
          />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) =>
              editingId === item.id ? (
                <div key={item.id} className="sm:col-span-2 lg:col-span-3">
                  <ServiceForm
                    initial={item}
                    onSaved={handleSaved}
                    onPublished={handlePublished}
                    onCancel={() => setEditingId(null)}
                  />
                </div>
              ) : (
                <ServiceCard
                  key={item.id}
                  service={item}
                  onEdit={() => setEditingId(item.id)}
                  onDelete={() => setDeletingId(item.id)}
                />
              )
            )}
          </div>
        )}
      </CardContent>

      <ConfirmDeleteDialog
        open={deletingId !== null}
        onOpenChange={(open) => !open && setDeletingId(null)}
        title="Delete this service?"
        description="This service will no longer appear in Marketplace. Linked showcase projects are not affected."
        onConfirm={() => handleDelete(deletingId as string)}
      />
    </Card>
  );
}
