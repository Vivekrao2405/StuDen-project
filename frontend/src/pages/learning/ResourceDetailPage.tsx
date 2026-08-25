import { ArrowLeft, Check, Clock, Download } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  completeResource,
  downloadResource,
  getResource,
  startResource,
  viewResource,
} from "@/lib/api/endpoints/resources";
import { formatShortDate } from "@/lib/format";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { difficultyBadgeVariant } from "@/pages/practical/practicalDisplay";
import { progressStatusBadgeVariant, progressStatusLabel, RESOURCE_TYPE_LABEL } from "@/pages/learning/resourceDisplay";

const DIRECT_VIDEO_FILE = /\.(mp4|webm)(\?|$)/i;

// Fetches the file as a Blob and renders it via an object URL rather than pointing the iframe
// straight at the API: this app authenticates with a Bearer token (not a cookie), so a bare
// navigation to the file endpoint can't attach it, and Cloudinary's own raw-file URL serves no
// reliable Content-Type — either would force a download instead of an inline PDF view. View-only —
// downloading is a separate, explicit action rendered by the parent (see handleDownload below), so
// this component can never trigger one on its own.
function PdfViewer({ resourceId }: { resourceId: string }) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let createdUrl: string | null = null;
    setObjectUrl(null);
    setLoadError(false);

    viewResource(resourceId)
      .then((blob) => {
        if (cancelled) return;
        createdUrl = URL.createObjectURL(blob);
        setObjectUrl(createdUrl);
      })
      .catch(() => {
        if (!cancelled) setLoadError(true);
      });

    return () => {
      cancelled = true;
      if (createdUrl) URL.revokeObjectURL(createdUrl);
    };
  }, [resourceId]);

  if (loadError) {
    return <p className="text-sm text-destructive">Couldn't load this document. Please try again.</p>;
  }
  if (!objectUrl) {
    return (
      <div className="flex h-[70vh] w-full items-center justify-center rounded-lg border border-border text-sm text-muted-foreground">
        Loading document...
      </div>
    );
  }
  return <iframe src={objectUrl} title="Resource document" className="h-[70vh] w-full rounded-lg border border-border" />;
}

function ResourceBody({ resourceId, resourceType, hasFile, externalUrl, notesContent }: {
  resourceId: string;
  resourceType: string;
  hasFile: boolean;
  externalUrl: string | null;
  notesContent: string | null;
}) {
  if ((resourceType === "PDF" || resourceType === "DOCUMENT") && hasFile) {
    return <PdfViewer resourceId={resourceId} />;
  }

  if (resourceType === "VIDEO" && externalUrl) {
    if (DIRECT_VIDEO_FILE.test(externalUrl)) {
      return (
        <video controls className="w-full rounded-lg border border-border bg-black">
          <source src={externalUrl} />
        </video>
      );
    }
    return (
      <Button render={<a href={externalUrl} target="_blank" rel="noreferrer" />}>
        Watch
      </Button>
    );
  }

  if (resourceType === "EXTERNAL_LINK" && externalUrl) {
    return (
      <Button render={<a href={externalUrl} target="_blank" rel="noreferrer" />}>
        Open Resource
      </Button>
    );
  }

  if (resourceType === "NOTES" && notesContent) {
    return <QuestionContent text={notesContent} />;
  }

  return <p className="text-sm text-muted-foreground">This resource has no content yet.</p>;
}

export function ResourceDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const toast = useToast();
  const [updating, setUpdating] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const { data: resource, error, loading, refetch } = useAsync(() => getResource(id), [id]);

  async function handleStart() {
    setUpdating(true);
    try {
      await startResource(id);
      refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't update your progress. Please try again.");
    } finally {
      setUpdating(false);
    }
  }

  async function handleComplete() {
    setUpdating(true);
    try {
      await completeResource(id);
      refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't update your progress. Please try again.");
    } finally {
      setUpdating(false);
    }
  }

  // The only action that ever hits the attachment-disposition endpoint — every other action on
  // this page (view/start/complete) only ever reads the inline endpoint or writes progress state.
  async function handleDownload() {
    if (!resource) return;
    setDownloading(true);
    try {
      const blob = await downloadResource(resource.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      const safeTitle = resource.title.replace(/[^A-Za-z0-9 ._-]/g, "_").trim() || "resource";
      link.download = `${safeTitle}.${blob.type === "application/pdf" ? "pdf" : "docx"}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't download this file. Please try again.");
    } finally {
      setDownloading(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading resource..." />;
  }
  if (error || !resource) {
    return <ErrorState title="Resource not found" message="This resource isn't available." onRetry={refetch} />;
  }

  const lastActivityLabel = resource.progressStatus === "COMPLETED"
    ? formatShortDate(resource.completedAt)
    : formatShortDate(resource.startedAt);

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-6 sm:px-0">
      <Link
        to={ROUTES.myLearning}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to My Learning
      </Link>

      <Card>
        <CardContent className="space-y-6 pt-6">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline">{RESOURCE_TYPE_LABEL[resource.resourceType]}</Badge>
              {resource.difficulty ? <Badge variant={difficultyBadgeVariant(resource.difficulty)}>{resource.difficulty}</Badge> : null}
              <Badge variant={progressStatusBadgeVariant(resource.progressStatus)}>
                {progressStatusLabel(resource.progressStatus)}
              </Badge>
            </div>
            <h1 className="mt-2 text-xl font-bold text-foreground">{resource.title}</h1>
            <p className="text-sm text-muted-foreground">{resource.skillName}</p>
          </div>

          {resource.description ? <p className="text-sm text-muted-foreground">{resource.description}</p> : null}

          <div className="flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
            {resource.estimatedMinutes ? (
              <span className="inline-flex items-center gap-1.5">
                <Clock className="size-4" /> {resource.estimatedMinutes} minutes
              </span>
            ) : null}
          </div>

          {resource.tags.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {resource.tags.map((tag) => (
                <Badge key={tag} variant="outline">
                  {tag}
                </Badge>
              ))}
            </div>
          ) : null}

          <ResourceBody
            resourceId={resource.id}
            resourceType={resource.resourceType}
            hasFile={resource.fileUrl != null}
            externalUrl={resource.externalUrl}
            notesContent={resource.notesContent}
          />

          {lastActivityLabel ? (
            <p className="text-xs text-muted-foreground">
              {resource.progressStatus === "COMPLETED" ? `Completed ${lastActivityLabel}` : `Last read: ${lastActivityLabel}`}
            </p>
          ) : null}

          <div className="flex flex-col gap-2">
            {resource.progressStatus === "COMPLETED" ? (
              <div className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border bg-muted/30 px-4 py-2.5 text-sm font-medium text-foreground">
                <Check className="size-4 text-emerald-600 dark:text-emerald-400" /> Completed
              </div>
            ) : resource.progressStatus === "IN_PROGRESS" ? (
              <Button size="lg" onClick={handleComplete} disabled={updating}>
                {updating ? "Saving..." : "Mark as Completed"}
              </Button>
            ) : (
              <Button size="lg" onClick={handleStart} disabled={updating}>
                {updating ? "Saving..." : "Continue Learning"}
              </Button>
            )}

            {resource.fileUrl ? (
              <Button variant="outline" size="lg" onClick={handleDownload} disabled={downloading}>
                <Download className="size-4" /> {downloading ? "Downloading..." : "Download PDF"}
              </Button>
            ) : null}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
