import { LayoutGrid, Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { ProjectCard } from "@/components/shared/ProjectCard";
import { useProjectShowcase } from "@/lib/hooks/useProjectShowcase";
import { ProjectForm } from "@/pages/portfolio/ProjectForm";

export function ShowcaseSection() {
  const {
    items,
    error,
    loading,
    refetch,
    addOpen,
    setAddOpen,
    editingId,
    setEditingId,
    deletingId,
    setDeletingId,
    handleSaved,
    handleDelete,
  } = useProjectShowcase();

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <LayoutGrid className="size-4" /> Showcase
        </CardTitle>
        {!addOpen ? (
          <Button variant="outline" size="sm" onClick={() => setAddOpen(true)}>
            <Plus /> {items.length > 0 ? "Add another project" : "Add Project"}
          </Button>
        ) : null}
      </CardHeader>
      <CardContent className="space-y-4">
        {addOpen ? <ProjectForm onSaved={handleSaved} onCancel={() => setAddOpen(false)} /> : null}

        {loading ? (
          <LoadingState label="Loading your showcase..." />
        ) : error ? (
          <ErrorState message={error.message} onRetry={refetch} />
        ) : items.length === 0 && !addOpen ? (
          <EmptyState
            icon={LayoutGrid}
            title="Your showcase is empty"
            description="Add your first project and start showing what you can do."
            action={
              <Button size="sm" onClick={() => setAddOpen(true)}>
                <Plus /> Add Project
              </Button>
            }
          />
        ) : (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) =>
              editingId === item.id ? (
                <div key={item.id} className="sm:col-span-2 lg:col-span-3">
                  <ProjectForm initial={item} onSaved={handleSaved} onCancel={() => setEditingId(null)} />
                </div>
              ) : (
                <ProjectCard
                  key={item.id}
                  project={item}
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
        title="Delete this project?"
        description="This will remove the project and its associated showcase media."
        onConfirm={() => handleDelete(deletingId as string)}
      />
    </Card>
  );
}
