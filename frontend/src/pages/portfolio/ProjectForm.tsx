import { Plus, X } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import {
  createProject,
  removeProjectMedia,
  reorderProjectMedia,
  setProjectCoverMedia,
  updateProject,
  uploadProjectMedia,
} from "@/lib/api/endpoints/projects";
import type { ProjectRequest, ProjectResponse, ProjectVisibility, SkillResponse } from "@/lib/api/types";
import { isBlank, isValidUrl } from "@/lib/validation";
import { mediaResponseToPendingItems, ProjectMediaUpload, type PendingMediaItem } from "@/pages/portfolio/ProjectMediaUpload";
import { SkillPicker } from "@/pages/portfolio/SkillPicker";

interface LinkRow {
  key: string;
  label: string;
  url: string;
}

interface ProjectFormProps {
  initial?: ProjectResponse;
  onSaved: (project: ProjectResponse) => void;
  onCancel: () => void;
}

interface FormErrors {
  title?: string;
  links?: string;
}

function toLinkRows(initial?: ProjectResponse): LinkRow[] {
  return (initial?.links ?? []).map((l) => ({ key: crypto.randomUUID(), label: l.label, url: l.url }));
}

export function ProjectForm({ initial, onSaved, onCancel }: ProjectFormProps) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [shortDescription, setShortDescription] = useState(initial?.shortDescription ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [skills, setSkills] = useState<SkillResponse[]>(initial?.skills ?? []);
  const [visibility, setVisibility] = useState<ProjectVisibility>(initial?.visibility ?? "PUBLIC");
  const [links, setLinks] = useState<LinkRow[]>(toLinkRows(initial));
  const [mediaItems, setMediaItems] = useState<PendingMediaItem[]>(mediaResponseToPendingItems(initial?.media ?? []));

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(title)) next.title = "Give your project a title.";
    else if (title.length > 255) next.title = "This must be 255 characters or fewer.";

    for (const link of links) {
      const label = link.label.trim();
      const url = link.url.trim();
      if (!label && !url) continue;
      if (!label) {
        next.links = "Each link needs a label.";
        break;
      }
      if (!url) {
        next.links = "Each link needs a URL.";
        break;
      }
      if (!isValidUrl(url)) {
        next.links = `"${label}" needs a valid URL (https://...).`;
        break;
      }
    }

    return next;
  }

  function addLinkRow() {
    setLinks((prev) => [...prev, { key: crypto.randomUUID(), label: "", url: "" }]);
  }

  function updateLinkRow(key: string, field: "label" | "url", value: string) {
    setLinks((prev) => prev.map((row) => (row.key === key ? { ...row, [field]: value } : row)));
  }

  function removeLinkRow(key: string) {
    setLinks((prev) => prev.filter((row) => row.key !== key));
  }

  async function reconcileMedia(projectId: string, response: ProjectResponse): Promise<ProjectResponse> {
    let latest = response;
    const originalIds = new Set((initial?.media ?? []).map((m) => m.id));
    const keptIds = new Set(
      mediaItems.filter((item) => item.kind === "existing").map((item) => item.existingId as string)
    );
    const removedIds = [...originalIds].filter((id) => !keptIds.has(id));

    for (const id of removedIds) {
      latest = await removeProjectMedia(projectId, id);
    }

    const resolvedIds = new Map<string, string>();
    for (const item of mediaItems) {
      if (item.kind === "existing") resolvedIds.set(item.key, item.existingId as string);
    }

    for (const item of mediaItems) {
      if (item.kind !== "new" || !item.file) continue;
      const knownIds = new Set(latest.media.map((m) => m.id));
      latest = await uploadProjectMedia(projectId, item.file);
      const newMedia = latest.media.find((m) => !knownIds.has(m.id));
      if (newMedia) resolvedIds.set(item.key, newMedia.id);
    }

    const finalOrder = mediaItems
      .map((item) => resolvedIds.get(item.key))
      .filter((id): id is string => Boolean(id));

    if (finalOrder.length > 0) {
      latest = await reorderProjectMedia(projectId, finalOrder);

      const coverItem = mediaItems.find((item) => item.isCover);
      const coverId = coverItem ? resolvedIds.get(coverItem.key) : undefined;
      if (coverId) {
        latest = await setProjectCoverMedia(projectId, coverId);
      }
    }

    return latest;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setApiError(null);
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    const payload: ProjectRequest = {
      title: title.trim(),
      shortDescription: shortDescription.trim() || undefined,
      description: description.trim() || undefined,
      visibility,
      skillIds: skills.map((s) => s.id),
      links: links
        .map((l) => ({ label: l.label.trim(), url: l.url.trim() }))
        .filter((l) => l.label && l.url),
    };

    setSubmitting(true);
    try {
      let result = initial ? await updateProject(initial.id, payload) : await createProject(payload);
      result = await reconcileMedia(result.id, result);
      onSaved(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-border p-4" noValidate>
      {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

      <FormField label="Title" htmlFor="title" error={errors.title}>
        <Input
          id="title"
          placeholder="e.g. Brand Identity for a Local Café"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="h-10"
        />
      </FormField>

      <FormField
        label="Short description"
        htmlFor="shortDescription"
        hint="A one-line summary shown on cards and previews."
      >
        <Input
          id="shortDescription"
          placeholder="e.g. Logo, packaging, and social templates for a café launch"
          value={shortDescription}
          onChange={(e) => setShortDescription(e.target.value)}
          className="h-10"
        />
      </FormField>

      <FormField label="Detailed description" htmlFor="description">
        <Textarea
          id="description"
          placeholder="Tell people about the project — the goal, your process, and the outcome..."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={5}
        />
      </FormField>

      <FormField label="Skills" htmlFor="skills">
        <SkillPicker value={skills} onChange={setSkills} disabled={submitting} />
      </FormField>

      <FormField label="Images & videos" htmlFor="media">
        <ProjectMediaUpload items={mediaItems} onChange={setMediaItems} disabled={submitting} />
      </FormField>

      <FormField label="Links" htmlFor="links" error={errors.links} hint="Optional — GitHub, Live Demo, Behance, YouTube, etc.">
        <div className="space-y-2">
          {links.map((row) => (
            <div key={row.key} className="flex gap-2">
              <Input
                placeholder="Label (e.g. Live Demo)"
                value={row.label}
                onChange={(e) => updateLinkRow(row.key, "label", e.target.value)}
                className="h-10 w-2/5"
                disabled={submitting}
              />
              <Input
                placeholder="https://..."
                value={row.url}
                onChange={(e) => updateLinkRow(row.key, "url", e.target.value)}
                className="h-10 flex-1"
                disabled={submitting}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label="Remove link"
                onClick={() => removeLinkRow(row.key)}
                disabled={submitting}
              >
                <X />
              </Button>
            </div>
          ))}
          <Button type="button" variant="outline" size="sm" onClick={addLinkRow} disabled={submitting}>
            <Plus /> Add link
          </Button>
        </div>
      </FormField>

      <div className="flex items-center justify-between rounded-lg border border-border px-4 py-3">
        <div>
          <p className="text-sm font-medium text-foreground">Public</p>
          <p className="text-xs text-muted-foreground">
            {visibility === "PUBLIC"
              ? "Visible on your Showcase and public profile."
              : "Private — only visible to you."}
          </p>
        </div>
        <Switch
          checked={visibility === "PUBLIC"}
          onCheckedChange={(checked) => setVisibility(checked ? "PUBLIC" : "PRIVATE")}
          disabled={submitting}
        />
      </div>

      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={submitting}>
          {submitting ? "Saving..." : "Save"}
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
