import { Briefcase, Pencil } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SkillChip } from "@/components/shared/SkillChip";
import type { PlacementProfileResponse } from "@/lib/api/placementTypes";
import { COMPANY_TYPE_LABEL, EXPERIENCE_LEVEL_LABEL } from "@/pages/placement/placementDisplay";

interface PlacementProfileOverviewProps {
  profile: PlacementProfileResponse;
  onEdit: () => void;
}

/** The student's existing Placement experience, per Phase 2: just their saved profile plus an
 * Edit entry point. Readiness scoring, skill gaps, recommendations and the rest of the Placement
 * dashboard are later phases and deliberately not shown here. */
export function PlacementProfileOverview({ profile, onEdit }: PlacementProfileOverviewProps) {
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Your Placement Profile</h1>
          <p className="text-sm text-muted-foreground">This is what StuDen uses to personalize your placement prep.</p>
        </div>
        <Button size="sm" onClick={onEdit}>
          <Pencil className="size-4" /> Edit Profile
        </Button>
      </div>

      <div className="space-y-5 rounded-xl border border-border bg-card p-5">
        <div className="flex items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
            <Briefcase className="size-5 text-primary" />
          </div>
          <div>
            <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Target Role</p>
            <p className="text-base font-semibold text-foreground">{profile.targetRoleName}</p>
          </div>
        </div>

        <div>
          <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">Company Types</p>
          {profile.companyTypes.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {profile.companyTypes.map((type) => (
                <Badge key={type} variant="secondary">
                  {COMPANY_TYPE_LABEL[type]}
                </Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">None selected</p>
          )}
        </div>

        <div>
          <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">Target Companies</p>
          {profile.targetCompanies.length > 0 || profile.manualTargetCompanies.length > 0 ? (
            <div className="flex flex-wrap gap-1.5">
              {profile.targetCompanies.map((c) => (
                <Badge key={c.id} variant="outline">
                  {c.name}
                </Badge>
              ))}
              {profile.manualTargetCompanies.map((name) => (
                <Badge key={name} variant="outline" title="Manually added — not an official StuDen-verified company">
                  {name}
                </Badge>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">None selected</p>
          )}
        </div>

        <div>
          <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">Current Skills</p>
          {profile.currentSkills.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {profile.currentSkills.map((skill) => (
                <SkillChip key={skill.id} skill={skill} variant="compact" />
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">None selected</p>
          )}
        </div>

        <div>
          <p className="mb-1 text-xs font-medium tracking-wide text-muted-foreground uppercase">Experience Level</p>
          <p className="text-sm font-medium text-foreground">{EXPERIENCE_LEVEL_LABEL[profile.experienceLevel]}</p>
        </div>
      </div>
    </div>
  );
}
