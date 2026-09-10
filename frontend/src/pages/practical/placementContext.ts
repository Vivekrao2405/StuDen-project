// Carried via React Router `state` (not the URL) when a student deep-links from Placement Prep
// into the shared /practical-attempts/:id workspace — lets PracticalAttemptPage show a small
// "Role · Skill · Placement Preparation" breadcrumb and surface the AI Coach panel, without
// affecting the ordinary (non-placement) practical-attempt experience at all when absent.
export interface PlacementContext {
  seriesId: string;
  seriesTitle: string;
  roleName: string;
  skillName: string;
  backHref: string;
}

export interface PracticalAttemptLocationState {
  placementContext?: PlacementContext;
}
