import { ChevronLeft, ChevronRight, Star, UploadCloud, Video, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import type { ProjectMediaResponse, ProjectMediaType } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const ACCEPTED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const ACCEPTED_VIDEO_TYPES = ["video/mp4", "video/webm"];
const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024; // mirrors the backend's default MAX_IMAGE_SIZE_MB
const MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024; // mirrors the backend's default MAX_VIDEO_SIZE_MB

export interface PendingMediaItem {
  key: string;
  kind: "existing" | "new";
  existingId?: string;
  file?: File;
  previewUrl: string | null;
  mediaType: ProjectMediaType;
  isCover: boolean;
}

export function mediaResponseToPendingItems(media: ProjectMediaResponse[]): PendingMediaItem[] {
  return media.map((m) => ({
    key: m.id,
    kind: "existing",
    existingId: m.id,
    previewUrl: m.mediaType === "IMAGE" ? m.url : m.thumbnailUrl,
    mediaType: m.mediaType,
    isCover: m.cover,
  }));
}

interface ProjectMediaUploadProps {
  items: PendingMediaItem[];
  onChange: (items: PendingMediaItem[]) => void;
  disabled?: boolean;
  error?: string;
}

export function ProjectMediaUpload({ items, onChange, disabled, error }: ProjectMediaUploadProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const createdUrls = useRef<string[]>([]);
  const [localError, setLocalError] = useState<string | undefined>();
  const [dragOver, setDragOver] = useState(false);

  useEffect(() => {
    return () => {
      createdUrls.current.forEach((url) => URL.revokeObjectURL(url));
    };
  }, []);

  function validateFile(file: File): string | null {
    const isImage = ACCEPTED_IMAGE_TYPES.includes(file.type);
    const isVideo = ACCEPTED_VIDEO_TYPES.includes(file.type);
    if (!isImage && !isVideo) return `"${file.name}" isn't a supported image or video format.`;
    if (isImage && file.size > MAX_IMAGE_SIZE_BYTES) return `"${file.name}" is larger than 5MB.`;
    if (isVideo && file.size > MAX_VIDEO_SIZE_BYTES) return `"${file.name}" is larger than 50MB.`;
    return null;
  }

  function addFiles(fileList: FileList | File[]) {
    const files = Array.from(fileList);
    const errors: string[] = [];
    const newItems: PendingMediaItem[] = [];

    for (const file of files) {
      const err = validateFile(file);
      if (err) {
        errors.push(err);
        continue;
      }
      const isImage = ACCEPTED_IMAGE_TYPES.includes(file.type);
      const previewUrl = isImage ? URL.createObjectURL(file) : null;
      if (previewUrl) createdUrls.current.push(previewUrl);
      newItems.push({
        key: crypto.randomUUID(),
        kind: "new",
        file,
        previewUrl,
        mediaType: isImage ? "IMAGE" : "VIDEO",
        isCover: false,
      });
    }

    setLocalError(errors.length > 0 ? errors.join(" ") : undefined);
    if (newItems.length === 0) return;

    const combined = [...items, ...newItems];
    if (!combined.some((i) => i.isCover)) {
      combined[0] = { ...combined[0], isCover: true };
    }
    onChange(combined);
  }

  function handleFileInput(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files ? Array.from(e.target.files) : [];
    e.target.value = "";
    if (files.length > 0) addFiles(files);
  }

  function handleDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault();
    setDragOver(false);
    if (disabled) return;
    if (e.dataTransfer.files.length > 0) addFiles(e.dataTransfer.files);
  }

  function remove(key: string) {
    const target = items.find((i) => i.key === key);
    let next = items.filter((i) => i.key !== key);
    if (target?.isCover && next.length > 0) {
      next = next.map((item, idx) => (idx === 0 ? { ...item, isCover: true } : item));
    }
    onChange(next);
  }

  function setCover(key: string) {
    onChange(items.map((item) => ({ ...item, isCover: item.key === key })));
  }

  function move(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  const displayedError = localError ?? error;

  return (
    <div className="space-y-3">
      <input
        ref={inputRef}
        type="file"
        multiple
        accept="image/jpeg,image/png,image/webp,video/mp4,video/webm"
        className="hidden"
        onChange={handleFileInput}
        disabled={disabled}
      />

      <div
        onClick={() => !disabled && inputRef.current?.click()}
        onDragOver={(e) => {
          e.preventDefault();
          if (!disabled) setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        role="button"
        tabIndex={0}
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center gap-1.5 rounded-lg border-2 border-dashed p-6 text-center transition-colors",
          dragOver ? "border-primary bg-accent" : "border-border hover:border-primary/50 hover:bg-muted/40",
          disabled && "pointer-events-none opacity-50"
        )}
      >
        <UploadCloud className="size-6 text-muted-foreground" />
        <p className="text-sm font-medium text-foreground">Drag &amp; drop images or videos, or click to choose files</p>
        <p className="text-xs text-muted-foreground">JPG, PNG, WEBP (up to 5MB) &middot; MP4, WEBM (up to 50MB)</p>
      </div>

      {displayedError ? <p className="text-sm text-destructive">{displayedError}</p> : null}

      {items.length > 0 ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {items.map((item, index) => (
            <div
              key={item.key}
              className="group relative aspect-square overflow-hidden rounded-lg border border-border bg-muted"
            >
              {item.previewUrl ? (
                <img src={item.previewUrl} alt="" className="h-full w-full object-cover" />
              ) : (
                <div className="flex h-full w-full flex-col items-center justify-center gap-1 px-2 text-muted-foreground">
                  <Video className="size-6" />
                  <span className="line-clamp-2 text-center text-[11px] leading-tight break-all">
                    {item.file?.name ?? "Video"}
                  </span>
                </div>
              )}

              {item.mediaType === "VIDEO" ? (
                <span className="absolute left-1.5 top-1.5 rounded bg-black/60 px-1.5 py-0.5 text-[10px] font-medium text-white">
                  Video
                </span>
              ) : null}

              <button
                type="button"
                onClick={() => remove(item.key)}
                disabled={disabled}
                aria-label="Remove media"
                className="absolute right-1.5 top-1.5 rounded-full bg-black/60 p-1 text-white transition-colors hover:bg-black/80 disabled:opacity-40"
              >
                <X className="size-3.5" />
              </button>

              <button
                type="button"
                onClick={() => setCover(item.key)}
                disabled={disabled}
                aria-label={item.isCover ? "Cover media" : "Set as cover"}
                className={cn(
                  "absolute bottom-1.5 left-1.5 flex items-center gap-1 rounded-full px-2 py-1 text-[10px] font-medium transition-colors disabled:opacity-40",
                  item.isCover ? "bg-primary text-primary-foreground" : "bg-black/60 text-white hover:bg-black/80"
                )}
              >
                <Star className={cn("size-3", item.isCover && "fill-current")} />
                {item.isCover ? "Cover" : "Set cover"}
              </button>

              <div className="absolute bottom-1.5 right-1.5 flex gap-1">
                <button
                  type="button"
                  onClick={() => move(index, -1)}
                  disabled={disabled || index === 0}
                  aria-label="Move earlier"
                  className="rounded-full bg-black/60 p-1 text-white transition-colors hover:bg-black/80 disabled:opacity-30"
                >
                  <ChevronLeft className="size-3.5" />
                </button>
                <button
                  type="button"
                  onClick={() => move(index, 1)}
                  disabled={disabled || index === items.length - 1}
                  aria-label="Move later"
                  className="rounded-full bg-black/60 p-1 text-white transition-colors hover:bg-black/80 disabled:opacity-30"
                >
                  <ChevronRight className="size-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}
    </div>
  );
}
