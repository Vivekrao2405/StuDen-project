import { LayoutGrid, Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { ProjectCard } from "@/components/shared/ProjectCard";
import { useProjectShowcase } from "@/lib/hooks/useProjectShowcase";
import { ProjectForm } from "@/pages/portfolio/ProjectForm";

export function ShowcasePage() {
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
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">My Showcase</h1>
          <p className="text-sm text-muted-foreground">Show the work you're proud of.</p>
        </div>
        {!addOpen ? (
          <Button onClick={() => setAddOpen(true)}>
            <Plus /> Add Project
          </Button>
        ) : null}
      </div>

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

      <ConfirmDeleteDialog
        open={deletingId !== null}
        onOpenChange={(open) => !open && setDeletingId(null)}
        title="Delete this project?"
        description="This will remove the project and its associated showcase media."
        onConfirm={() => handleDelete(deletingId as string)}
      />
    </div>
  );
}
