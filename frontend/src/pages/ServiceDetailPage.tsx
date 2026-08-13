import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ErrorState } from "@/components/shared/ErrorState";
import { useAuth } from "@/features/auth/useAuth";
import { getMyPortfolio } from "@/lib/api/endpoints/portfolio";
import { getPublicService } from "@/lib/api/endpoints/publicProfile";
import type { ServiceRequestRecord } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { RequestServiceDialog } from "@/pages/marketplace/RequestServiceDialog";
import { ServiceDetailSkeleton } from "@/pages/marketplace/ServiceDetailSkeleton";
import { ServiceDetailView, type ServiceDetailViewData } from "@/pages/marketplace/ServiceDetailView";

export function ServiceDetailPage() {
  const { serviceId = "" } = useParams<{ serviceId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const { status: authStatus } = useAuth();
  const { data, error, loading, refetch } = useAsync(() => getPublicService(serviceId), [serviceId]);

  const [showJustPublished] = useState(
    Boolean((location.state as { justPublished?: boolean } | null)?.justPublished)
  );
  const [requestDialogOpen, setRequestDialogOpen] = useState(false);
  // Only fetched when authenticated, and only to answer "is this my own service?" — a brand-new
  // user with no portfolio yet simply never resolves to a match, which is the correct outcome.
  const [ownProviderSlug, setOwnProviderSlug] = useState<string | null>(null);

  useEffect(() => {
    if (!showJustPublished) return;
    // Clear the nav-state flag so a page refresh doesn't re-show the banner.
    navigate(location.pathname, { replace: true, state: {} });
  }, [showJustPublished, navigate, location.pathname]);

  useEffect(() => {
    if (authStatus !== "authenticated") return;
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
  }, [authStatus]);

  function handleRequestSuccess(request: ServiceRequestRecord) {
    navigate(ROUTES.serviceRequestDetail(request.id), { state: { justSubmitted: true } });
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6 lg:px-8">
        <Card>
          <CardContent className="pt-4">
            <ServiceDetailSkeleton />
          </CardContent>
        </Card>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16">
        <ErrorState
          title="Service not found"
          message="This service doesn't exist or is no longer available."
          onRetry={refetch}
        />
      </div>
    );
  }

  const viewData: ServiceDetailViewData = {
    title: data.title,
    description: data.description,
    category: data.category,
    location: data.location,
    priceAmount: data.priceAmount,
    currency: data.currency,
    deliveryDays: data.deliveryDays,
    available: data.available,
    skills: data.skills,
    whatYoullReceive: data.whatYoullReceive,
    media: data.media,
    links: data.links,
    linkedProjects: data.linkedProjects,
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6 lg:px-8">
      <Link
        to={ROUTES.marketplace}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Marketplace
      </Link>

      {showJustPublished ? (
        <div className="rounded-lg border border-primary/30 bg-primary/5 px-4 py-3 text-sm font-medium text-primary">
          Your service is now live.
        </div>
      ) : null}

      <Card>
        <CardContent className="space-y-4 pt-4">
          <ServiceDetailView
            data={viewData}
            provider={{
              name: data.providerName,
              headline: data.providerHeadline,
              profileImageUrl: data.providerProfileImageUrl,
              slug: data.providerSlug,
            }}
          />

          <div className="flex flex-wrap gap-2 border-t border-border pt-4">
            <Button variant="outline" size="sm" render={<Link to={ROUTES.publicProfile(data.providerSlug)} />}>
              View Profile
            </Button>
            {authStatus === "authenticated" && ownProviderSlug === data.providerSlug ? (
              <Button size="sm" disabled>
                Your Service
              </Button>
            ) : !data.available ? (
              <Button size="sm" disabled>
                Currently Unavailable
              </Button>
            ) : (
              <Button
                size="sm"
                onClick={() => {
                  if (authStatus === "authenticated") {
                    setRequestDialogOpen(true);
                  } else {
                    navigate(ROUTES.login, { state: { from: location } });
                  }
                }}
              >
                Request Service
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <RequestServiceDialog
        open={requestDialogOpen}
        onOpenChange={setRequestDialogOpen}
        serviceId={data.id}
        serviceTitle={data.title}
        providerName={data.providerName}
        onSuccess={handleRequestSuccess}
      />
    </div>
  );
}
