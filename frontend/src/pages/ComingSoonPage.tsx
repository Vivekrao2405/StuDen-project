import type { LucideIcon } from "lucide-react";

import { EmptyState } from "@/components/shared/EmptyState";

interface ComingSoonPageProps {
  title: string;
  description: string;
  icon: LucideIcon;
}

/**
 * A single honest placeholder for any nav destination that doesn't have a backend yet
 * (Marketplace, Skill Assessments, Challenges, Messages, Notifications, Projects). Every sidebar
 * and journey link stays a real navigation action instead of a dead link — it just lands here
 * until that feature actually exists, rather than showing fabricated data.
 */
export function ComingSoonPage({ title, description, icon }: ComingSoonPageProps) {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">{title}</h1>
      </div>
      <EmptyState icon={icon} title={`${title} is coming soon`} description={description} />
    </div>
  );
}
