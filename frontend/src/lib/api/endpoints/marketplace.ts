import { apiFetch } from "@/lib/api/client";
import type { MarketplaceSearchParams, MarketplaceSearchResponse } from "@/lib/api/types";

export function searchMarketplace(params: MarketplaceSearchParams) {
  const query = new URLSearchParams();
  if (params.q) query.set("q", params.q);
  if (params.category) query.set("category", params.category);
  if (params.location) query.set("location", params.location);
  if (params.availability) query.set("availability", params.availability);
  if (params.skill) query.set("skill", params.skill);
  if (params.sort) query.set("sort", params.sort);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<MarketplaceSearchResponse>(`/marketplace${qs ? `?${qs}` : ""}`);
}
