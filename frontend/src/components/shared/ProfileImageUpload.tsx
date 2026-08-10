import { Upload, X } from "lucide-react";
import { useRef, useState } from "react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/ApiError";
import { removeProfileImage, uploadProfileImage } from "@/lib/api/endpoints/users";
import { cn } from "@/lib/utils";

const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_SIZE_BYTES = 5 * 1024 * 1024; // mirrors the backend's default MAX_IMAGE_SIZE_MB

interface ProfileImageUploadProps {
  currentUrl: string | null;
  fallbackText: string;
  onChange: (url: string | null) => void;
  className?: string;
}

export function ProfileImageUpload({ currentUrl, fallbackText, onChange, className }: ProfileImageUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>();

  async function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    if (!ACCEPTED_TYPES.includes(file.type)) {
      setError("Only JPEG, PNG, and WEBP images are supported.");
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      setError("Image must be 5MB or smaller.");
      return;
    }

    setError(undefined);
    setBusy(true);
    try {
      const updated = await uploadProfileImage(file);
      onChange(updated.profileImageUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't upload the photo. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove() {
    setError(undefined);
    setBusy(true);
    try {
      const updated = await removeProfileImage();
      onChange(updated.profileImageUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't remove the photo. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={cn("space-y-2", className)}>
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={handleFileSelect}
        disabled={busy}
      />
      <div className="flex items-center gap-4">
        <Avatar className="size-16 border border-border">
          {currentUrl ? <AvatarImage src={currentUrl} alt="Profile photo" /> : null}
          <AvatarFallback className="text-base">{fallbackText}</AvatarFallback>
        </Avatar>
        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => inputRef.current?.click()}
            disabled={busy}
          >
            <Upload /> {currentUrl ? "Change Photo" : "Upload Profile Photo"}
          </Button>
          {currentUrl ? (
            <Button type="button" variant="ghost" size="sm" onClick={handleRemove} disabled={busy}>
              <X /> Remove
            </Button>
          ) : null}
        </div>
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
    </div>
  );
}
