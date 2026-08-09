import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import { createEducation, updateEducation } from "@/lib/api/endpoints/education";
import type { EducationRequest, EducationResponse } from "@/lib/api/types";
import { isBlank } from "@/lib/validation";

interface EducationFormProps {
  initial?: EducationResponse;
  onSaved: (education: EducationResponse) => void;
  onCancel: () => void;
}

interface FormErrors {
  degree?: string;
  institution?: string;
  startYear?: string;
  endYear?: string;
}

const CURRENT_YEAR = new Date().getFullYear();

export function EducationForm({ initial, onSaved, onCancel }: EducationFormProps) {
  const [degree, setDegree] = useState(initial?.degree ?? "");
  const [fieldOfStudy, setFieldOfStudy] = useState(initial?.fieldOfStudy ?? "");
  const [institution, setInstitution] = useState(initial?.institution ?? "");
  const [startYear, setStartYear] = useState(initial ? String(initial.startYear) : "");
  const [endYear, setEndYear] = useState(initial?.endYear != null ? String(initial.endYear) : "");
  const [current, setCurrent] = useState(initial?.current ?? false);

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(degree)) next.degree = "Degree is required.";
    if (isBlank(institution)) next.institution = "Institution is required.";

    const startYearNum = Number(startYear);
    if (isBlank(startYear) || Number.isNaN(startYearNum)) next.startYear = "Enter a valid start year.";
    else if (startYearNum < 1950 || startYearNum > 2100) next.startYear = "Start year must be between 1950 and 2100.";

    if (endYear) {
      const endYearNum = Number(endYear);
      if (Number.isNaN(endYearNum)) next.endYear = "Enter a valid end year.";
      else if (endYearNum < 1950 || endYearNum > 2100) next.endYear = "End year must be between 1950 and 2100.";
      else if (!Number.isNaN(startYearNum) && endYearNum < startYearNum)
        next.endYear = "End year can't be before the start year.";
    }

    return next;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setApiError(null);
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    const payload: EducationRequest = {
      degree: degree.trim(),
      fieldOfStudy: fieldOfStudy.trim() || undefined,
      institution: institution.trim(),
      startYear: Number(startYear),
      endYear: current ? null : endYear ? Number(endYear) : null,
      current,
    };

    setSubmitting(true);
    try {
      const result = initial ? await updateEducation(initial.id, payload) : await createEducation(payload);
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

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Degree" htmlFor="degree" error={errors.degree}>
          <Input
            id="degree"
            placeholder="e.g. B.Tech"
            value={degree}
            onChange={(e) => setDegree(e.target.value)}
            className="h-10"
          />
        </FormField>
        <FormField label="Field of study" htmlFor="fieldOfStudy">
          <Input
            id="fieldOfStudy"
            placeholder="e.g. Computer Science"
            value={fieldOfStudy}
            onChange={(e) => setFieldOfStudy(e.target.value)}
            className="h-10"
          />
        </FormField>
      </div>

      <FormField label="Institution" htmlFor="institution" error={errors.institution}>
        <Input
          id="institution"
          placeholder="e.g. IIT Hyderabad"
          value={institution}
          onChange={(e) => setInstitution(e.target.value)}
          className="h-10"
        />
      </FormField>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Start year" htmlFor="startYear" error={errors.startYear}>
          <Input
            id="startYear"
            type="number"
            placeholder={String(CURRENT_YEAR)}
            value={startYear}
            onChange={(e) => setStartYear(e.target.value)}
            className="h-10"
          />
        </FormField>
        <FormField label="End year" htmlFor="endYear" error={errors.endYear}>
          <Input
            id="endYear"
            type="number"
            placeholder={String(CURRENT_YEAR)}
            value={endYear}
            onChange={(e) => setEndYear(e.target.value)}
            disabled={current}
            className="h-10"
          />
        </FormField>
      </div>

      <div className="flex items-center gap-2.5">
        <Switch
          id="current"
          checked={current}
          onCheckedChange={(checked) => {
            setCurrent(checked);
            if (checked) setEndYear("");
          }}
        />
        <label htmlFor="current" className="text-sm font-medium text-foreground">
          I'm currently studying here
        </label>
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
