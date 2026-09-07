import type { AssessmentLevel, PageResponse, SkillIconType } from "@/lib/api/types";

export type { PageResponse };

// Enable/disable for admin-managed reference data — mirrors the backend's PlacementCatalogStatus.
export type PlacementCatalogStatus = "ACTIVE" | "INACTIVE";

export type CompanyType = "SERVICE_BASED" | "PRODUCT_BASED" | "STARTUP" | "CONSULTING" | "GCC" | "OTHER";

// --- Roles + Role-Skill mapping --------------------------------------------------------------

export interface PlacementRoleRequest {
  name: string;
  description: string | null;
  displayOrder: number | null;
}

export interface PlacementRoleResponse {
  id: string;
  name: string;
  description: string | null;
  status: PlacementCatalogStatus;
  displayOrder: number;
  skillCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface RoleSkillResponse {
  id: string;
  skillId: string;
  skillName: string;
  skillCategory: string;
  skillIconSlug: string | null;
  skillIconType: SkillIconType;
  weight: number;
  requiredProficiency: AssessmentLevel | null;
  priority: number;
}

export interface PlacementRoleDetailResponse {
  id: string;
  name: string;
  description: string | null;
  status: PlacementCatalogStatus;
  displayOrder: number;
  skills: RoleSkillResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface RoleSkillRequest {
  skillId: string;
  weight: number | null;
  requiredProficiency: AssessmentLevel | null;
  priority: number | null;
}

// --- Companies --------------------------------------------------------------------------------

export interface PlacementCompanyRequest {
  name: string;
  companyType: CompanyType;
  description: string | null;
  logoUrl: string | null;
}

export interface PlacementCompanyResponse {
  id: string;
  name: string;
  companyType: CompanyType;
  description: string | null;
  logoUrl: string | null;
  status: PlacementCatalogStatus;
  createdAt: string;
  updatedAt: string;
}

// --- Skill catalog (admin) ----------------------------------------------------------------------

export interface AdminSkillResponse {
  id: string;
  name: string;
  category: string;
  iconSlug: string | null;
  iconType: SkillIconType;
  createdAt: string;
  updatedAt: string;
}
