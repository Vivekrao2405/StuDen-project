import { ArrowRight, Clock, MapPin } from "lucide-react";
import { Link } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { SkillChip } from "@/components/shared/SkillChip";
import type { ServiceResultResponse } from "@/lib/api/types";
import { cloudinaryThumb } from "@/lib/cloudinary";
import { MARKETPLACE_CATEGORY_OPTIONS } from "@/lib/marketplaceOptions";
import { ROUTES } from "@/lib/routes";

const MAX_SKILLS = 4;

function categoryLabel(category: ServiceResultResponse["category"]) {
  return MARKETPLACE_CATEGORY_OPTIONS.find((c) => c.value === category)?.label ?? category;
}

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function ServiceResultCard({ result }: { result: ServiceResultResponse }) {
  const displayedSkills = result.skills.slice(0, MAX_SKILLS);
  const thumb = cloudinaryThumb(result.coverImageUrl, 400);

  return (
    <Card className="flex h-full flex-col overflow-hidden">
      {thumb ? (
        <div className="relative aspect-[4/3] w-full bg-muted">
          <img src={thumb} alt="" loading="lazy" className="h-full w-full object-cover" />
          {!result.available ? (
            <Badge variant="secondary" className="absolute top-2 left-2">
              Currently unavailable
            </Badge>
          ) : null}
        </div>
      ) : null}

      <CardContent className="flex flex-1 flex-col gap-3">
        <div className="flex items-center gap-2">
          <span className="w-fit rounded-full bg-accent px-2.5 py-0.5 text-xs font-medium text-primary">
            {categoryLabel(result.category)}
          </span>
          {!thumb && !result.available ? <Badge variant="secondary">Currently unavailable</Badge> : null}
        </div>

        <div className="min-w-0">
          <h3 className="text-base font-semibold text-foreground">{result.title}</h3>
          {result.description ? (
            <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{result.description}</p>
          ) : null}
        </div>

        {result.priceAmount != null || result.deliveryDays != null ? (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            {result.priceAmount != null ? (
              <span className="font-semibold text-foreground">
                Starting at ₹{result.priceAmount.toLocaleString("en-IN")}
              </span>
            ) : null}
            {result.deliveryDays != null ? (
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                <Clock className="size-3.5" /> {result.deliveryDays} day{result.deliveryDays === 1 ? "" : "s"}
              </span>
            ) : null}
          </div>
        ) : null}

        {displayedSkills.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {displayedSkills.map((skill) => (
              <SkillChip key={skill.id} skill={skill} variant="compact" />
            ))}
          </div>
        ) : null}

        <div className="mt-auto flex items-center gap-2">
          <Avatar className="size-6 shrink-0">
            {result.providerProfileImageUrl ? (
              <AvatarImage src={result.providerProfileImageUrl} alt={result.providerName} />
            ) : null}
            <AvatarFallback className="text-[9px]">{getInitials(result.providerName)}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1 text-xs text-muted-foreground">
            <p className="truncate font-medium text-foreground">{result.providerName}</p>
            {result.providerHeadline ? <p className="truncate">{result.providerHeadline}</p> : null}
          </div>
        </div>

        {result.location ? (
          <p className="inline-flex items-center gap-1 text-xs text-muted-foreground">
            <MapPin className="size-3.5 shrink-0" /> {result.location}
          </p>
        ) : null}

        <Link
          to={ROUTES.serviceDetail(result.id)}
          className="inline-flex items-center gap-1 self-end text-sm font-medium text-primary hover:underline"
        >
          View Service <ArrowRight className="size-3.5" />
        </Link>
      </CardContent>
    </Card>
  );
}
