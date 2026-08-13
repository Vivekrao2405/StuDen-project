import { Briefcase, Clock, Pencil, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SkillChip } from "@/components/shared/SkillChip";
import type { ServiceCurrency, ServiceStatus, SkillResponse } from "@/lib/api/types";
import { cloudinaryThumb } from "@/lib/cloudinary";

export interface ServiceCardData {
  id: string;
  title: string;
  coverImageUrl: string | null;
  skills: SkillResponse[];
  status: ServiceStatus;
  available: boolean;
  priceAmount: number | null;
  currency: ServiceCurrency;
  deliveryDays: number | null;
}

interface ServiceCardProps {
  service: ServiceCardData;
  onEdit?: () => void;
  onDelete?: () => void;
}

function statusBadge(status: ServiceStatus) {
  if (status === "DRAFT") return <Badge variant="secondary">Draft</Badge>;
  if (status === "INACTIVE") return <Badge variant="secondary">Inactive</Badge>;
  return null;
}

export function ServiceCard({ service, onEdit, onDelete }: ServiceCardProps) {
  const thumb = cloudinaryThumb(service.coverImageUrl, 400);

  return (
    <div className="flex flex-col overflow-hidden rounded-xl border border-border bg-card">
      <div className="relative aspect-[4/3] w-full bg-muted">
        {thumb ? (
          <img src={thumb} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <Briefcase className="size-8 text-muted-foreground" />
          </div>
        )}
        <div className="absolute top-2 left-2 flex flex-wrap gap-1.5">
          {statusBadge(service.status)}
          {service.status === "ACTIVE" && !service.available ? (
            <Badge variant="secondary">Currently unavailable</Badge>
          ) : null}
        </div>
      </div>

      <div className="space-y-1 p-3 pb-0">
        <h3 className="line-clamp-1 font-medium text-foreground">{service.title}</h3>
        {service.priceAmount != null || service.deliveryDays != null ? (
          <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
            {service.priceAmount != null ? (
              <span className="font-semibold text-foreground">₹{service.priceAmount.toLocaleString("en-IN")}</span>
            ) : null}
            {service.deliveryDays != null ? (
              <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                <Clock className="size-3.5" /> {service.deliveryDays}d
              </span>
            ) : null}
          </div>
        ) : null}
      </div>

      <div className="flex flex-1 flex-col gap-2 p-3 pt-2">
        {service.skills.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {service.skills.slice(0, 4).map((skill) => (
              <SkillChip key={skill.id} skill={skill} variant="compact" />
            ))}
          </div>
        ) : null}

        {onEdit || onDelete ? (
          <div className="mt-auto flex justify-end gap-1 pt-1">
            {onEdit ? (
              <Button variant="ghost" size="icon-sm" aria-label="Edit service" onClick={onEdit}>
                <Pencil />
              </Button>
            ) : null}
            {onDelete ? (
              <Button variant="ghost" size="icon-sm" aria-label="Delete service" onClick={onDelete}>
                <Trash2 />
              </Button>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}
