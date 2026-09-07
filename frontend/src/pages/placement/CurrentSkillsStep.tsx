import { ErrorState } from "@/components/shared/ErrorState";
import { SelectableOption } from "@/components/shared/SelectableOption";
import { SkillIcon } from "@/components/shared/SkillIcon";
import { Skeleton } from "@/components/ui/skeleton";
import type { RoleSkillResponse } from "@/lib/api/placementTypes";

interface CurrentSkillsStepProps {
  roleSkills: RoleSkillResponse[] | null;
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  selectedSkillIds: string[];
  onChange: (skillIds: string[]) => void;
}

/** Step 4: which of the target role's configured required skills does the student already have.
 * "Current skills" is the student's own data — never the role's requirements, which come from
 * the Admin Role -> Skill mapping and are only used here to decide which skills are selectable.
 * Purely presentational: PlacementOnboarding owns fetching the role's skills (so Review can reuse
 * the same data) and re-evaluating the selection when the target role changes. */
export function CurrentSkillsStep({ roleSkills, loading, error, onRetry, selectedSkillIds, onChange }: CurrentSkillsStepProps) {
  function toggle(skillId: string) {
    const next = selectedSkillIds.includes(skillId)
      ? selectedSkillIds.filter((id) => id !== skillId)
      : [...selectedSkillIds, skillId];
    onChange(next);
  }

  if (loading) {
    return (
      <div className="space-y-2" aria-hidden="true">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-14 w-full rounded-xl" />
        ))}
      </div>
    );
  }

  if (error) {
    return <ErrorState message={error} onRetry={onRetry} />;
  }

  if (!roleSkills || roleSkills.length === 0) {
    return (
      <p className="rounded-xl border border-dashed border-border bg-muted/30 px-4 py-6 text-center text-sm text-muted-foreground">
        No skills have been configured for this role yet.
      </p>
    );
  }

  return (
    <div className="space-y-2" role="group" aria-label="Current skills">
      {roleSkills.map((rs) => (
        <SelectableOption
          key={rs.skillId}
          multi
          selected={selectedSkillIds.includes(rs.skillId)}
          onClick={() => toggle(rs.skillId)}
          title={rs.skillName}
          subtitle={rs.skillCategory}
          icon={<SkillIcon iconSlug={rs.skillIconSlug} iconType={rs.skillIconType} className="size-5 shrink-0" />}
        />
      ))}
    </div>
  );
}
