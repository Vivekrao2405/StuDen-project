import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { ApiError } from "@/lib/api/ApiError";
import { createPortfolio, updatePortfolio } from "@/lib/api/endpoints/portfolio";
import type { PortfolioRequest, PortfolioResponse } from "@/lib/api/types";
import { isBlank, isValidUrl } from "@/lib/validation";

interface PortfolioFormProps {
  mode: "create" | "edit";
  initial?: PortfolioResponse;
  onSaved: (portfolio: PortfolioResponse) => void;
  onCancel: () => void;
}

interface FormErrors {
  headline?: string;
  hourlyRate?: string;
  coverImageUrl?: string;
}

export function PortfolioForm({ mode, initial, onSaved, onCancel }: PortfolioFormProps) {
  const [headline, setHeadline] = useState(initial?.headline ?? "");
  const [bio, setBio] = useState(initial?.bio ?? "");
  const [experienceSummary, setExperienceSummary] = useState(initial?.experienceSummary ?? "");
  const [hourlyRate, setHourlyRate] = useState(initial?.hourlyRate != null ? String(initial.hourlyRate) : "");
  const [responseTime, setResponseTime] = useState(initial?.responseTime ?? "");
  const [location, setLocation] = useState(initial?.location ?? "");
  const [available, setAvailable] = useState(initial?.available ?? true);
  const [coverImageUrl, setCoverImageUrl] = useState(initial?.coverImageUrl ?? "");

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(headline)) next.headline = "Headline is required.";
    else if (headline.length > 255) next.headline = "Headline must be 255 characters or fewer.";

    if (hourlyRate && Number.isNaN(Number(hourlyRate))) next.hourlyRate = "Enter a valid number.";
    else if (hourlyRate && Number(hourlyRate) < 0) next.hourlyRate = "Hourly rate can't be negative.";

    if (coverImageUrl && !isValidUrl(coverImageUrl)) next.coverImageUrl = "Enter a valid URL (https://...).";

    return next;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setApiError(null);
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    const payload: PortfolioRequest = {
      headline: headline.trim(),
      bio: bio.trim() || undefined,
      experienceSummary: experienceSummary.trim() || undefined,
      hourlyRate: hourlyRate ? Number(hourlyRate) : null,
      responseTime: responseTime.trim() || undefined,
      location: location.trim() || undefined,
      available,
      coverImageUrl: coverImageUrl.trim() || undefined,
    };

    setSubmitting(true);
    try {
      const result = mode === "create" ? await createPortfolio(payload) : await updatePortfolio(payload);
      onSaved(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{mode === "create" ? "Create your portfolio" : "Edit your portfolio"}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

          <FormField label="Headline" htmlFor="headline" error={errors.headline}>
            <Input
              id="headline"
              placeholder="e.g. Full Stack Developer"
              value={headline}
              onChange={(e) => setHeadline(e.target.value)}
              className="h-10"
            />
          </FormField>

          <FormField label="Bio" htmlFor="bio">
            <Textarea
              id="bio"
              placeholder="Tell people about yourself..."
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              rows={4}
            />
          </FormField>

          <FormField label="Experience summary" htmlFor="experienceSummary">
            <Textarea
              id="experienceSummary"
              placeholder="Summarize your relevant experience..."
              value={experienceSummary}
              onChange={(e) => setExperienceSummary(e.target.value)}
              rows={3}
            />
          </FormField>

          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Hourly rate" htmlFor="hourlyRate" error={errors.hourlyRate}>
              <Input
                id="hourlyRate"
                type="number"
                min={0}
                step="0.01"
                placeholder="e.g. 500"
                value={hourlyRate}
                onChange={(e) => setHourlyRate(e.target.value)}
                className="h-10"
              />
            </FormField>

            <FormField label="Typical response time" htmlFor="responseTime">
              <Input
                id="responseTime"
                placeholder="e.g. Within a day"
                value={responseTime}
                onChange={(e) => setResponseTime(e.target.value)}
                className="h-10"
              />
            </FormField>
          </div>

          <FormField label="Location" htmlFor="location">
            <Input
              id="location"
              placeholder="e.g. Hyderabad, India"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              className="h-10"
            />
          </FormField>

          <FormField label="Cover image URL" htmlFor="coverImageUrl" error={errors.coverImageUrl}>
            <Input
              id="coverImageUrl"
              placeholder="https://..."
              value={coverImageUrl}
              onChange={(e) => setCoverImageUrl(e.target.value)}
              className="h-10"
            />
          </FormField>

          <div className="flex items-center justify-between rounded-lg border border-border px-4 py-3">
            <div>
              <p className="text-sm font-medium text-foreground">Available for work</p>
              <p className="text-xs text-muted-foreground">Shown on your public profile.</p>
            </div>
            <Switch checked={available} onCheckedChange={setAvailable} />
          </div>

          <div className="flex gap-2 pt-2">
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving..." : mode === "create" ? "Create Portfolio" : "Save Changes"}
            </Button>
            <Button type="button" variant="outline" onClick={onCancel} disabled={submitting}>
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  );
}
