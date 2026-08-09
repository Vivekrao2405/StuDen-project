import { GraduationCap, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { EducationListItem } from "@/components/shared/EducationListItem";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useToast } from "@/hooks/useToast";
import { deleteEducation, listEducation } from "@/lib/api/endpoints/education";
import type { EducationResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { EducationForm } from "@/pages/portfolio/EducationForm";

export function EducationSection() {
  const { data, error, loading, refetch } = useAsync(listEducation, []);
  const [items, setItems] = useState<EducationResponse[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (data) setItems(data);
  }, [data]);

  function handleSaved(item: EducationResponse) {
    setItems((prev) => {
      const exists = prev.some((i) => i.id === item.id);
      return exists ? prev.map((i) => (i.id === item.id ? item : i)) : [item, ...prev];
    });
    setAddOpen(false);
    setEditingId(null);
    toast.success("Education saved.");
  }

  async function handleDelete(id: string) {
    await deleteEducation(id);
    setItems((prev) => prev.filter((i) => i.id !== id));
    toast.success("Education removed.");
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <GraduationCap className="size-4" /> Education
        </CardTitle>
        {!addOpen ? (
          <Button variant="outline" size="sm" onClick={() => setAddOpen(true)}>
            <Plus /> Add education
          </Button>
        ) : null}
      </CardHeader>
      <CardContent className="space-y-4">
        {addOpen ? (
          <EducationForm onSaved={handleSaved} onCancel={() => setAddOpen(false)} />
        ) : null}

        {loading ? (
          <LoadingState label="Loading education..." />
        ) : error ? (
          <ErrorState message={error.message} onRetry={refetch} />
        ) : items.length === 0 && !addOpen ? (
          <p className="py-4 text-center text-sm text-muted-foreground">No education added yet.</p>
        ) : (
          <div className="divide-y divide-border">
            {items.map((item) =>
              editingId === item.id ? (
                <div key={item.id} className="py-3">
                  <EducationForm initial={item} onSaved={handleSaved} onCancel={() => setEditingId(null)} />
                </div>
              ) : (
                <div key={item.id} className="flex items-start justify-between gap-2">
                  <EducationListItem item={item} />
                  <div className="mt-3 flex shrink-0 gap-1">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Edit education"
                      onClick={() => setEditingId(item.id)}
                    >
                      <Pencil />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Delete education"
                      onClick={() => setDeletingId(item.id)}
                    >
                      <Trash2 />
                    </Button>
                  </div>
                </div>
              )
            )}
          </div>
        )}
      </CardContent>

      <ConfirmDeleteDialog
        open={deletingId !== null}
        onOpenChange={(open) => !open && setDeletingId(null)}
        title="Delete this education entry?"
        description="This action cannot be undone."
        onConfirm={() => handleDelete(deletingId as string)}
      />
    </Card>
  );
}
