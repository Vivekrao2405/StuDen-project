import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import { createCertificate, updateCertificate } from "@/lib/api/endpoints/certificates";
import type { CertificateRequest, CertificateResponse } from "@/lib/api/types";
import { isBlank, isValidUrl } from "@/lib/validation";

interface CertificateFormProps {
  initial?: CertificateResponse;
  onSaved: (certificate: CertificateResponse) => void;
  onCancel: () => void;
}

interface FormErrors {
  title?: string;
  certificateUrl?: string;
}

export function CertificateForm({ initial, onSaved, onCancel }: CertificateFormProps) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [issuedBy, setIssuedBy] = useState(initial?.issuedBy ?? "");
  const [issueDate, setIssueDate] = useState(initial?.issueDate ?? "");
  const [certificateUrl, setCertificateUrl] = useState(initial?.certificateUrl ?? "");

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(title)) next.title = "Title is required.";
    if (certificateUrl && !isValidUrl(certificateUrl)) next.certificateUrl = "Enter a valid URL (https://...).";
    return next;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setApiError(null);
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    const payload: CertificateRequest = {
      title: title.trim(),
      issuedBy: issuedBy.trim() || undefined,
      issueDate: issueDate || null,
      certificateUrl: certificateUrl.trim() || undefined,
    };

    setSubmitting(true);
    try {
      const result = initial ? await updateCertificate(initial.id, payload) : await createCertificate(payload);
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
          placeholder="e.g. AWS Certified Developer"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="h-10"
        />
      </FormField>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Issued by" htmlFor="issuedBy">
          <Input
            id="issuedBy"
            placeholder="e.g. Amazon Web Services"
            value={issuedBy}
            onChange={(e) => setIssuedBy(e.target.value)}
            className="h-10"
          />
        </FormField>
        <FormField label="Issue date" htmlFor="issueDate">
          <Input
            id="issueDate"
            type="date"
            max={new Date().toISOString().slice(0, 10)}
            value={issueDate ?? ""}
            onChange={(e) => setIssueDate(e.target.value)}
            className="h-10"
          />
        </FormField>
      </div>

      <FormField label="Certificate URL" htmlFor="certificateUrl" error={errors.certificateUrl}>
        <Input
          id="certificateUrl"
          placeholder="https://..."
          value={certificateUrl ?? ""}
          onChange={(e) => setCertificateUrl(e.target.value)}
          className="h-10"
        />
      </FormField>

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
