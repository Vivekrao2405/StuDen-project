import { Archive, Copy, FileText, Plus } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

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
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { archiveTemplate, createTemplate, duplicateTemplate, listTemplates, updateTemplate } from "@/lib/api/endpoints/communications";
import type { CommunicationCategory, TemplateResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { CATEGORY_LABEL, CATEGORY_OPTIONS } from "@/pages/admin/communications/communicationsDisplay";
import { MessageComposerStep, type MessageFields } from "@/pages/admin/communications/MessageComposerStep";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";

export function TemplatesPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [editing, setEditing] = useState<TemplateResponse | "new" | null>(null);

  const list = useAsync(() => listTemplates(true), []);

  async function handleDuplicate(id: string) {
    try {
      await duplicateTemplate(id);
      toast.success("Template duplicated.");
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to duplicate template.");
    }
  }

  async function handleArchive(id: string) {
    try {
      await archiveTemplate(id);
      toast.success("Template archived.");
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to archive template.");
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Communications</h1>
          <p className="text-sm text-muted-foreground">Reusable message templates for campaigns.</p>
        </div>
        <Button size="sm" onClick={() => setEditing("new")}>
          <Plus className="size-4" /> New Template
        </Button>
      </div>

      <SegmentedControl
        value="templates"
        onChange={(value) => {
          if (value === "campaigns") navigate(ROUTES.adminCommunications);
          if (value === "segments") navigate(ROUTES.adminCommunicationsSegments);
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
          {list.data.map((t) => (
            <div key={t.id} className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
              <button type="button" onClick={() => setEditing(t)} className="min-w-0 flex-1 text-left">
                <p className="truncate text-sm font-medium text-foreground">{t.name}</p>
                <p className="truncate text-xs text-muted-foreground">{t.emailSubject || t.pushTitle || t.inappTitle || "No content yet"}</p>
              </button>
              <div className="flex shrink-0 items-center gap-2">
                <Badge variant="secondary">{CATEGORY_LABEL[t.category]}</Badge>
                {t.archived ? <Badge variant="outline">Archived</Badge> : null}
                <Button size="sm" variant="outline" onClick={() => handleDuplicate(t.id)}>
                  <Copy className="size-4" /> Duplicate
                </Button>
                {!t.archived ? (
                  <Button size="sm" variant="outline" onClick={() => handleArchive(t.id)}>
                    <Archive className="size-4" /> Archive
                  </Button>
                ) : null}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState icon={FileText} title="No templates yet" description="Create a reusable template for future campaigns." />
      )}

      {editing ? (
        <TemplateEditorDialog
          template={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            list.refetch();
          }}
        />
      ) : null}
    </div>
  );
}

function TemplateEditorDialog({
  template,
  onClose,
  onSaved,
}: {
  template: TemplateResponse | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const toast = useToast();
  const [name, setName] = useState(template?.name ?? "");
  const [category, setCategory] = useState<CommunicationCategory>(template?.category ?? "CUSTOM");
  const [message, setMessage] = useState<MessageFields>({
    emailSubject: template?.emailSubject ?? "",
    emailBodyHtml: template?.emailBodyHtml ?? "",
    pushTitle: template?.pushTitle ?? "",
    pushBody: template?.pushBody ?? "",
    inappTitle: template?.inappTitle ?? "",
    inappBody: template?.inappBody ?? "",
    ctaText: template?.ctaText ?? "",
    ctaUrl: template?.ctaUrl ?? "",
  });
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    if (!name.trim()) {
      toast.error("Give this template a name.");
      return;
    }
    setSaving(true);
    const request = {
      name: name.trim(),
      category,
      emailSubject: message.emailSubject || null,
      emailBodyHtml: message.emailBodyHtml || null,
      pushTitle: message.pushTitle || null,
      pushBody: message.pushBody || null,
      inappTitle: message.inappTitle || null,
      inappBody: message.inappBody || null,
      ctaText: message.ctaText || null,
      ctaUrl: message.ctaUrl || null,
    };
    try {
      if (template) {
        await updateTemplate(template.id, request);
      } else {
        await createTemplate(request);
      }
      toast.success("Template saved.");
      onSaved();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to save template.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>{template ? "Edit Template" : "New Template"}</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Name</label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Assessment reminder" />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Category</label>
              <select value={category} onChange={(e) => setCategory(e.target.value as CommunicationCategory)} className={QB_SELECT_CLASS}>
                {CATEGORY_OPTIONS.map((c) => (
                  <option key={c} value={c}>
                    {CATEGORY_LABEL[c]}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <MessageComposerStep value={message} onChange={setMessage} sendEmail sendPush sendInapp />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? "Saving..." : "Save Template"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
