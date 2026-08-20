import { AlertTriangle, ArrowLeft, ChevronDown, ChevronUp } from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/components/shared/ErrorState";
import { ApiError } from "@/lib/api/ApiError";
import { getCampaign, getCampaignAnalytics, getFailedRecipients } from "@/lib/api/endpoints/communications";
import type { RecipientChannel, RecipientFailureResponse, RecipientStatus } from "@/lib/api/types";
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
          {c.sendEmail ? (
            <ChannelStats title="Email" channel="EMAIL" campaignId={campaignId} statuses={EMAIL_STATUSES} data={analytics.data?.email ?? {}} />
          ) : null}
          {c.sendPush ? (
            <ChannelStats title="Push" channel="PUSH" campaignId={campaignId} statuses={PUSH_STATUSES} data={analytics.data?.push ?? {}} />
          ) : null}
          {c.sendInapp ? (
            <ChannelStats title="In-App" channel="INAPP" campaignId={campaignId} statuses={INAPP_STATUSES} data={analytics.data?.inapp ?? {}} />
          ) : null}
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
  channel,
  campaignId,
  statuses,
  data,
}: {
  title: string;
  channel: RecipientChannel;
  campaignId: string;
  statuses: RecipientStatus[];
  data: Partial<Record<RecipientStatus, number>>;
}) {
  const failedCount = data.FAILED ?? 0;

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
      {failedCount > 0 ? <FailureDetails campaignId={campaignId} channel={channel} count={failedCount} /> : null}
    </div>
  );
}

// Fetched on demand (not eagerly) so a healthy campaign with zero failures never issues this
// request. Shows the real provider error captured verbatim at send time (e.g. Resend's exact
// rejection reason) — never fabricated or re-derived.
function FailureDetails({ campaignId, channel, count }: { campaignId: string; channel: RecipientChannel; count: number }) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [failures, setFailures] = useState<RecipientFailureResponse[] | null>(null);

  const toggle = () => {
    if (open) {
      setOpen(false);
      return;
    }
    setOpen(true);
    if (failures !== null) return;
    setLoading(true);
    setError(null);
    getFailedRecipients(campaignId, channel)
      .then((data) => setFailures(data))
      .catch((err: ApiError | Error) => setError(err.message))
      .finally(() => setLoading(false));
  };

  return (
    <div className="mt-3 border-t border-border pt-3">
      <button
        type="button"
        onClick={toggle}
        className="flex w-full items-center justify-between text-sm font-medium text-destructive hover:underline"
      >
        <span className="flex items-center gap-1.5">
          <AlertTriangle className="size-3.5" /> {count} failure{count === 1 ? "" : "s"} — view error
        </span>
        {open ? <ChevronUp className="size-4" /> : <ChevronDown className="size-4" />}
      </button>
      {open ? (
        <div className="mt-2 space-y-2">
          {loading ? <Skeleton className="h-16 w-full rounded-lg" /> : null}
          {error ? <p className="text-sm text-destructive">{error}</p> : null}
          {failures?.map((f) => (
            <div key={f.recipientId} className="rounded-lg bg-muted/50 p-2.5 text-xs">
              <p className="font-medium text-foreground">{f.recipientEmail ?? "(no email on file)"}</p>
              <p className="mt-1 break-words text-muted-foreground">{f.errorMessage ?? "(no error message recorded)"}</p>
            </div>
          ))}
          {failures !== null && failures.length === 0 ? (
            <p className="text-sm text-muted-foreground">No failure details found.</p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
