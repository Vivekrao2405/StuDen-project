import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
import { FormField } from "@/components/shared/FormField";
import { ProfileImageUpload } from "@/components/shared/ProfileImageUpload";
import { useAuth } from "@/features/auth/useAuth";
import { ApiError } from "@/lib/api/ApiError";
import { createPortfolio, removeCoverImage, updatePortfolio, uploadCoverImage } from "@/lib/api/endpoints/portfolio";
import type { PortfolioRequest, PortfolioResponse } from "@/lib/api/types";
import { isBlank } from "@/lib/validation";
import { CoverImageUpload, type CoverImageValue } from "@/pages/portfolio/CoverImageUpload";

interface PortfolioFormProps {
  mode: "create" | "edit";
  initial?: PortfolioResponse;
  onSaved: (portfolio: PortfolioResponse) => void;
  onCancel: () => void;
}

interface FormErrors {
  headline?: string;
}

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function PortfolioForm({ mode, initial, onSaved, onCancel }: PortfolioFormProps) {
  const { user, setUser } = useAuth();
  const [headline, setHeadline] = useState(initial?.headline ?? "");
  const [bio, setBio] = useState(initial?.bio ?? "");
  const [experienceSummary, setExperienceSummary] = useState(initial?.experienceSummary ?? "");
  const [responseTime, setResponseTime] = useState(initial?.responseTime ?? "");
  const [location, setLocation] = useState(initial?.location ?? "");
  const [available, setAvailable] = useState(initial?.available ?? true);
  const [coverImage, setCoverImage] = useState<CoverImageValue>({ file: null, removed: false });

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(headline)) next.headline = "Tell us what you do.";
    else if (headline.length > 255) next.headline = "This must be 255 characters or fewer.";

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
      responseTime: responseTime.trim() || undefined,
      location: location.trim() || undefined,
      available,
    };

    setSubmitting(true);
    try {
      let result = mode === "create" ? await createPortfolio(payload) : await updatePortfolio(payload);

      // The cover image lives behind its own dedicated endpoints (it needs an existing
      // portfolio to attach to), so it's applied as a second step right after the
      // portfolio itself is created/updated — still one combined "Save" action to the user.
      if (coverImage.file) {
        result = await uploadCoverImage(coverImage.file);
      } else if (coverImage.removed && initial?.coverImageUrl) {
        result = await removeCoverImage();
      }

      onSaved(result);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleProfileImageChange(profileImageUrl: string | null) {
    if (!user) return;
    setUser({ ...user, profileImageUrl });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>{mode === "create" ? "Create your portfolio" : "Edit your portfolio"}</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

          {user ? (
            <FormField label="Profile photo" htmlFor="profileImage">
              <ProfileImageUpload
                currentUrl={user.profileImageUrl}
                fallbackText={getInitials(user.fullName)}
                onChange={handleProfileImageChange}
              />
            </FormField>
          ) : null}

          <FormField
            label="What do you do?"
            htmlFor="headline"
            error={errors.headline}
            hint='e.g. "Web Development", "Graphic Design", "Data Analytics"'
          >
            <Input
              id="headline"
              placeholder="e.g. Web Development"
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

          <FormField label="Typical response time" htmlFor="responseTime">
            <Input
              id="responseTime"
              placeholder="e.g. Within a day"
              value={responseTime}
              onChange={(e) => setResponseTime(e.target.value)}
              className="h-10"
            />
          </FormField>

          <FormField label="Location" htmlFor="location">
            <Input
              id="location"
              placeholder="e.g. Hyderabad, India"
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              className="h-10"
            />
          </FormField>

          <FormField label="Cover image" htmlFor="coverImage">
            <CoverImageUpload
              currentUrl={initial?.coverImageUrl ?? null}
              value={coverImage}
              onChange={setCoverImage}
              disabled={submitting}
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
