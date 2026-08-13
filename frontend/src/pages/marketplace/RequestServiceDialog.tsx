import { Plus, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

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
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import { createServiceRequest, listMyServiceRequests } from "@/lib/api/endpoints/serviceRequests";
import type { ServiceRequestRecord } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";
import { isValidUrl } from "@/lib/validation";

const DESCRIPTION_MIN = 20;
const DESCRIPTION_MAX = 2000;
const MAX_LINKS = 5;

interface LinkRow {
  key: string;
  label: string;
  url: string;
}

interface FormErrors {
  description?: string;
  budget?: string;
  links?: string;
}

interface RequestServiceDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  serviceId: string;
  serviceTitle: string;
  providerName: string;
  onSuccess: (request: ServiceRequestRecord) => void;
}

function todayIsoDate() {
  return new Date().toISOString().slice(0, 10);
}

export function RequestServiceDialog({
  open,
  onOpenChange,
  serviceId,
  serviceTitle,
  providerName,
  onSuccess,
}: RequestServiceDialogProps) {
  const [description, setDescription] = useState("");
  const [requestedDeliveryDate, setRequestedDeliveryDate] = useState("");
  const [budget, setBudget] = useState("");
  const [links, setLinks] = useState<LinkRow[]>([]);
  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [duplicateRequestId, setDuplicateRequestId] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function resetAndClose() {
    setDescription("");
    setRequestedDeliveryDate("");
    setBudget("");
    setLinks([]);
    setErrors({});
    setApiError(null);
    setDuplicateRequestId(null);
    onOpenChange(false);
  }

  function validate(): FormErrors {
    const next: FormErrors = {};
    const trimmed = description.trim();
    if (trimmed.length < DESCRIPTION_MIN || trimmed.length > DESCRIPTION_MAX) {
      next.description = `Please write between ${DESCRIPTION_MIN} and ${DESCRIPTION_MAX} characters.`;
    }
    if (budget.trim() && (!Number.isFinite(Number(budget)) || Number(budget) <= 0)) {
      next.budget = "Enter a positive amount.";
    }
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

  async function handleSubmit() {
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setApiError(null);
    setDuplicateRequestId(null);
    setSubmitting(true);
    try {
      const request = await createServiceRequest({
        serviceId,
        description: description.trim(),
        requestedDeliveryDate: requestedDeliveryDate || undefined,
        proposedBudget: budget.trim() ? Number(budget) : undefined,
        links: links
          .map((l) => ({ label: l.label.trim(), url: l.url.trim() }))
          .filter((l) => l.label && l.url),
      });
      onSuccess(request);
      resetAndClose();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setApiError(err.message);
        try {
          const mine = await listMyServiceRequests();
          const existing = mine.find((r) => r.serviceId === serviceId && r.status === "PENDING");
          setDuplicateRequestId(existing?.id ?? null);
        } catch {
          // Best-effort only — the inline message alone is still useful without the deep link.
        }
      } else {
        setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
      }
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

  return (
    <Dialog open={open} onOpenChange={(next) => (next ? onOpenChange(next) : resetAndClose())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Request {serviceTitle}</DialogTitle>
          <DialogDescription>
            Provider: <span className="font-medium text-foreground">{providerName}</span>
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {apiError ? (
            <p className="text-sm text-destructive">
              {apiError}
              {duplicateRequestId ? (
                <>
                  {" "}
                  <Link
                    to={ROUTES.serviceRequestDetail(duplicateRequestId)}
                    className="font-medium underline underline-offset-2"
                  >
                    View Request
                  </Link>
                </>
              ) : null}
            </p>
          ) : null}

          <FormField label="What do you need?" htmlFor="request-description" error={errors.description}>
            <Textarea
              id="request-description"
              placeholder="Describe what you'd like the student to build, your requirements, expected output, and any important details..."
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={5}
              disabled={submitting}
            />
          </FormField>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <FormField label="Expected delivery" htmlFor="request-delivery" hint="Optional">
              <Input
                id="request-delivery"
                type="date"
                min={todayIsoDate()}
                value={requestedDeliveryDate}
                onChange={(e) => setRequestedDeliveryDate(e.target.value)}
                className="h-10"
                disabled={submitting}
              />
            </FormField>

            <FormField label="Budget (₹)" htmlFor="request-budget" error={errors.budget} hint={errors.budget ? undefined : "Optional"}>
              <Input
                id="request-budget"
                type="number"
                min={1}
                placeholder="e.g. 2500"
                value={budget}
                onChange={(e) => setBudget(e.target.value)}
                className="h-10"
                disabled={submitting}
              />
            </FormField>
          </div>

          <FormField label="Reference links" htmlFor="request-links" error={errors.links} hint="Optional — GitHub, Figma, Drive, etc.">
            <div className="space-y-2">
              {links.map((row) => (
                <div key={row.key} className="flex flex-col gap-1.5 sm:flex-row sm:gap-2">
                  <Input
                    placeholder="Label"
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
              {links.length < MAX_LINKS ? (
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setLinks((prev) => [...prev, { key: crypto.randomUUID(), label: "", url: "" }])}
                  disabled={submitting}
                >
                  <Plus /> Add link
                </Button>
              ) : null}
            </div>
          </FormField>
        </div>

        <DialogFooter>
          <Button type="button" variant="outline" onClick={resetAndClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="button" onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Sending..." : "Send Request"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
