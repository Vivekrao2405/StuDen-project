import { Clock3, MapPin, Pencil, Trash2 } from "lucide-react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { CoverImage } from "@/components/shared/CoverImage";
import type { PortfolioResponse, UserResponse } from "@/lib/api/types";

interface PortfolioSummaryCardProps {
  portfolio: PortfolioResponse;
  user: Pick<UserResponse, "fullName" | "profileImageUrl">;
  onEdit: () => void;
  onDeleteClick: () => void;
}

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function PortfolioSummaryCard({ portfolio, user, onEdit, onDeleteClick }: PortfolioSummaryCardProps) {
  return (
    <Card>
      <CoverImage
        src={portfolio.coverImageUrl}
        alt={`${user.fullName} cover`}
        className="-mx-(--card-spacing) -mt-(--card-spacing) mb-2 rounded-t-xl"
      />
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div className="flex items-start gap-3">
          <Avatar className="size-12 border border-border">
            {user.profileImageUrl ? <AvatarImage src={user.profileImageUrl} alt={user.fullName} /> : null}
            <AvatarFallback>{getInitials(user.fullName)}</AvatarFallback>
          </Avatar>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-lg font-semibold text-foreground">{user.fullName}</h2>
              <Badge variant={portfolio.available ? "default" : "secondary"}>
                {portfolio.available ? "Available" : "Not available"}
              </Badge>
            </div>
            <p className="text-sm text-muted-foreground">{portfolio.headline}</p>
            <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
              {portfolio.location ? (
                <span className="inline-flex items-center gap-1">
                  <MapPin className="size-3.5" /> {portfolio.location}
                </span>
              ) : null}
              {portfolio.responseTime ? (
                <span className="inline-flex items-center gap-1">
                  <Clock3 className="size-3.5" /> {portfolio.responseTime}
                </span>
              ) : null}
            </div>
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          <Button variant="outline" size="icon-sm" aria-label="Edit portfolio" onClick={onEdit}>
            <Pencil />
          </Button>
          <Button variant="destructive" size="icon-sm" aria-label="Delete portfolio" onClick={onDeleteClick}>
            <Trash2 />
          </Button>
        </div>
      </CardHeader>
      {portfolio.bio || portfolio.experienceSummary ? (
        <CardContent className="space-y-3">
          {portfolio.bio ? <p className="text-sm text-foreground">{portfolio.bio}</p> : null}
          {portfolio.experienceSummary ? (
            <p className="text-sm text-muted-foreground">{portfolio.experienceSummary}</p>
          ) : null}
        </CardContent>
      ) : null}
    </Card>
  );
}
