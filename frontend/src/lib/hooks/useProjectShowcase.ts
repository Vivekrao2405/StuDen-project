import { useEffect, useState } from "react";

import { useToast } from "@/hooks/useToast";
import { deleteProject, listProjects } from "@/lib/api/endpoints/projects";
import type { ProjectResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";

/**
 * Shared list/CRUD state behind both the "Showcase" section on the portfolio dashboard and the
 * standalone Showcase page (/projects) — same data, same actions, different surrounding layout.
 */
export function useProjectShowcase() {
  const { data, error, loading, refetch } = useAsync(listProjects, []);
  const [items, setItems] = useState<ProjectResponse[]>([]);
  const [addOpen, setAddOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (data) setItems(data);
  }, [data]);

  function handleSaved(project: ProjectResponse) {
    setItems((prev) => {
      const exists = prev.some((p) => p.id === project.id);
      return exists ? prev.map((p) => (p.id === project.id ? project : p)) : [project, ...prev];
    });
    setAddOpen(false);
    setEditingId(null);
    toast.success("Project saved.");
  }

  async function handleDelete(id: string) {
    await deleteProject(id);
    setItems((prev) => prev.filter((p) => p.id !== id));
    toast.success("Project removed.");
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
