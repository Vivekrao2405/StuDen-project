import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import type { ServiceRequestRecord } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";
import { requestStatusLabel, requestStatusVariant } from "@/pages/requests/requestStatusBadge";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

interface ServiceRequestCardProps {
  request: ServiceRequestRecord;
  perspective: "sent" | "received";
}

export function ServiceRequestCard({ request, perspective }: ServiceRequestCardProps) {
  const counterpartName = perspective === "sent" ? request.providerName : request.requesterName;
  const counterpartImage = perspective === "sent" ? request.providerProfileImageUrl : request.requesterProfileImageUrl;
  const amount = request.proposedBudget ?? request.servicePriceAmount;

  return (
    <Card>
      <CardContent className="flex flex-col gap-3 pt-4">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-sm font-semibold text-foreground">{request.serviceTitle}</h3>
          <Badge variant={requestStatusVariant(request.status)}>{requestStatusLabel(request.status)}</Badge>
        </div>

        <div className="flex items-center gap-2">
          <Avatar className="size-6 shrink-0">
            {counterpartImage ? <AvatarImage src={counterpartImage} alt={counterpartName} /> : null}
            <AvatarFallback className="text-[9px]">{getInitials(counterpartName)}</AvatarFallback>
          </Avatar>
          <p className="truncate text-xs font-medium text-muted-foreground">{counterpartName}</p>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>
            {amount != null ? `₹${amount.toLocaleString("en-IN")}` : "No budget listed"}
            {" · "}
            {new Date(request.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
          </span>
          <Link
            to={ROUTES.serviceRequestDetail(request.id)}
            className="inline-flex items-center gap-1 font-medium text-primary hover:underline"
          >
            View Request <ArrowRight className="size-3.5" />
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}
