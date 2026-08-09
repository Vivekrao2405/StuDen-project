import { Check, Copy, Share2 } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useToast } from "@/hooks/useToast";
import { getShareMetadata } from "@/lib/api/endpoints/portfolio";
import { useAsync } from "@/lib/hooks/useAsync";

export function ShareProfilePage() {
  const { data, error, loading, refetch } = useAsync(getShareMetadata, []);
  const [copied, setCopied] = useState(false);
  const toast = useToast();

  async function handleCopy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      toast.success("Link copied to clipboard.");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy the link. Please copy it manually.");
    }
  }

  async function handleShare(url: string) {
    try {
      await navigator.share({ url, title: "My StuDen profile" });
    } catch {
      // user cancelled the native share sheet — nothing to do
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Share Your Profile</h1>
        <p className="text-sm text-muted-foreground">
          Share your public StuDen profile link anywhere — LinkedIn, WhatsApp, resumes and more.
        </p>
      </div>

      {loading ? (
        <LoadingState label="Loading your profile link..." />
      ) : error ? (
        <ErrorState
          message={
            error.message.includes("portfolio")
              ? "You need to create a portfolio before you can share your profile."
              : error.message
          }
          onRetry={refetch}
        />
      ) : data ? (
        <Card>
          <CardHeader>
            <CardTitle>Your public profile link</CardTitle>
            <CardDescription>Anyone with this link can view your public profile — no account needed.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex gap-2">
              <Input readOnly value={data.profileUrl} className="h-10" />
              <Button variant="outline" size="icon-lg" aria-label="Copy link" onClick={() => handleCopy(data.profileUrl)}>
                {copied ? <Check className="text-primary" /> : <Copy />}
              </Button>
            </div>

            {typeof navigator !== "undefined" && "share" in navigator ? (
              <Button variant="outline" onClick={() => handleShare(data.profileUrl)}>
                <Share2 /> Share
              </Button>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
