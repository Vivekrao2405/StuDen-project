import { apiFetch } from "@/lib/api/client";
import type { PortfolioRequest, PortfolioResponse, ShareMetadataResponse, SkillLevel } from "@/lib/api/types";

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

export function uploadCoverImage(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<PortfolioResponse>("/portfolio/me/cover-image", { method: "POST", body: formData });
}

export function removeCoverImage() {
  return apiFetch<PortfolioResponse>("/portfolio/me/cover-image", { method: "DELETE" });
}

export function getShareMetadata() {
  return apiFetch<ShareMetadataResponse>("/portfolio/me/share");
}

export function updateSkillLevel(skillId: string, level: SkillLevel) {
  return apiFetch<PortfolioResponse>(`/portfolio/me/skills/${skillId}/level`, {
    method: "PUT",
    body: { level },
  });
}
