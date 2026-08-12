import { ArrowRight, MapPin } from "lucide-react";
import { Link } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { SkillChip } from "@/components/shared/SkillChip";
import type { StudentResultResponse } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";

const MAX_SKILLS = 4;

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function StudentResultCard({ result }: { result: StudentResultResponse }) {
  const displayedSkills = result.skills.slice(0, MAX_SKILLS);

  return (
    <Card className="flex h-full flex-col">
      <CardContent className="flex flex-1 flex-col gap-3">
        <div className="flex items-start justify-between gap-2">
          <Avatar className="size-12 border border-border">
            {result.profileImageUrl ? <AvatarImage src={result.profileImageUrl} alt={result.fullName} /> : null}
            <AvatarFallback>{getInitials(result.fullName)}</AvatarFallback>
          </Avatar>
          <Badge variant={result.available ? "default" : "secondary"}>
            {result.available ? "Available" : "Not available"}
          </Badge>
        </div>

        <div className="min-w-0">
          <h3 className="text-base font-semibold whitespace-normal break-words text-foreground">
            {result.fullName}
          </h3>
          <p className="text-sm font-medium whitespace-normal break-words text-primary">{result.headline}</p>
          {result.location ? (
            <p className="mt-1 inline-flex items-center gap-1 text-xs text-muted-foreground">
              <MapPin className="size-3.5 shrink-0" /> {result.location}
            </p>
          ) : null}
        </div>

        {displayedSkills.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {displayedSkills.map((skill) => (
              <SkillChip key={skill.id} skill={skill} variant="compact" />
            ))}
          </div>
        ) : null}

        {result.bio ? (
          <p className="line-clamp-2 flex-1 text-sm text-muted-foreground italic">&ldquo;{result.bio}&rdquo;</p>
        ) : (
          <div className="flex-1" />
        )}

        <Link
          to={ROUTES.publicProfile(result.publicSlug)}
          className="inline-flex items-center gap-1 self-end text-sm font-medium text-primary hover:underline"
        >
          View Profile <ArrowRight className="size-3.5" />
        </Link>
      </CardContent>
    </Card>
  );
}
