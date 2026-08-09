import { LogOut } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { FormField } from "@/components/shared/FormField";
import { useAuth } from "@/features/auth/useAuth";
import { ApiError } from "@/lib/api/ApiError";
import { updateCurrentUser } from "@/lib/api/endpoints/users";
import { isBlank, isValidPhone, isValidUrl } from "@/lib/validation";

interface FormErrors {
  fullName?: string;
  phone?: string;
  profileImageUrl?: string;
}

export function SettingsPage() {
  const { user, setUser, logout } = useAuth();

  const [fullName, setFullName] = useState(user?.fullName ?? "");
  const [phone, setPhone] = useState(user?.phone ?? "");
  const [profileImageUrl, setProfileImageUrl] = useState(user?.profileImageUrl ?? "");

  const [errors, setErrors] = useState<FormErrors>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!user) return null;

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (isBlank(fullName)) next.fullName = "Full name is required.";
    else if (fullName.length > 255) next.fullName = "Full name must be 255 characters or fewer.";

    if (phone && !isValidPhone(phone)) next.phone = "Enter a valid phone number.";
    if (profileImageUrl && !isValidUrl(profileImageUrl)) next.profileImageUrl = "Enter a valid URL (https://...).";

    return next;
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setApiError(null);
    const validationErrors = validate();
    setErrors(validationErrors);
    if (Object.keys(validationErrors).length > 0) return;

    setSubmitting(true);
    try {
      const updated = await updateCurrentUser({
        fullName: fullName.trim(),
        phone: phone.trim(),
        profileImageUrl: profileImageUrl.trim(),
      });
      setUser(updated);
    } catch (err) {
      setApiError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  function handleLogout() {
    // /settings is always inside ProtectedRoute, which redirects to /login itself
    // once status flips to guest — no explicit navigation needed here.
    logout();
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Settings</h1>
        <p className="text-sm text-muted-foreground">Manage your account details.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
          <CardDescription>Update your name, phone and profile photo URL.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            {apiError ? <p className="text-sm text-destructive">{apiError}</p> : null}

            <FormField label="Full name" htmlFor="fullName" error={errors.fullName}>
              <Input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} className="h-10" />
            </FormField>

            <FormField label="Phone" htmlFor="phone" error={errors.phone}>
              <Input
                id="phone"
                value={phone ?? ""}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="e.g. +91 98765 43210"
                className="h-10"
              />
            </FormField>

            <FormField label="Profile photo URL" htmlFor="profileImageUrl" error={errors.profileImageUrl}>
              <Input
                id="profileImageUrl"
                value={profileImageUrl ?? ""}
                onChange={(e) => setProfileImageUrl(e.target.value)}
                placeholder="https://..."
                className="h-10"
              />
            </FormField>

            <Separator />

            <div className="grid gap-3 text-sm sm:grid-cols-2">
              <div>
                <p className="text-muted-foreground">Email</p>
                <p className="font-medium text-foreground">{user.email}</p>
              </div>
              <div>
                <p className="text-muted-foreground">University</p>
                <p className="font-medium text-foreground">{user.university ?? "Not set"}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Role</p>
                <Badge variant="outline" className="mt-0.5">
                  {user.role}
                </Badge>
              </div>
            </div>

            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving..." : "Save Changes"}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Log out</CardTitle>
          <CardDescription>Sign out of your StuDen account on this device.</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="destructive" onClick={handleLogout}>
            <LogOut /> Log out
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
