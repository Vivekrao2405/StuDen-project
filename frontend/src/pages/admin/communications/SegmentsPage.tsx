import { Layers, Plus, Trash2, Users as UsersIcon } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { createSegment, deleteSegment, listSegments, previewSegment, updateSegment } from "@/lib/api/endpoints/communications";
import type { AudienceCondition, SegmentResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import {
  AudienceBuilderStep,
  parseAudienceFilter,
  serializeAudienceFilter,
} from "@/pages/admin/communications/AudienceBuilderStep";

export function SegmentsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [editing, setEditing] = useState<SegmentResponse | "new" | null>(null);
  const [deleting, setDeleting] = useState<SegmentResponse | null>(null);

  const list = useAsync(() => listSegments(), []);

  async function handleDelete() {
    if (!deleting) return;
    try {
      await deleteSegment(deleting.id);
      toast.success("Segment deleted.");
      setDeleting(null);
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to delete segment.");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Communications</h1>
          <p className="text-sm text-muted-foreground">Saved audience definitions, re-resolved live every time they're used.</p>
        </div>
        <Button size="sm" onClick={() => setEditing("new")}>
          <Plus className="size-4" /> New Segment
        </Button>
      </div>

      <SegmentedControl
        value="segments"
        onChange={(value) => {
          if (value === "campaigns") navigate(ROUTES.adminCommunications);
          if (value === "templates") navigate(ROUTES.adminCommunicationsTemplates);
        }}
        options={[
          { value: "campaigns", label: "Campaigns" },
          { value: "templates", label: "Templates" },
          { value: "segments", label: "Segments" },
        ]}
      />

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : list.data && list.data.length > 0 ? (
        <div className="space-y-2">
          {list.data.map((s) => (
            <SegmentRow key={s.id} segment={s} onEdit={() => setEditing(s)} onDelete={() => setDeleting(s)} />
          ))}
        </div>
      ) : (
        <EmptyState icon={Layers} title="No segments yet" description="Save a reusable audience definition for future campaigns." />
      )}

      {editing ? (
        <SegmentEditorDialog
          segment={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            list.refetch();
          }}
        />
      ) : null}

      <Dialog open={deleting !== null} onOpenChange={(open) => !open && setDeleting(null)}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Delete this segment?</DialogTitle>
            <DialogDescription>
              "{deleting?.name}" will be removed. Campaigns already sent using it are unaffected.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleting(null)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete}>
              <Trash2 className="size-4" /> Delete
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SegmentRow({ segment, onEdit, onDelete }: { segment: SegmentResponse; onEdit: () => void; onDelete: () => void }) {
  const [count, setCount] = useState<number | null>(null);
  const [loadingCount, setLoadingCount] = useState(false);

  async function loadCount() {
    setLoadingCount(true);
    try {
      const result = await previewSegment(segment.id);
      setCount(result.count);
    } catch {
      setCount(null);
    } finally {
      setLoadingCount(false);
    }
  }

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
      <button type="button" onClick={onEdit} className="min-w-0 flex-1 text-left">
        <p className="truncate text-sm font-medium text-foreground">{segment.name}</p>
        <p className="truncate text-xs text-muted-foreground">{segment.description || "No description"}</p>
      </button>
      <div className="flex shrink-0 items-center gap-2">
        <Button size="sm" variant="outline" onClick={loadCount} disabled={loadingCount}>
          <UsersIcon className="size-4" /> {count !== null ? `${count.toLocaleString()} students` : loadingCount ? "Counting..." : "Preview count"}
        </Button>
        <Button size="sm" variant="destructive" onClick={onDelete}>
          <Trash2 className="size-4" />
        </Button>
      </div>
    </div>
  );
}

function SegmentEditorDialog({
  segment,
  onClose,
  onSaved,
}: {
  segment: SegmentResponse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(segment?.name ?? "");
  const [description, setDescription] = useState(segment?.description ?? "");
  const [conditions, setConditions] = useState<AudienceCondition[]>(parseAudienceFilter(segment?.filterJson));
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    if (!name.trim()) {
      toast.error("Give this segment a name.");
      return;
    }
    setSaving(true);
    const request = { name: name.trim(), description: description.trim() || null, filterJson: serializeAudienceFilter(conditions) };
    try {
      if (segment) {
        await updateSegment(segment.id, request);
      } else {
        await createSegment(request);
      }
      toast.success("Segment saved.");
      onSaved();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to save segment.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{segment ? "Edit Segment" : "New Segment"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Name</label>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Inactive React learners" />
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Description (optional)</label>
            <Textarea value={description} onChange={(e) => setDescription(e.target.value)} className="min-h-16" />
          </div>
          <AudienceBuilderStep conditions={conditions} onChange={setConditions} />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? "Saving..." : "Save Segment"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
