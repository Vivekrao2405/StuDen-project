import { ArrowLeft, ExternalLink, FolderKanban, Play, X } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Card, CardContent } from "@/components/ui/card";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { SkillChip } from "@/components/shared/SkillChip";
import { getPublicProject } from "@/lib/api/endpoints/publicProfile";
import type { ProjectMediaResponse } from "@/lib/api/types";
import { cloudinaryThumb } from "@/lib/cloudinary";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function resolveCover(media: ProjectMediaResponse[]): string | null {
  const explicit = media.find((m) => m.cover);
  if (explicit) return explicit.mediaType === "IMAGE" ? explicit.url : explicit.thumbnailUrl;
  const firstImage = media.find((m) => m.mediaType === "IMAGE");
  if (firstImage) return firstImage.url;
  const firstVideo = media.find((m) => m.mediaType === "VIDEO");
  return firstVideo?.thumbnailUrl ?? null;
}

export function PublicProjectDetailPage() {
  const { slug = "", projectId = "" } = useParams<{ slug: string; projectId: string }>();
  const { data, error, loading, refetch } = useAsync(() => getPublicProject(projectId), [projectId]);
  const [lightboxUrl, setLightboxUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!lightboxUrl) return;
    document.body.style.overflow = "hidden";
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setLightboxUrl(null);
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [lightboxUrl]);

  if (loading) {
    return <LoadingState label="Loading project..." />;
  }

  if (error || !data) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16">
        <ErrorState
          title="Project not found"
          message="This project doesn't exist or is no longer available."
          onRetry={refetch}
        />
      </div>
    );
  }

  const cover = resolveCover(data.media);

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6 lg:px-8">
      <Link
        to={ROUTES.publicProfile(slug)}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to profile
      </Link>

      <Card>
        <div className="relative aspect-[16/9] w-full overflow-hidden rounded-t-xl bg-muted">
          {cover ? (
            <img src={cloudinaryThumb(cover, 900) ?? cover} alt="" className="h-full w-full object-cover" />
          ) : (
            <div className="flex h-full w-full items-center justify-center">
              <FolderKanban className="size-10 text-muted-foreground" />
            </div>
          )}
        </div>
        <CardContent className="space-y-4 pt-4">
          <div>
            <h1 className="text-xl font-bold text-foreground">{data.title}</h1>
            {data.shortDescription ? <p className="text-sm text-muted-foreground">{data.shortDescription}</p> : null}
          </div>

          <Link
            to={ROUTES.publicProfile(data.studentSlug)}
            className="inline-flex items-center gap-2 rounded-lg border border-border px-2.5 py-1.5 hover:bg-muted/50"
          >
            <Avatar className="size-6">
              {data.studentProfileImageUrl ? (
                <AvatarImage src={data.studentProfileImageUrl} alt={data.studentName} />
              ) : null}
              <AvatarFallback className="text-[10px]">{getInitials(data.studentName)}</AvatarFallback>
            </Avatar>
            <span className="text-sm font-medium text-foreground">{data.studentName}</span>
          </Link>

          {data.skills.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {data.skills.map((skill) => (
                <SkillChip key={skill.id} skill={skill} variant="compact" />
              ))}
            </div>
          ) : null}

          {data.description ? (
            <p className="whitespace-pre-wrap text-sm text-foreground">{data.description}</p>
          ) : null}

          {data.links.length > 0 ? (
            <div className="flex flex-wrap gap-2 pt-1">
              {data.links.map((link) => (
                <a
                  key={link.url}
                  href={link.url}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted/50"
                >
                  {link.label} <ExternalLink className="size-3.5" />
                </a>
              ))}
            </div>
          ) : null}
        </CardContent>
      </Card>

      {data.media.length > 0 ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {data.media.map((item) =>
            item.mediaType === "IMAGE" ? (
              <button
                key={item.id}
                type="button"
                onClick={() => setLightboxUrl(item.url)}
                className="aspect-square overflow-hidden rounded-lg border border-border bg-muted"
              >
                <img
                  src={cloudinaryThumb(item.url, 300) ?? item.url}
                  alt=""
                  loading="lazy"
                  className="h-full w-full object-cover"
                />
              </button>
            ) : (
              <div key={item.id} className="relative aspect-square overflow-hidden rounded-lg border border-border bg-black">
                <video
                  controls
                  poster={cloudinaryThumb(item.thumbnailUrl, 300) ?? item.thumbnailUrl ?? undefined}
                  preload="none"
                  className="h-full w-full object-contain"
                >
                  <source src={item.url} />
                </video>
                {!item.thumbnailUrl ? (
                  <Play className="pointer-events-none absolute inset-0 m-auto size-8 text-white/80" />
                ) : null}
              </div>
            )
          )}
        </div>
      ) : null}

      {lightboxUrl ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
          onClick={() => setLightboxUrl(null)}
        >
          <button
            type="button"
            onClick={() => setLightboxUrl(null)}
            aria-label="Close"
            className={cn("absolute top-4 right-4 rounded-full bg-black/60 p-2 text-white hover:bg-black/80")}
          >
            <X className="size-5" />
          </button>
          <img
            src={lightboxUrl}
            alt=""
            className="max-h-full max-w-full rounded-lg object-contain"
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      ) : null}
    </div>
  );
}
