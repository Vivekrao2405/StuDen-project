import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { FormField } from "@/components/shared/FormField";
import { LoadingState } from "@/components/shared/LoadingState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  archiveResource,
  createResource,
  getAdminResource,
  publishResource,
  unpublishResource,
  updateResource,
} from "@/lib/api/endpoints/adminResources";
import type { ResourceDetail, ResourceRequest, ResourceType } from "@/lib/api/resourceTypes";
import type { Difficulty, SkillResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { QuestionSkillPicker } from "@/pages/admin/QuestionSkillPicker";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";
import { ResourceFileUpload } from "@/pages/admin/resources/ResourceFileUpload";
import { DIFFICULTY_OPTIONS } from "@/pages/practical/practicalDisplay";
import { RESOURCE_TYPE_OPTIONS, resourceStatusBadgeVariant } from "@/pages/learning/resourceDisplay";

export function ResourceEditorPage() {
  const { id } = useParams<{ id: string }>();
  const isEditing = Boolean(id);
  const navigate = useNavigate();
  const toast = useToast();

  const existing = useAsync(() => (id ? getAdminResource(id) : Promise.resolve(null)), [id]);
  const loaded = existing.data ?? undefined;

  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [resourceType, setResourceType] = useState<ResourceType>("EXTERNAL_LINK");
  const [skill, setSkill] = useState<SkillResponse | null>(null);
  const [difficulty, setDifficulty] = useState<Difficulty | "">("");
  const [estimatedMinutes, setEstimatedMinutes] = useState("");
  const [externalUrl, setExternalUrl] = useState("");
  const [notesContent, setNotesContent] = useState("");
  const [tagsText, setTagsText] = useState("");
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [initialized, setInitialized] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (loaded && !initialized) {
      setTitle(loaded.title);
      setDescription(loaded.description ?? "");
      setResourceType(loaded.resourceType);
      setSkill({ id: loaded.skillId, name: loaded.skillName, category: "", iconSlug: null, iconType: "LUCIDE" });
      setDifficulty(loaded.difficulty ?? "");
      setEstimatedMinutes(loaded.estimatedMinutes ? String(loaded.estimatedMinutes) : "");
      setExternalUrl(loaded.externalUrl ?? "");
      setNotesContent(loaded.notesContent ?? "");
      setTagsText(loaded.tags.join(", "));
      setFileUrl(loaded.fileUrl);
      setInitialized(true);
    }
  }, [loaded, initialized]);

  function buildPayload(): ResourceRequest {
    const tags = tagsText.split(",").map((t) => t.trim()).filter(Boolean);
    return {
      title: title.trim(),
      description: description.trim() || null,
      resourceType,
      skillId: skill?.id ?? "",
      difficulty: difficulty || null,
      estimatedMinutes: estimatedMinutes.trim() ? Number(estimatedMinutes) : null,
      externalUrl: resourceType === "EXTERNAL_LINK" || resourceType === "VIDEO" ? externalUrl.trim() || null : null,
      notesContent: resourceType === "NOTES" ? notesContent : null,
      tags,
    };
  }

  async function handleSave() {
    if (!title.trim() || !skill) {
      toast.error("Title and skill are required.");
      return;
    }
    setSubmitting(true);
    try {
      const payload = buildPayload();
      const result = isEditing && id ? await updateResource(id, payload) : await createResource(payload);
      toast.success(isEditing ? "Resource updated." : "Resource created — you can now upload a file or publish it.");
      navigate(ROUTES.adminResourceDetail(result.id), { replace: true });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function runTransition(action: (id: string) => Promise<ResourceDetail>, successMessage: string) {
    if (!id) return;
    setSubmitting(true);
    try {
      await action(id);
      toast.success(successMessage);
      existing.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  if (isEditing && existing.loading) {
    return <LoadingState label="Loading resource..." />;
  }
  if (isEditing && existing.error) {
    return <ErrorState message={existing.error.message} onRetry={existing.refetch} />;
  }

  const needsFile = resourceType === "PDF" || resourceType === "DOCUMENT";

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{isEditing ? "Edit Resource" : "New Resource"}</h1>
          {loaded ? <Badge variant={resourceStatusBadgeVariant(loaded.status)} className="mt-1">{loaded.status}</Badge> : null}
        </div>
        <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.adminResources)}>
          Back
        </Button>
      </div>

      <div className="space-y-6">
        <section className="space-y-4 rounded-xl border border-border p-4">
          <h2 className="text-sm font-semibold text-foreground">Basic Information</h2>
          <FormField label="Title" htmlFor="res-title">
            <Input id="res-title" value={title} onChange={(e) => setTitle(e.target.value)} disabled={submitting} />
          </FormField>
          <FormField label="Short Description" htmlFor="res-description" hint="Optional — shown on the resource card.">
            <Textarea
              id="res-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={submitting}
              rows={2}
            />
          </FormField>
          <FormField label="Skill" htmlFor="res-skill">
            <QuestionSkillPicker value={skill} onChange={setSkill} disabled={submitting} />
          </FormField>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <FormField label="Resource Type" htmlFor="res-type">
              <select
                id="res-type"
                value={resourceType}
                onChange={(e) => setResourceType(e.target.value as ResourceType)}
                className={QB_SELECT_CLASS}
                disabled={submitting}
              >
                {RESOURCE_TYPE_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Difficulty" htmlFor="res-difficulty" hint="Optional">
              <select
                id="res-difficulty"
                value={difficulty}
                onChange={(e) => setDifficulty(e.target.value as Difficulty | "")}
                className={QB_SELECT_CLASS}
                disabled={submitting}
              >
                <option value="">None</option>
                {DIFFICULTY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label="Estimated Minutes" htmlFor="res-estimated" hint="Optional">
              <Input
                id="res-estimated"
                type="number"
                min={0}
                value={estimatedMinutes}
                onChange={(e) => setEstimatedMinutes(e.target.value)}
                disabled={submitting}
              />
            </FormField>
          </div>
          <FormField label="Tags" htmlFor="res-tags" hint="Optional — comma-separated. Match your Question Bank tags for the best matching.">
            <Input
              id="res-tags"
              value={tagsText}
              onChange={(e) => setTagsText(e.target.value)}
              placeholder="e.g. python-dictionaries, python-iteration"
              disabled={submitting}
            />
          </FormField>
        </section>

        <section className="space-y-4 rounded-xl border border-border p-4">
          <h2 className="text-sm font-semibold text-foreground">Content</h2>
          {needsFile ? (
            isEditing && id ? (
              <ResourceFileUpload resourceId={id} resourceType={resourceType} currentUrl={fileUrl} onChange={setFileUrl} />
            ) : (
              <p className="text-sm text-muted-foreground">Save this resource first, then you can upload a file.</p>
            )
          ) : resourceType === "EXTERNAL_LINK" || resourceType === "VIDEO" ? (
            <FormField
              label="URL"
              htmlFor="res-url"
              hint={resourceType === "VIDEO" ? "A direct .mp4/.webm URL plays inline; any other URL opens as a link." : undefined}
            >
              <Input
                id="res-url"
                value={externalUrl}
                onChange={(e) => setExternalUrl(e.target.value)}
                placeholder="https://..."
                disabled={submitting}
              />
            </FormField>
          ) : (
            <FormField label="Notes" htmlFor="res-notes" hint="Plain text — wrap code in ```lang fences to render it as a code block.">
              <Textarea
                id="res-notes"
                value={notesContent}
                onChange={(e) => setNotesContent(e.target.value)}
                disabled={submitting}
                rows={12}
              />
            </FormField>
          )}
        </section>

        <div className="flex flex-wrap gap-2">
          <Button onClick={handleSave} disabled={submitting}>
            {submitting ? "Saving..." : isEditing ? "Save Changes" : "Create Resource"}
          </Button>
          {isEditing && loaded ? (
            <>
              {loaded.status !== "PUBLISHED" ? (
                <Button variant="outline" onClick={() => runTransition(publishResource, "Published.")} disabled={submitting}>
                  Publish
                </Button>
              ) : null}
              {loaded.status === "PUBLISHED" ? (
                <Button variant="outline" onClick={() => runTransition(unpublishResource, "Unpublished.")} disabled={submitting}>
                  Unpublish
                </Button>
              ) : null}
              {loaded.status !== "ARCHIVED" ? (
                <Button variant="outline" onClick={() => runTransition(archiveResource, "Archived.")} disabled={submitting}>
                  Archive
                </Button>
              ) : null}
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
