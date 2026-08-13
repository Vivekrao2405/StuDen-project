import { Plus, X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { useAuth } from "@/features/auth/useAuth";
import { ApiError } from "@/lib/api/ApiError";
import { listProjects } from "@/lib/api/endpoints/projects";
import {
  createService,
  deactivateService,
  publishService,
  reactivateService,
  removeServiceMedia,
  reorderServiceMedia,
  setServiceCoverMedia,
  updateService,
  uploadServiceMedia,
} from "@/lib/api/endpoints/services";
import type { MarketplaceCategory, ProjectResponse, ServiceRequest, ServiceResponse, SkillResponse } from "@/lib/api/types";
import { isBlank, isValidUrl } from "@/lib/validation";
import { MARKETPLACE_CATEGORY_OPTIONS, MARKETPLACE_DELIVERY_OPTIONS, SERVICE_LINK_PRESETS } from "@/lib/marketplaceOptions";
import { mediaResponseToPendingItems, ProjectMediaUpload, type PendingMediaItem } from "@/pages/portfolio/ProjectMediaUpload";
import { SkillPicker } from "@/pages/portfolio/SkillPicker";
import { ServiceDetailView, type ServiceDetailViewData } from "@/pages/marketplace/ServiceDetailView";
import { MARKETPLACE_SELECT_CLASS } from "@/pages/marketplace/marketplaceSelectClass";

interface LinkRow {
  key: string;
  label: string;
  url: string;
}

interface DeliverableRow {
  key: string;
  text: string;
}

interface ServiceFormProps {
  initial?: ServiceResponse;
  onSaved: (service: ServiceResponse) => void;
  onPublished: (service: ServiceResponse) => void;
  onCancel: () => void;
}

interface FormErrors {
  title?: string;
  category?: string;
  links?: string;
  description?: string;
  price?: string;
  delivery?: string;
}

function toLinkRows(initial?: ServiceResponse): LinkRow[] {
  return (initial?.links ?? []).map((l) => ({ key: crypto.randomUUID(), label: l.label, url: l.url }));
}

function toDeliverableRows(initial?: ServiceResponse): DeliverableRow[] {
  return (initial?.whatYoullReceive ?? []).map((text) => ({ key: crypto.randomUUID(), text }));
}

function initialDeliverySelect(initial?: ServiceResponse): string {
  if (initial?.deliveryDays == null) return "";
  const preset = MARKETPLACE_DELIVERY_OPTIONS.find((o) => o.value === String(initial.deliveryDays));
  return preset ? preset.value : "custom";
}

function initialDeliveryCustom(initial?: ServiceResponse): string {
  if (initial?.deliveryDays == null) return "";
  const isPreset = MARKETPLACE_DELIVERY_OPTIONS.some((o) => o.value === String(initial.deliveryDays));
  return isPreset ? "" : String(initial.deliveryDays);
}

export function ServiceForm({ initial, onSaved, onPublished, onCancel }: ServiceFormProps) {
  const { user } = useAuth();
  const [step, setStep] = useState<"form" | "preview">("form");

  const [title, setTitle] = useState(initial?.title ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [category, setCategory] = useState<MarketplaceCategory | "">(initial?.category ?? "");
  const [location, setLocation] = useState(initial?.location ?? "");
  const [skills, setSkills] = useState<SkillResponse[]>(initial?.skills ?? []);
  const [deliverables, setDeliverables] = useState<DeliverableRow[]>(toDeliverableRows(initial));
  const [priceAmount, setPriceAmount] = useState(initial?.priceAmount != null ? String(initial.priceAmount) : "");
  const [deliverySelect, setDeliverySelect] = useState(initialDeliverySelect(initial));
  const [deliveryCustom, setDeliveryCustom] = useState(initialDeliveryCustom(initial));
  const [available, setAvailable] = useState(initial?.available ?? true);
  const [links, setLinks] = useState<LinkRow[]>(toLinkRows(initial));
  const [mediaItems, setMediaItems] = useState<PendingMediaItem[]>(mediaResponseToPendingItems(initial?.media ?? []));
  const [linkedProjectIds, setLinkedProjectIds] = useState<Set<string>>(
    new Set((initial?.linkedProjects ?? []).map((p) => p.id))
  );

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [showcaseProjects, setShowcaseProjects] = useState<ProjectResponse[]>([]);
  const [showcaseLoading, setShowcaseLoading] = useState(true);

  // Loaded exactly once on mount — no re-fetch on every keystroke/render, and no upfront skill
  // fetch (SkillPicker keeps its own on-demand debounced search).
  useEffect(() => {
    let cancelled = false;
    listProjects()
      .then((projects) => {
        if (!cancelled) setShowcaseProjects(projects.filter((p) => p.visibility === "PUBLIC"));
      })
      .catch(() => {
        if (!cancelled) setShowcaseProjects([]);
      })
      .finally(() => {
        if (!cancelled) setShowcaseLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function resolveDeliveryDays(): number | undefined {
    if (deliverySelect === "custom") {
      const n = Number(deliveryCustom);
      return deliveryCustom.trim() && Number.isFinite(n) && n > 0 ? n : undefined;
    }
    return deliverySelect ? Number(deliverySelect) : undefined;
  }

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(title)) next.title = "Give your service a title.";
    else if (title.length > 255) next.title = "This must be 255 characters or fewer.";
    if (!category) next.category = "Choose a category.";

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

  function validateForPublish(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(description)) next.description = "Add a description before publishing.";
    if (!priceAmount.trim() || Number(priceAmount) <= 0) next.price = "Set a starting price before publishing.";
    if (resolveDeliveryDays() === undefined) next.delivery = "Set an estimated delivery time before publishing.";
    return next;
  }

  function buildPayload(): ServiceRequest {
    return {
      title: title.trim(),
      description: description.trim() || undefined,
      category: category as MarketplaceCategory,
      location: location.trim() || undefined,
      skillIds: skills.map((s) => s.id),
      whatYoullReceive: deliverables.map((d) => d.text.trim()).filter(Boolean),
      priceAmount: priceAmount.trim() ? Number(priceAmount) : undefined,
      currency: "INR",
      deliveryDays: resolveDeliveryDays(),
      available,
      linkedProjectIds: Array.from(linkedProjectIds),
      links: links
        .map((l) => ({ label: l.label.trim(), url: l.url.trim() }))
        .filter((l) => l.label && l.url),
    };
  }

  async function reconcileMedia(serviceId: string, response: ServiceResponse): Promise<ServiceResponse> {
    let latest = response;
    const originalIds = new Set((initial?.media ?? []).map((m) => m.id));
    const keptIds = new Set(
      mediaItems.filter((item) => item.kind === "existing").map((item) => item.existingId as string)
    );
    const removedIds = [...originalIds].filter((id) => !keptIds.has(id));

    for (const id of removedIds) {
      latest = await removeServiceMedia(serviceId, id);
    }

    const resolvedIds = new Map<string, string>();
    for (const item of mediaItems) {
      if (item.kind === "existing") resolvedIds.set(item.key, item.existingId as string);
    }

    const newItems = mediaItems.filter(
      (item): item is PendingMediaItem & { file: File } => item.kind === "new" && Boolean(item.file)
    );
    if (newItems.length > 0) {
      const uploaded = await Promise.all(
        newItems.map(async (item) => ({ key: item.key, media: await uploadServiceMedia(serviceId, item.file) }))
      );
      uploaded.forEach(({ key, media }) => resolvedIds.set(key, media.id));
    }

    const finalOrder = mediaItems
      .map((item) => resolvedIds.get(item.key))
      .filter((id): id is string => Boolean(id));

    if (finalOrder.length > 0) {
      latest = await reorderServiceMedia(serviceId, finalOrder);

      const coverItem = mediaItems.find((item) => item.isCover);
      const coverId = coverItem ? resolvedIds.get(coverItem.key) : undefined;
      if (coverId) {
        latest = await setServiceCoverMedia(serviceId, coverId);
      }
    }

    return latest;
  }

  async function handleSave() {
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setApiError(null);
    setSubmitting(true);
    try {
      let result = initial ? await updateService(initial.id, buildPayload()) : await createService(buildPayload());
      result = await reconcileMedia(result.id, result);
      onSaved(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleContinueToPreview() {
    const combined = { ...validate(), ...validateForPublish() };
    setErrors(combined);
    if (Object.keys(combined).length > 0) return;
    setApiError(null);
    setStep("preview");
  }

  async function handlePublish() {
    setApiError(null);
    setSubmitting(true);
    try {
      let result = initial ? await updateService(initial.id, buildPayload()) : await createService(buildPayload());
      result = await reconcileMedia(result.id, result);
      result = await publishService(result.id);
      onPublished(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
      setStep("form");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDeactivate() {
    if (!initial) return;
    setApiError(null);
    setSubmitting(true);
    try {
      const result = await deactivateService(initial.id);
      onSaved(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleReactivate() {
    if (!initial) return;
    setApiError(null);
    setSubmitting(true);
    try {
      const result = await reactivateService(initial.id);
      onSaved(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function updateLinkRow(key: string, field: "label" | "url", value: string) {
    setLinks((prev) => prev.map((row) => (row.key === key ? { ...row, [field]: value } : row)));
  }

  function removeLinkRow(key: string) {
    setLinks((prev) => prev.filter((row) => row.key !== key));
  }

  function addDeliverableRow() {
    setDeliverables((prev) => [...prev, { key: crypto.randomUUID(), text: "" }]);
  }

  function updateDeliverableRow(key: string, text: string) {
    setDeliverables((prev) => prev.map((row) => (row.key === key ? { ...row, text } : row)));
  }

  function removeDeliverableRow(key: string) {
    setDeliverables((prev) => prev.filter((row) => row.key !== key));
  }

  function toggleLinkedProject(projectId: string) {
    setLinkedProjectIds((prev) => {
      const next = new Set(prev);
      if (next.has(projectId)) next.delete(projectId);
      else next.add(projectId);
      return next;
    });
  }

  if (step === "preview") {
    const previewData: ServiceDetailViewData = {
      title: title.trim() || "Untitled service",
      description: description.trim() || null,
      category,
      location: location.trim() || null,
      priceAmount: priceAmount.trim() ? Number(priceAmount) : null,
      currency: "INR",
      deliveryDays: resolveDeliveryDays() ?? null,
      available,
      skills,
      whatYoullReceive: deliverables.map((d) => d.text.trim()).filter(Boolean),
      media: mediaItems
        .filter((item) => item.previewUrl)
        .map((item) => ({
          id: item.key,
          url: item.previewUrl as string,
          thumbnailUrl: item.previewUrl,
          mediaType: item.mediaType,
          cover: item.isCover,
        })),
      links: links.map((l) => ({ label: l.label.trim(), url: l.url.trim() })).filter((l) => l.label && l.url),
      linkedProjects: showcaseProjects
        .filter((p) => linkedProjectIds.has(p.id))
        .map((p) => ({ id: p.id, title: p.title, coverImageUrl: p.coverImageUrl })),
    };

    return (
      <div className="space-y-4 rounded-lg border border-border p-4">
        <h2 className="text-sm font-semibold text-foreground">Preview</h2>
        {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}
        <ServiceDetailView
          data={previewData}
          provider={{ name: user?.fullName ?? "You", profileImageUrl: user?.profileImageUrl ?? null }}
        />
        <div className="flex gap-2 border-t border-border pt-4">
          <Button type="button" variant="outline" size="sm" onClick={() => setStep("form")} disabled={submitting}>
            ← Edit
          </Button>
          <Button type="button" size="sm" onClick={handlePublish} disabled={submitting}>
            {submitting ? "Publishing..." : "Publish Service"}
          </Button>
        </div>
      </div>
    );
  }

  const status = initial?.status;

  return (
    <div className="space-y-4 rounded-lg border border-border p-4">
      {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

      <FormField label="Service title" htmlFor="service-title" error={errors.title}>
        <Input
          id="service-title"
          placeholder="e.g. Power BI Dashboard Development"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="h-10"
          disabled={submitting}
        />
      </FormField>

      <FormField label="Category" htmlFor="service-category" error={errors.category}>
        <select
          id="service-category"
          value={category}
          onChange={(e) => setCategory(e.target.value as MarketplaceCategory)}
          className={MARKETPLACE_SELECT_CLASS}
          disabled={submitting}
        >
          <option value="">Select a category</option>
          {MARKETPLACE_CATEGORY_OPTIONS.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </FormField>

      <FormField label="Skills" htmlFor="service-skills">
        <SkillPicker value={skills} onChange={setSkills} disabled={submitting} />
      </FormField>

      <FormField label="Location" htmlFor="service-location" hint="Optional — shown to help students find local help.">
        <Input
          id="service-location"
          placeholder="e.g. Hyderabad"
          value={location}
          onChange={(e) => setLocation(e.target.value)}
          className="h-10"
          disabled={submitting}
        />
      </FormField>

      <FormField label="What I offer" htmlFor="service-description" error={errors.description}>
        <Textarea
          id="service-description"
          placeholder="Describe what you'll do for the student who books this service..."
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={5}
          disabled={submitting}
        />
      </FormField>

      <FormField label="What you'll receive" htmlFor="service-deliverables" hint="Optional — a short list of what's included.">
        <div className="space-y-2">
          {deliverables.map((row) => (
            <div key={row.key} className="flex gap-2">
              <Input
                value={row.text}
                onChange={(e) => updateDeliverableRow(row.key, e.target.value)}
                placeholder="e.g. Final Power BI file"
                className="h-9"
                disabled={submitting}
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label="Remove item"
                onClick={() => removeDeliverableRow(row.key)}
                disabled={submitting}
              >
                <X />
              </Button>
            </div>
          ))}
          <Button type="button" variant="outline" size="sm" onClick={addDeliverableRow} disabled={submitting}>
            <Plus /> Add item
          </Button>
        </div>
      </FormField>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField label="Starting price (₹)" htmlFor="service-price" error={errors.price}>
          <Input
            id="service-price"
            type="number"
            min={1}
            placeholder="e.g. 1500"
            value={priceAmount}
            onChange={(e) => setPriceAmount(e.target.value)}
            className="h-10"
            disabled={submitting}
          />
        </FormField>

        <FormField label="Estimated delivery" htmlFor="service-delivery" error={errors.delivery}>
          <div className="space-y-2">
            <select
              id="service-delivery"
              value={deliverySelect}
              onChange={(e) => setDeliverySelect(e.target.value)}
              className={MARKETPLACE_SELECT_CLASS}
              disabled={submitting}
            >
              <option value="">Select delivery time</option>
              {MARKETPLACE_DELIVERY_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
            {deliverySelect === "custom" ? (
              <Input
                type="number"
                min={1}
                placeholder="Number of days"
                value={deliveryCustom}
                onChange={(e) => setDeliveryCustom(e.target.value)}
                className="h-9"
                disabled={submitting}
              />
            ) : null}
          </div>
        </FormField>
      </div>

      <div className="flex items-center justify-between rounded-lg border border-border px-4 py-3">
        <div>
          <p className="text-sm font-medium text-foreground">Available</p>
          <p className="text-xs text-muted-foreground">
            {available
              ? "Bookable and discoverable in Marketplace."
              : "Stays published, but clearly marked as currently unavailable."}
          </p>
        </div>
        <Switch checked={available} onCheckedChange={setAvailable} disabled={submitting} />
      </div>

      <FormField label="Add examples of your work" htmlFor="service-projects">
        {showcaseLoading ? (
          <p className="text-sm text-muted-foreground">Loading your showcase projects...</p>
        ) : showcaseProjects.length === 0 ? (
          <div className="rounded-lg border border-dashed border-border p-4 text-center">
            <p className="text-sm font-medium text-foreground">No showcase projects yet</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Adding your previous work can help people understand what you offer.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {showcaseProjects.map((project) => {
              const checked = linkedProjectIds.has(project.id);
              return (
                <button
                  key={project.id}
                  type="button"
                  onClick={() => toggleLinkedProject(project.id)}
                  disabled={submitting}
                  className={`flex items-center gap-2 rounded-lg border p-2 text-left text-xs transition-colors ${
                    checked ? "border-primary bg-accent" : "border-border hover:bg-muted/50"
                  }`}
                >
                  <span
                    className={`flex size-4 shrink-0 items-center justify-center rounded border ${
                      checked ? "border-primary bg-primary text-primary-foreground" : "border-input"
                    }`}
                  >
                    {checked ? "✓" : ""}
                  </span>
                  <span className="line-clamp-2 font-medium text-foreground">{project.title}</span>
                </button>
              );
            })}
          </div>
        )}
      </FormField>

      <FormField label="Service media" htmlFor="service-media" hint="Optional — a banner, images, or a short video.">
        <ProjectMediaUpload items={mediaItems} onChange={setMediaItems} disabled={submitting} />
      </FormField>

      <FormField label="Links" htmlFor="service-links" error={errors.links} hint="Optional — GitHub, Live Demo, Behance, YouTube, etc.">
        <div className="space-y-2">
          {links.map((row) => (
            <div key={row.key} className="flex flex-col gap-1.5 sm:flex-row sm:gap-2">
              <Input
                placeholder="Label (e.g. Live Demo)"
                value={row.label}
                onChange={(e) => updateLinkRow(row.key, "label", e.target.value)}
                className="h-10 sm:w-2/5"
                disabled={submitting}
              />
              <div className="flex gap-2">
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
            </div>
          ))}
          <div className="flex flex-wrap gap-1.5">
            {SERVICE_LINK_PRESETS.map((preset) => (
              <Button
                key={preset}
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setLinks((prev) => [...prev, { key: crypto.randomUUID(), label: preset, url: "" }])}
                disabled={submitting}
              >
                <Plus /> {preset}
              </Button>
            ))}
          </div>
        </div>
      </FormField>

      <div className="flex flex-wrap gap-2 border-t border-border pt-4">
        <Button type="button" size="sm" onClick={handleSave} disabled={submitting}>
          {submitting ? "Saving..." : status ? "Save Changes" : "Save Draft"}
        </Button>
        {!status || status === "DRAFT" ? (
          <Button type="button" variant="outline" size="sm" onClick={handleContinueToPreview} disabled={submitting}>
            Preview &amp; Publish
          </Button>
        ) : status === "ACTIVE" ? (
          <Button type="button" variant="outline" size="sm" onClick={handleDeactivate} disabled={submitting}>
            Deactivate
          </Button>
        ) : (
          <Button type="button" variant="outline" size="sm" onClick={handleReactivate} disabled={submitting}>
            Reactivate
          </Button>
        )}
        <Button type="button" variant="ghost" size="sm" onClick={onCancel} disabled={submitting}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
