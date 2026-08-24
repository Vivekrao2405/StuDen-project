import { ArrowLeft, Check, Clock, Download } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { QuestionContent } from "@/components/shared/QuestionContent";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { completeResource, getResource, startResource } from "@/lib/api/endpoints/resources";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { difficultyBadgeVariant } from "@/pages/practical/practicalDisplay";
import { progressStatusBadgeVariant, progressStatusLabel, RESOURCE_TYPE_LABEL } from "@/pages/learning/resourceDisplay";

const DIRECT_VIDEO_FILE = /\.(mp4|webm)(\?|$)/i;

function ResourceBody({ resourceType, fileUrl, externalUrl, notesContent }: {
  resourceType: string;
  fileUrl: string | null;
  externalUrl: string | null;
  notesContent: string | null;
}) {
  if ((resourceType === "PDF" || resourceType === "DOCUMENT") && fileUrl) {
    return (
      <div className="space-y-3">
        <iframe src={fileUrl} title="Resource document" className="h-[70vh] w-full rounded-lg border border-border" />
        <Button variant="outline" render={<a href={fileUrl} download />}>
          <Download className="size-4" /> Download
        </Button>
      </div>
    );
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

  if (loading) {
    return <LoadingState label="Loading resource..." />;
  }
  if (error || !resource) {
    return <ErrorState title="Resource not found" message="This resource isn't available." onRetry={refetch} />;
  }

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
            resourceType={resource.resourceType}
            fileUrl={resource.fileUrl}
            externalUrl={resource.externalUrl}
            notesContent={resource.notesContent}
          />

          {resource.progressStatus === "COMPLETED" ? (
            <div className="flex w-full items-center justify-center gap-1.5 rounded-lg border border-border bg-muted/30 px-4 py-2.5 text-sm font-medium text-foreground">
              <Check className="size-4 text-emerald-600 dark:text-emerald-400" /> Completed
            </div>
          ) : resource.progressStatus === "IN_PROGRESS" ? (
            <Button className="w-full" size="lg" onClick={handleComplete} disabled={updating}>
              {updating ? "Saving..." : "Mark Complete"}
            </Button>
          ) : (
            <Button className="w-full" size="lg" onClick={handleStart} disabled={updating}>
              {updating ? "Saving..." : "Mark as Started"}
            </Button>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
