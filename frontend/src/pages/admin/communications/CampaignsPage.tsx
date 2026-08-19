import { BarChart3, Megaphone, Plus, RotateCcw, XCircle } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { cancelCampaign, listCampaigns, retryFailedCampaign } from "@/lib/api/endpoints/communications";
import type { CampaignSummaryResponse } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";
import { CAMPAIGN_STATUS_LABEL, CATEGORY_LABEL, campaignStatusBadgeVariant } from "@/pages/admin/communications/communicationsDisplay";

const PAGE_SIZE = 20;

export function CampaignsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [page, setPage] = useState(0);

  const list = useAsync(() => listCampaigns(page, PAGE_SIZE), [page]);

  async function handleCancel(id: string) {
    try {
      await cancelCampaign(id);
      toast.success("Campaign cancelled.");
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to cancel campaign.");
    }
  }

  async function handleRetry(id: string) {
    try {
      await retryFailedCampaign(id);
      toast.success("Retrying failed deliveries.");
      list.refetch();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to retry campaign.");
    }
  }

  function openCampaign(c: CampaignSummaryResponse) {
    if (c.status === "DRAFT") {
      navigate(ROUTES.adminCommunicationsCampaignDetail(c.id));
    } else {
      navigate(ROUTES.adminCommunicationsCampaignAnalytics(c.id));
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Communications</h1>
          <p className="text-sm text-muted-foreground">Build an audience, compose a message, and send campaigns to students.</p>
        </div>
        <Button size="sm" onClick={() => navigate(ROUTES.adminCommunicationsNew)}>
          <Plus className="size-4" /> New Campaign
        </Button>
      </div>

      <SegmentedControl
        value="campaigns"
        onChange={(value) => {
          if (value === "templates") navigate(ROUTES.adminCommunicationsTemplates);
          if (value === "segments") navigate(ROUTES.adminCommunicationsSegments);
        }}
        options={[
          { value: "campaigns", label: "Campaigns" },
          { value: "templates", label: "Templates" },
          { value: "segments", label: "Segments" },
        ]}
      />

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : list.data && list.data.content.length > 0 ? (
        <>
          <div className="space-y-2">
            {list.data.content.map((c) => (
              <CampaignRow key={c.id} campaign={c} onOpen={() => openCampaign(c)} onCancel={() => handleCancel(c.id)} onRetry={() => handleRetry(c.id)} />
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={Megaphone}
          title="No campaigns yet"
          description="Create your first campaign to message students."
          action={
            <Button size="sm" onClick={() => navigate(ROUTES.adminCommunicationsNew)}>
              <Plus className="size-4" /> New Campaign
            </Button>
          }
        />
      )}
    </div>
  );
}

function CampaignRow({
  campaign,
  onOpen,
  onCancel,
  onRetry,
}: {
  campaign: CampaignSummaryResponse;
  onOpen: () => void;
  onCancel: () => void;
  onRetry: () => void;
}) {
  const channels = [campaign.sendEmail && "Email", campaign.sendPush && "Push", campaign.sendInapp && "In-App"]
    .filter(Boolean)
    .join(" + ");
  const canCancel = campaign.status === "SCHEDULED" || campaign.status === "DRAFT";
  const canRetry = campaign.status === "PARTIALLY_SENT" || campaign.status === "FAILED";

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
      <button type="button" onClick={onOpen} className="min-w-0 flex-1 text-left">
        <p className="truncate text-sm font-medium text-foreground">{campaign.name}</p>
        <p className="truncate text-xs text-muted-foreground">
          {CATEGORY_LABEL[campaign.category]} · {channels || "No channels"} ·{" "}
          {campaign.resolvedRecipientCount !== null ? `${campaign.resolvedRecipientCount.toLocaleString()} recipients` : "Not yet resolved"}
        </p>
      </button>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        {campaign.marketing ? <Badge variant="outline">Marketing</Badge> : null}
        <Badge variant={campaignStatusBadgeVariant(campaign.status)}>{CAMPAIGN_STATUS_LABEL[campaign.status]}</Badge>
        {campaign.status !== "DRAFT" ? (
          <Button size="sm" variant="outline" onClick={onOpen}>
            <BarChart3 className="size-4" /> Analytics
          </Button>
        ) : null}
        {canRetry ? (
          <Button size="sm" variant="outline" onClick={onRetry}>
            <RotateCcw className="size-4" /> Retry Failed
          </Button>
        ) : null}
        {canCancel ? (
          <Button size="sm" variant="destructive" onClick={onCancel}>
            <XCircle className="size-4" /> Cancel
          </Button>
        ) : null}
      </div>
    </div>
  );
}
