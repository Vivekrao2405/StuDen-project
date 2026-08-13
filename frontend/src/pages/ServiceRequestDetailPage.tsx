import { ArrowLeft, ExternalLink } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { getMyPortfolio } from "@/lib/api/endpoints/portfolio";
import { getServiceRequest } from "@/lib/api/endpoints/serviceRequests";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { requestStatusLabel, requestStatusVariant } from "@/pages/requests/requestStatusBadge";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function ServiceRequestDetailPage() {
  const { requestId = "" } = useParams<{ requestId: string }>();
  const { data: request, error, loading, refetch } = useAsync(() => getServiceRequest(requestId), [requestId]);

  // Same "compare against my own portfolio slug" technique as ServiceDetailPage — the response
  // never exposes raw requester/provider ids, so this is how the page tells which side of the
  // request the viewer is on.
  const [ownProviderSlug, setOwnProviderSlug] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false;
    getMyPortfolio()
      .then((portfolio) => {
        if (!cancelled) setOwnProviderSlug(portfolio.publicSlug);
      })
      .catch(() => {
        if (!cancelled) setOwnProviderSlug(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return <LoadingState label="Loading request..." />;
  }

  if (error || !request) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16">
        <ErrorState
          title="Request not found"
          message="This request doesn't exist, or you don't have access to it."
          onRetry={refetch}
        />
      </div>
    );
  }

  const isProvider = ownProviderSlug != null && ownProviderSlug === request.providerSlug;
  const amount = request.proposedBudget ?? request.servicePriceAmount;

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-10 sm:px-6 lg:px-8">
      <Link
        to={ROUTES.serviceRequests}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Requests
      </Link>

      <Card>
        <CardContent className="space-y-5 pt-4">
          <div className="flex items-start justify-between gap-2">
            <div>
              <h1 className="text-lg font-semibold text-foreground">{request.serviceTitle}</h1>
              {amount != null ? (
                <p className="text-sm text-muted-foreground">₹{amount.toLocaleString("en-IN")}</p>
              ) : null}
            </div>
            <Badge variant={requestStatusVariant(request.status)}>{requestStatusLabel(request.status)}</Badge>
          </div>

          {request.status === "PENDING" && !isProvider ? (
            <div className="rounded-lg border border-primary/30 bg-primary/5 px-4 py-3 text-sm font-medium text-primary">
              Your request has been sent to the provider. You&apos;ll be notified when they respond.
            </div>
          ) : null}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="flex items-center gap-2 rounded-lg border border-border p-3">
              <Avatar className="size-8 shrink-0">
                {request.providerProfileImageUrl ? (
                  <AvatarImage src={request.providerProfileImageUrl} alt={request.providerName} />
                ) : null}
                <AvatarFallback className="text-xs">{getInitials(request.providerName)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-muted-foreground">Provider</p>
                {request.providerSlug ? (
                  <Link
                    to={ROUTES.publicProfile(request.providerSlug)}
                    className="truncate text-sm font-medium text-foreground hover:text-primary"
                  >
                    {request.providerName}
                  </Link>
                ) : (
                  <p className="truncate text-sm font-medium text-foreground">{request.providerName}</p>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2 rounded-lg border border-border p-3">
              <Avatar className="size-8 shrink-0">
                {request.requesterProfileImageUrl ? (
                  <AvatarImage src={request.requesterProfileImageUrl} alt={request.requesterName} />
                ) : null}
                <AvatarFallback className="text-xs">{getInitials(request.requesterName)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-muted-foreground">Requester</p>
                {request.requesterSlug ? (
                  <Link
                    to={ROUTES.publicProfile(request.requesterSlug)}
                    className="truncate text-sm font-medium text-foreground hover:text-primary"
                  >
                    {request.requesterName}
                  </Link>
                ) : (
                  <p className="truncate text-sm font-medium text-foreground">{request.requesterName}</p>
                )}
              </div>
            </div>
          </div>

          <div>
            <h2 className="text-sm font-semibold text-foreground">What they need</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{request.description}</p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <h2 className="text-sm font-semibold text-foreground">Expected delivery</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {request.requestedDeliveryDate ? formatDate(request.requestedDeliveryDate) : "Not specified"}
              </p>
            </div>
            <div>
              <h2 className="text-sm font-semibold text-foreground">Requested on</h2>
              <p className="mt-1 text-sm text-muted-foreground">{formatDate(request.createdAt)}</p>
            </div>
          </div>

          {request.links.length > 0 ? (
            <div>
              <h2 className="text-sm font-semibold text-foreground">Reference links</h2>
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {request.links.map((link, i) => (
                  <a
                    key={i}
                    href={link.url}
                    target="_blank"
                    rel="noreferrer noopener"
                    className="inline-flex items-center gap-1 rounded-full border border-border px-2.5 py-1 text-xs font-medium text-foreground hover:bg-muted"
                  >
                    {link.label} <ExternalLink className="size-3" />
                  </a>
                ))}
              </div>
            </div>
          ) : null}

          {isProvider ? (
            <div className="flex flex-wrap gap-2 border-t border-border pt-4">
              <Button size="sm" disabled title="Coming in a future phase">
                Accept
              </Button>
              <Button size="sm" variant="outline" disabled title="Coming in a future phase">
                Reject
              </Button>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
