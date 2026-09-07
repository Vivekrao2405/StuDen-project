import { Pencil } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { CompanyType, ExperienceLevel, PlacementCompanyResponse } from "@/lib/api/placementTypes";
import { COMPANY_TYPE_LABEL, EXPERIENCE_LEVEL_LABEL } from "@/pages/placement/placementDisplay";

interface ReviewSectionProps {
  title: string;
  onEdit: () => void;
  children: React.ReactNode;
}

function ReviewSection({ title, onEdit, children }: ReviewSectionProps) {
  return (
    <div className="flex items-start justify-between gap-3 border-b border-border pb-4 last:border-b-0 last:pb-0">
      <div className="min-w-0 space-y-1">
        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">{title}</p>
        {children}
      </div>
      <Button type="button" variant="ghost" size="sm" onClick={onEdit}>
        <Pencil className="size-3.5" /> Edit
      </Button>
    </div>
  );
}

interface PlacementReviewStepProps {
  roleName: string | null;
  companyTypes: CompanyType[];
  selectedCompanies: PlacementCompanyResponse[];
  manualCompanies: string[];
  currentSkillNames: string[];
  experienceLevel: ExperienceLevel | null;
  onEditStep: (step: number) => void;
}

export function PlacementReviewStep({
  roleName,
  companyTypes,
  selectedCompanies,
  manualCompanies,
  currentSkillNames,
  experienceLevel,
  onEditStep,
}: PlacementReviewStepProps) {
  const allCompanyNames = [...selectedCompanies.map((c) => c.name), ...manualCompanies];

  return (
    <div className="space-y-4 rounded-xl border border-border bg-card p-4">
      <ReviewSection title="Target Role" onEdit={() => onEditStep(1)}>
        <p className="text-sm font-medium text-foreground">{roleName ?? "Not selected"}</p>
      </ReviewSection>

      <ReviewSection title="Company Types" onEdit={() => onEditStep(2)}>
        <p className="text-sm text-foreground">
          {companyTypes.length > 0 ? companyTypes.map((t) => COMPANY_TYPE_LABEL[t]).join(", ") : "None selected"}
        </p>
      </ReviewSection>

      <ReviewSection title="Target Companies" onEdit={() => onEditStep(3)}>
        <p className="text-sm text-foreground">{allCompanyNames.length > 0 ? allCompanyNames.join(", ") : "None selected"}</p>
      </ReviewSection>

      <ReviewSection title="Current Skills" onEdit={() => onEditStep(4)}>
        <p className="text-sm text-foreground">{currentSkillNames.length > 0 ? currentSkillNames.join(", ") : "None selected"}</p>
      </ReviewSection>

      <ReviewSection title="Experience Level" onEdit={() => onEditStep(5)}>
        <p className="text-sm font-medium text-foreground">
          {experienceLevel ? EXPERIENCE_LEVEL_LABEL[experienceLevel] : "Not selected"}
        </p>
      </ReviewSection>
    </div>
  );
}
