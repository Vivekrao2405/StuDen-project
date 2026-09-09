import { useNavigate } from "react-router-dom";

import { SegmentedControl } from "@/components/ui/segmented-control";
import { ROUTES } from "@/lib/routes";

export type PlacementSection = "roles" | "skills" | "role-skills" | "companies" | "assessments" | "series";

const SECTION_ROUTE: Record<PlacementSection, string> = {
  roles: ROUTES.adminPlacementRoles,
  skills: ROUTES.adminPlacementSkills,
  "role-skills": ROUTES.adminPlacementRoleSkills,
  companies: ROUTES.adminPlacementCompanies,
  assessments: ROUTES.adminPlacementAssessments,
  series: ROUTES.adminPlacementSeries,
};

/** Shared sub-nav for the Placement Management screens — same SegmentedControl-driven pattern the
 * Communications admin section already uses for Campaigns/Templates/Segments. */
export function PlacementTabs({ active }: { active: PlacementSection }) {
  const navigate = useNavigate();

  return (
    <SegmentedControl
      value={active}
      onChange={(value) => navigate(SECTION_ROUTE[value])}
      options={[
        { value: "roles", label: "Roles" },
        { value: "skills", label: "Skills" },
        { value: "role-skills", label: "Role-Skill Mapping" },
        { value: "companies", label: "Companies" },
        { value: "assessments", label: "Readiness Assessments" },
        { value: "series", label: "Placement Series" },
      ]}
    />
  );
}
