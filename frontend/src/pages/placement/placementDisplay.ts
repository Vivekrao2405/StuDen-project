import type { CompanyType, ExperienceLevel } from "@/lib/api/placementTypes";

export const COMPANY_TYPE_LABEL: Record<CompanyType, string> = {
  SERVICE_BASED: "Service-based",
  PRODUCT_BASED: "Product-based",
  STARTUP: "Startup",
  CONSULTING: "Consulting",
  GCC: "GCC",
  OTHER: "Other",
};
export const COMPANY_TYPE_OPTIONS = Object.entries(COMPANY_TYPE_LABEL) as [CompanyType, string][];

export const EXPERIENCE_LEVEL_LABEL: Record<ExperienceLevel, string> = {
  BEGINNER: "Beginner",
  INTERMEDIATE: "Intermediate",
  ADVANCED: "Advanced",
};
export const EXPERIENCE_LEVEL_OPTIONS = Object.entries(EXPERIENCE_LEVEL_LABEL) as [ExperienceLevel, string][];
