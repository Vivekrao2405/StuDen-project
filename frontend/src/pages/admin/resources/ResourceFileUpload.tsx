import { FileText, Upload, X } from "lucide-react";
import { useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/ApiError";
import { deleteResourceFile, uploadResourceFile } from "@/lib/api/endpoints/adminResources";
import { cn } from "@/lib/utils";

const MAX_SIZE_BYTES = 10 * 1024 * 1024; // mirrors the backend's default MAX_DOCUMENT_SIZE_MB
const ACCEPT_BY_TYPE: Record<"PDF" | "DOCUMENT", string> = {
  PDF: ".pdf,application/pdf",
  DOCUMENT: ".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document",
};

interface ResourceFileUploadProps {
  resourceId: string;
  resourceType: "PDF" | "DOCUMENT";
  currentUrl: string | null;
  onChange: (fileUrl: string | null) => void;
  className?: string;
}

// Modeled on ProfileImageUpload — a single-file variant, but the upload only becomes available
// once the resource row already exists (needs an id for the publicId convention), same
// constraint PortfolioController.uploadCoverImage has against an existing portfolio.
export function ResourceFileUpload({ resourceId, resourceType, currentUrl, onChange, className }: ResourceFileUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | undefined>();

  async function handleFileSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    if (file.size > MAX_SIZE_BYTES) {
      setError("The file must be 10MB or smaller.");
      return;
    }

    setError(undefined);
    setBusy(true);
    try {
      const updated = await uploadResourceFile(resourceId, file);
      onChange(updated.fileUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't upload the file. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  async function handleRemove() {
    setError(undefined);
    setBusy(true);
    try {
      const updated = await deleteResourceFile(resourceId);
      onChange(updated.fileUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't remove the file. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={cn("space-y-2", className)}>
      <input ref={inputRef} type="file" accept={ACCEPT_BY_TYPE[resourceType]} className="hidden" onChange={handleFileSelect} disabled={busy} />
      <div className="flex flex-wrap items-center gap-2">
        {currentUrl ? (
          <span className="inline-flex items-center gap-1.5 text-sm text-foreground">
            <FileText className="size-4 text-muted-foreground" /> File uploaded
          </span>
        ) : null}
        <Button type="button" variant="outline" size="sm" onClick={() => inputRef.current?.click()} disabled={busy}>
          <Upload /> {currentUrl ? "Replace File" : "Upload File"}
        </Button>
        {currentUrl ? (
          <Button type="button" variant="ghost" size="sm" onClick={handleRemove} disabled={busy}>
            <X /> Remove
          </Button>
        ) : null}
      </div>
      {error ? <p className="text-sm text-destructive">{error}</p> : null}
    </div>
  );
}
