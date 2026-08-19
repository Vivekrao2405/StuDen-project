import { ArrowLeft } from "lucide-react";
import { useNavigate, useParams } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/shared/ErrorState";
import { getCampaign, getCampaignAnalytics } from "@/lib/api/endpoints/communications";
import type { RecipientStatus } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { CAMPAIGN_STATUS_LABEL, CATEGORY_LABEL, RECIPIENT_STATUS_LABEL, campaignStatusBadgeVariant } from "@/pages/admin/communications/communicationsDisplay";

const EMAIL_STATUSES: RecipientStatus[] = ["QUEUED", "SENT", "DELIVERED", "BOUNCED", "COMPLAINED", "FAILED", "SKIPPED"];
const PUSH_STATUSES: RecipientStatus[] = ["QUEUED", "SENT", "FAILED", "SKIPPED"];
const INAPP_STATUSES: RecipientStatus[] = ["QUEUED", "SENT", "FAILED", "SKIPPED"];

export function CampaignAnalyticsPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const campaignId = id ?? "";

  const campaign = useAsync(() => getCampaign(campaignId), [campaignId]);
  const analytics = useAsync(() => getCampaignAnalytics(campaignId), [campaignId]);

  if (campaign.loading || analytics.loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full rounded-xl" />
      </div>
    );
  }

  if (campaign.error || !campaign.data) {
    return <ErrorState message={campaign.error?.message ?? "Campaign not found."} onRetry={campaign.refetch} />;
  }

  const c = campaign.data;

  return (
    <div className="space-y-6">
      <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.adminCommunications)}>
        <ArrowLeft className="size-4" /> Back to Communications
      </Button>

      <div className="flex flex-wrap items-center gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">{c.name}</h1>
          <p className="text-sm text-muted-foreground">
            {CATEGORY_LABEL[c.category]} · {c.resolvedRecipientCount !== null ? `${c.resolvedRecipientCount.toLocaleString()} recipients` : "Not yet resolved"}
          </p>
        </div>
        <Badge variant={campaignStatusBadgeVariant(c.status)}>{CAMPAIGN_STATUS_LABEL[c.status]}</Badge>
      </div>

      {analytics.error ? (
        <ErrorState message={analytics.error.message} onRetry={analytics.refetch} />
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {c.sendEmail ? <ChannelStats title="Email" statuses={EMAIL_STATUSES} data={analytics.data?.email ?? {}} /> : null}
          {c.sendPush ? <ChannelStats title="Push" statuses={PUSH_STATUSES} data={analytics.data?.push ?? {}} /> : null}
          {c.sendInapp ? <ChannelStats title="In-App" statuses={INAPP_STATUSES} data={analytics.data?.inapp ?? {}} /> : null}
        </div>
      )}

      {(c.emailSubject || c.pushTitle || c.inappTitle) ? (
        <div className="space-y-3 rounded-xl border border-border bg-card p-4">
          <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Message sent</p>
          {c.emailSubject ? <p className="text-sm text-foreground"><span className="font-medium">Email:</span> {c.emailSubject}</p> : null}
          {c.pushTitle ? <p className="text-sm text-foreground"><span className="font-medium">Push:</span> {c.pushTitle}</p> : null}
          {c.inappTitle ? <p className="text-sm text-foreground"><span className="font-medium">In-App:</span> {c.inappTitle}</p> : null}
        </div>
      ) : null}
    </div>
  );
}

function ChannelStats({
  title,
  statuses,
  data,
}: {
  title: string;
  statuses: RecipientStatus[];
  data: Partial<Record<RecipientStatus, number>>;
}) {
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <p className="mb-3 text-sm font-semibold text-foreground">{title}</p>
      <dl className="space-y-1.5">
        {statuses.map((status) => (
          <div key={status} className="flex items-center justify-between text-sm">
            <dt className="text-muted-foreground">{RECIPIENT_STATUS_LABEL[status]}</dt>
            <dd className="font-medium text-foreground">{(data[status] ?? 0).toLocaleString()}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
