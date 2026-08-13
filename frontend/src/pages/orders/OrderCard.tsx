import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import type { OrderResponse } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";
import { orderStatusLabel, orderStatusVariant } from "@/pages/orders/orderStatusBadge";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

interface OrderCardProps {
  order: OrderResponse;
  perspective: "requester" | "provider";
}

export function OrderCard({ order, perspective }: OrderCardProps) {
  const counterpartName = perspective === "requester" ? order.providerName : order.requesterName;
  const counterpartImage = perspective === "requester" ? order.providerProfileImageUrl : order.requesterProfileImageUrl;
  const amount = order.proposedBudget ?? order.servicePriceAmount;

  return (
    <Card>
      <CardContent className="flex flex-col gap-3 pt-4">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-sm font-semibold text-foreground">{order.serviceTitle}</h3>
          <Badge variant={orderStatusVariant(order.status)}>{orderStatusLabel(order.status)}</Badge>
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
            {new Date(order.createdAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
          </span>
          <Link
            to={ROUTES.orderDetail(order.id)}
            className="inline-flex items-center gap-1 font-medium text-primary hover:underline"
          >
            View Order <ArrowRight className="size-3.5" />
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}
