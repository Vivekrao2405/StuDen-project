import { MapPin, Share2, Sparkles } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { SkillChip } from "@/components/shared/SkillChip";
import type { PortfolioResponse, UserResponse } from "@/lib/api/types";
import type { ProfileCompletion } from "@/lib/profileCompletion";
import { ROUTES } from "@/lib/routes";

const MAX_DISPLAYED_SKILLS = 4;

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

interface ProfileSummaryCardProps {
  user: Pick<UserResponse, "fullName" | "profileImageUrl">;
  portfolio: PortfolioResponse | null;
  completion: ProfileCompletion;
}

export function ProfileSummaryCard({ user, portfolio, completion }: ProfileSummaryCardProps) {
  const navigate = useNavigate();

  if (!portfolio) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-4 py-10 text-center sm:py-12">
          <div className="flex size-14 items-center justify-center rounded-full bg-accent">
            <Sparkles className="size-7 text-primary" />
          </div>
          <div className="space-y-1">
            <h2 className="text-lg font-semibold text-foreground">Your profile is waiting to be built</h2>
            <p className="max-w-sm text-sm text-muted-foreground">
              Create your portfolio to showcase your skills, projects and achievements.
            </p>
          </div>
          <Button onClick={() => navigate(ROUTES.profile)}>Complete Profile</Button>
        </CardContent>
      </Card>
    );
  }

  const displayedSkills = portfolio.skills.slice(0, MAX_DISPLAYED_SKILLS);
  const overflowCount = portfolio.skills.length - displayedSkills.length;

  return (
    <Card>
      <CardContent className="space-y-5">
        <div className="grid gap-5 lg:grid-cols-[auto_1fr_auto] lg:items-center">
          <div className="flex items-center gap-3">
            <div className="relative shrink-0">
              <Avatar className="size-16 border border-border">
                {user.profileImageUrl ? <AvatarImage src={user.profileImageUrl} alt={user.fullName} /> : null}
                <AvatarFallback className="text-lg">{getInitials(user.fullName)}</AvatarFallback>
              </Avatar>
              {portfolio.available ? (
                <span
                  className="absolute right-0.5 bottom-0.5 size-3.5 rounded-full border-2 border-card bg-emerald-500"
                  aria-label="Available"
                />
              ) : null}
            </div>
            <div className="min-w-0">
              <h2 className="truncate text-lg font-semibold text-foreground">{user.fullName}</h2>
              {portfolio.headline ? (
                <p className="truncate text-sm font-medium text-primary">{portfolio.headline}</p>
              ) : null}
              {portfolio.location ? (
                <p className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                  <MapPin className="size-3" /> {portfolio.location}
                </p>
              ) : null}
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">Profile completion</span>
              {completion.percent >= 100 ? (
                <span className="font-semibold text-primary">Profile complete ✓</span>
              ) : (
                <span className="text-lg leading-none font-bold text-primary">{completion.percent}%</span>
              )}
            </div>
            <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
              <div
                className="h-full rounded-full bg-primary transition-all"
                style={{ width: `${completion.percent}%` }}
              />
            </div>
          </div>

          <div className="flex flex-wrap gap-2 lg:flex-col">
            <Button onClick={() => navigate(ROUTES.profile)}>Complete Profile</Button>
            <Button variant="outline" onClick={() => navigate(`/u/${portfolio.publicSlug}`)}>
              View Portfolio
            </Button>
            <Button variant="outline" onClick={() => navigate(ROUTES.shareProfile)}>
              <Share2 /> Share Profile
            </Button>
          </div>
        </div>

        {displayedSkills.length > 0 ? (
          <div className="flex flex-wrap gap-2 border-t border-border pt-4">
            {displayedSkills.map((skill) => (
              <SkillChip key={skill.id} skill={skill} variant="compact" />
            ))}
            {overflowCount > 0 ? (
              <span className="inline-flex items-center rounded-full border border-border bg-card px-2.5 py-1 text-sm text-muted-foreground">
                +{overflowCount}
              </span>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
