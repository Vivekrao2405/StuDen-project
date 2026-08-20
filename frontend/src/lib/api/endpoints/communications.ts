import { apiFetch } from "@/lib/api/client";
import type {
  AudiencePreviewResponse,
  CampaignAnalyticsResponse,
  CampaignDetailResponse,
  CampaignRequest,
  CampaignSummaryResponse,
  PageResponse,
  RecipientChannel,
  RecipientFailureResponse,
  SegmentRequest,
  SegmentResponse,
  TemplateRequest,
  TemplateResponse,
} from "@/lib/api/types";

const BASE = "/admin/communications";

export function listCampaigns(page = 0, size = 20) {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return apiFetch<PageResponse<CampaignSummaryResponse>>(`${BASE}/campaigns?${query.toString()}`);
}

export function getCampaign(id: string) {
  return apiFetch<CampaignDetailResponse>(`${BASE}/campaigns/${id}`);
}

export function createCampaign(request: CampaignRequest) {
  return apiFetch<CampaignDetailResponse>(`${BASE}/campaigns`, { method: "POST", body: request });
}

export function updateCampaign(id: string, request: CampaignRequest) {
  return apiFetch<CampaignDetailResponse>(`${BASE}/campaigns/${id}`, { method: "PATCH", body: request });
}

// Recipient count/sample always come from the backend — never computed client-side. Not scoped
// to a campaign id, so it can be called while a wizard draft only exists in local state.
// `marketing` must match the campaign's own marketing toggle (false for anything without one,
// e.g. a saved segment) — the backend applies the exact same marketing-opt-out exclusion here
// that it applies when the campaign is actually sent, so the estimate can never diverge from who
// will really receive it.
export function previewAudience(filterJson: string, marketing: boolean) {
  return apiFetch<AudiencePreviewResponse>(`${BASE}/campaigns/audience-preview`, {
    method: "POST",
    body: { filterJson, marketing },
  });
}

export function sendCampaignNow(id: string) {
  return apiFetch<void>(`${BASE}/campaigns/${id}/send-now`, { method: "POST" });
}

export function scheduleCampaign(id: string, scheduledAt: string) {
  return apiFetch<void>(`${BASE}/campaigns/${id}/schedule`, { method: "POST", body: { scheduledAt } });
}

export function cancelCampaign(id: string) {
  return apiFetch<void>(`${BASE}/campaigns/${id}/cancel`, { method: "POST" });
}

export function retryFailedCampaign(id: string) {
  return apiFetch<void>(`${BASE}/campaigns/${id}/retry-failed`, { method: "POST" });
}

export function getCampaignAnalytics(id: string) {
  return apiFetch<CampaignAnalyticsResponse>(`${BASE}/campaigns/${id}/analytics`);
}

// The real per-recipient provider error (e.g. Resend's exact rejection reason), captured verbatim
// at send time — never fabricated. Lets an admin diagnose a FAILED count from this UI alone.
export function getFailedRecipients(id: string, channel: RecipientChannel) {
  const query = new URLSearchParams({ channel });
  return apiFetch<RecipientFailureResponse[]>(`${BASE}/campaigns/${id}/recipients/failed?${query.toString()}`);
}

export function listTemplates(includeArchived = false) {
  const query = new URLSearchParams({ includeArchived: String(includeArchived) });
  return apiFetch<TemplateResponse[]>(`${BASE}/templates?${query.toString()}`);
}

export function getTemplate(id: string) {
  return apiFetch<TemplateResponse>(`${BASE}/templates/${id}`);
}

export function createTemplate(request: TemplateRequest) {
  return apiFetch<TemplateResponse>(`${BASE}/templates`, { method: "POST", body: request });
}

export function updateTemplate(id: string, request: TemplateRequest) {
  return apiFetch<TemplateResponse>(`${BASE}/templates/${id}`, { method: "PATCH", body: request });
}

export function duplicateTemplate(id: string) {
  return apiFetch<TemplateResponse>(`${BASE}/templates/${id}/duplicate`, { method: "POST" });
}

export function archiveTemplate(id: string) {
  return apiFetch<void>(`${BASE}/templates/${id}/archive`, { method: "POST" });
}

export function listSegments() {
  return apiFetch<SegmentResponse[]>(`${BASE}/segments`);
}

export function createSegment(request: SegmentRequest) {
  return apiFetch<SegmentResponse>(`${BASE}/segments`, { method: "POST", body: request });
}

export function updateSegment(id: string, request: SegmentRequest) {
  return apiFetch<SegmentResponse>(`${BASE}/segments/${id}`, { method: "PATCH", body: request });
}

export function deleteSegment(id: string) {
  return apiFetch<void>(`${BASE}/segments/${id}`, { method: "DELETE" });
}

// Live re-resolve against current data — never a cached/stored recipient list.
export function previewSegment(id: string) {
  return apiFetch<AudiencePreviewResponse>(`${BASE}/segments/${id}/preview`, { method: "POST" });
}
