import { Upload, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { CoverImage } from "@/components/shared/CoverImage";

const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_SIZE_BYTES = 5 * 1024 * 1024; // mirrors the backend's default MAX_IMAGE_SIZE_MB

export interface CoverImageValue {
  file: File | null;
  removed: boolean;
}

interface CoverImageUploadProps {
  currentUrl: string | null;
  value: CoverImageValue;
  onChange: (value: CoverImageValue) => void;
  disabled?: boolean;
  error?: string;
}

export function CoverImageUpload({ currentUrl, value, onChange, disabled, error }: CoverImageUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | undefined>();

  useEffect(() => {
    if (!value.file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(value.file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [value.file]);

  const displayUrl = previewUrl ?? (value.removed ? null : currentUrl);

  function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    if (!ACCEPTED_TYPES.includes(file.type)) {
      setLocalError("Only JPEG, PNG, and WEBP images are supported.");
      return;
    }
    if (file.size > MAX_SIZE_BYTES) {
      setLocalError("Image must be 5MB or smaller.");
      return;
    }
    setLocalError(undefined);
    onChange({ file, removed: false });
  }

  function handleRemove() {
    setLocalError(undefined);
    onChange({ file: null, removed: true });
  }

  const displayedError = localError ?? error;

  return (
    <div className="space-y-2">
      <input
        ref={inputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        className="hidden"
        onChange={handleFileSelect}
        disabled={disabled}
      />
      <CoverImage src={displayUrl} alt="Cover image preview" className="rounded-lg border border-border" />
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()} disabled={disabled}>
          <Upload /> {displayUrl ? "Change Image" : "Upload Cover Image"}
        </Button>
        {displayUrl ? (
          <Button type="button" variant="ghost" size="sm" onClick={handleRemove} disabled={disabled}>
            <X /> Remove Image
          </Button>
        ) : null}
        {value.file ? <span className="truncate text-xs text-muted-foreground">{value.file.name}</span> : null}
      </div>
      {displayedError ? <p className="text-sm text-destructive">{displayedError}</p> : null}
    </div>
  );
}
