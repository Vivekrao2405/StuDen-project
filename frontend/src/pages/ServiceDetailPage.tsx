import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { getPublicService } from "@/lib/api/endpoints/publicProfile";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { ServiceDetailView, type ServiceDetailViewData } from "@/pages/marketplace/ServiceDetailView";

export function ServiceDetailPage() {
  const { serviceId = "" } = useParams<{ serviceId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const { data, error, loading, refetch } = useAsync(() => getPublicService(serviceId), [serviceId]);

  const [showJustPublished] = useState(
    Boolean((location.state as { justPublished?: boolean } | null)?.justPublished)
  );

  useEffect(() => {
    if (!showJustPublished) return;
    // Clear the nav-state flag so a page refresh doesn't re-show the banner.
    navigate(location.pathname, { replace: true, state: {} });
  }, [showJustPublished, navigate, location.pathname]);

  if (loading) {
    return <LoadingState label="Loading service..." />;
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
              profileImageUrl: data.providerProfileImageUrl,
              slug: data.providerSlug,
            }}
          />

          <div className="flex flex-wrap gap-2 border-t border-border pt-4">
            <Button variant="outline" size="sm" render={<Link to={ROUTES.publicProfile(data.providerSlug)} />}>
              View Profile
            </Button>
            <Button size="sm" disabled title="Coming soon">
              Request Service (Coming soon)
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
