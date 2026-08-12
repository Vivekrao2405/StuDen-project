import { ArrowRight, MapPin, User } from "lucide-react";
import { Link } from "react-router-dom";

import { Card, CardContent } from "@/components/ui/card";
import { SkillChip } from "@/components/shared/SkillChip";
import type { ServiceResultResponse } from "@/lib/api/types";
import { MARKETPLACE_CATEGORY_OPTIONS } from "@/lib/marketplaceOptions";
import { ROUTES } from "@/lib/routes";

const MAX_SKILLS = 4;

function categoryLabel(category: ServiceResultResponse["category"]) {
  return MARKETPLACE_CATEGORY_OPTIONS.find((c) => c.value === category)?.label ?? category;
}

// "View Service" links to the provider's public profile, reusing the existing public-profile
// page — there's no service-detail page in this phase (that's future work), so this is the
// closest real, existing destination rather than a dead link.
export function ServiceResultCard({ result }: { result: ServiceResultResponse }) {
  const displayedSkills = result.skills.slice(0, MAX_SKILLS);

  return (
    <Card className="flex h-full flex-col">
      <CardContent className="flex flex-1 flex-col gap-3">
        <span className="w-fit rounded-full bg-accent px-2.5 py-0.5 text-xs font-medium text-primary">
          {categoryLabel(result.category)}
        </span>

        <div className="min-w-0">
          <h3 className="text-base font-semibold text-foreground">{result.title}</h3>
          {result.description ? (
            <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{result.description}</p>
          ) : null}
        </div>

        {displayedSkills.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {displayedSkills.map((skill) => (
              <SkillChip key={skill.id} skill={skill} variant="compact" />
            ))}
          </div>
        ) : null}

        <div className="flex-1 space-y-1 text-xs text-muted-foreground">
          <p className="inline-flex items-center gap-1">
            <User className="size-3.5 shrink-0" /> By {result.providerName}
          </p>
          {result.location ? (
            <p className="inline-flex items-center gap-1">
              <MapPin className="size-3.5 shrink-0" /> {result.location}
            </p>
          ) : null}
        </div>

        <Link
          to={ROUTES.publicProfile(result.providerSlug)}
          className="inline-flex items-center gap-1 self-end text-sm font-medium text-primary hover:underline"
        >
          View Service <ArrowRight className="size-3.5" />
        </Link>
      </CardContent>
    </Card>
  );
}
