import { Award, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CertificateListItem } from "@/components/shared/CertificateListItem";
import { ConfirmDeleteDialog } from "@/components/shared/ConfirmDeleteDialog";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useToast } from "@/hooks/useToast";
import { deleteCertificate, listCertificates } from "@/lib/api/endpoints/certificates";
import type { CertificateResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { CertificateForm } from "@/pages/portfolio/CertificateForm";

export function CertificateSection() {
  const { data, error, loading, refetch } = useAsync(listCertificates, []);
  const [items, setItems] = useState<CertificateResponse[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (data) setItems(data);
  }, [data]);

  function handleSaved(item: CertificateResponse) {
    setItems((prev) => {
      const exists = prev.some((i) => i.id === item.id);
      return exists ? prev.map((i) => (i.id === item.id ? item : i)) : [item, ...prev];
    });
    setAddOpen(false);
    setEditingId(null);
    toast.success("Certificate saved.");
  }

  async function handleDelete(id: string) {
    await deleteCertificate(id);
    setItems((prev) => prev.filter((i) => i.id !== id));
    toast.success("Certificate removed.");
  }

  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle className="flex items-center gap-2">
          <Award className="size-4" /> Certificates
        </CardTitle>
        {!addOpen ? (
          <Button variant="outline" size="sm" onClick={() => setAddOpen(true)}>
            <Plus /> Add certificate
          </Button>
        ) : null}
      </CardHeader>
      <CardContent className="space-y-4">
        {addOpen ? <CertificateForm onSaved={handleSaved} onCancel={() => setAddOpen(false)} /> : null}

        {loading ? (
          <LoadingState label="Loading certificates..." />
        ) : error ? (
          <ErrorState message={error.message} onRetry={refetch} />
        ) : items.length === 0 && !addOpen ? (
          <p className="py-4 text-center text-sm text-muted-foreground">No certificates added yet.</p>
        ) : (
          <div className="divide-y divide-border">
            {items.map((item) =>
              editingId === item.id ? (
                <div key={item.id} className="py-3">
                  <CertificateForm initial={item} onSaved={handleSaved} onCancel={() => setEditingId(null)} />
                </div>
              ) : (
                <div key={item.id} className="flex items-start justify-between gap-2">
                  <CertificateListItem item={item} />
                  <div className="mt-3 flex shrink-0 gap-1">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Edit certificate"
                      onClick={() => setEditingId(item.id)}
                    >
                      <Pencil />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label="Delete certificate"
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
        title="Delete this certificate?"
        description="This action cannot be undone."
        onConfirm={() => handleDelete(deletingId as string)}
      />
    </Card>
  );
}
