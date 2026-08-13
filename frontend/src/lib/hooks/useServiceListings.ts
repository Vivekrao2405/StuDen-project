import { useEffect, useState } from "react";

import { useToast } from "@/hooks/useToast";
import { deleteService, listMyServices } from "@/lib/api/endpoints/services";
import type { ServiceResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";

/** Shared list/CRUD state for a student's own services, mirroring useProjectShowcase.ts. */
export function useServiceListings() {
  const { data, error, loading, refetch } = useAsync(listMyServices, []);
  const [items, setItems] = useState<ServiceResponse[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (data) setItems(data);
  }, [data]);

  function handleSaved(service: ServiceResponse) {
    setItems((prev) => {
      const exists = prev.some((s) => s.id === service.id);
      return exists ? prev.map((s) => (s.id === service.id ? service : s)) : [service, ...prev];
    });
    setAddOpen(false);
    setEditingId(null);
    toast.success("Service saved.");
  }

  async function handleDelete(id: string) {
    await deleteService(id);
    setItems((prev) => prev.filter((s) => s.id !== id));
    toast.success("Service removed.");
  }

  return {
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
  };
}
