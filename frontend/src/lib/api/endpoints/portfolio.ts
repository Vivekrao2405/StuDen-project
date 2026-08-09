import { apiFetch } from "@/lib/api/client";
import type { PortfolioRequest, PortfolioResponse, ShareMetadataResponse } from "@/lib/api/types";

export function getMyPortfolio() {
  return apiFetch<PortfolioResponse>("/portfolio/me");
}

export function createPortfolio(payload: PortfolioRequest) {
  return apiFetch<PortfolioResponse>("/portfolio", { method: "POST", body: payload });
}

export function updatePortfolio(payload: PortfolioRequest) {
  return apiFetch<PortfolioResponse>("/portfolio/me", { method: "PUT", body: payload });
}

export function deletePortfolio() {
  return apiFetch<void>("/portfolio/me", { method: "DELETE" });
}

export function getShareMetadata() {
  return apiFetch<ShareMetadataResponse>("/portfolio/me/share");
}
