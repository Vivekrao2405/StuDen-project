import { Clock3, MapPin, Pencil, Trash2, Wallet } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import type { PortfolioResponse } from "@/lib/api/types";

interface PortfolioSummaryCardProps {
  portfolio: PortfolioResponse;
  onEdit: () => void;
  onDeleteClick: () => void;
}

export function PortfolioSummaryCard({ portfolio, onEdit, onDeleteClick }: PortfolioSummaryCardProps) {
  return (
    <Card>
      <CardHeader className="flex-row items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg font-semibold text-foreground">{portfolio.headline}</h2>
            <Badge variant={portfolio.available ? "default" : "secondary"}>
              {portfolio.available ? "Available" : "Not available"}
            </Badge>
          </div>
          <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
            {portfolio.location ? (
              <span className="inline-flex items-center gap-1">
                <MapPin className="size-3.5" /> {portfolio.location}
              </span>
            ) : null}
            {portfolio.hourlyRate != null ? (
              <span className="inline-flex items-center gap-1">
                <Wallet className="size-3.5" /> ₹{portfolio.hourlyRate}/hr
              </span>
            ) : null}
            {portfolio.responseTime ? (
              <span className="inline-flex items-center gap-1">
                <Clock3 className="size-3.5" /> {portfolio.responseTime}
              </span>
            ) : null}
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
