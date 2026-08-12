import { Briefcase, ChevronRight, FolderKanban, MessageCircle, Sparkles, Trophy } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";

interface QuickLinkRow {
  label: string;
  countLabel: string;
  icon: LucideIcon;
  to: string;
}

interface MobileQuickLinksProps {
  skillsCount: number;
}

/** Mobile-only compact chevron-row list replacing the desktop's full cards below the journey
 * section — keeps every dashboard section reachable in the spec's mobile content order without
 * stacking tall cards on a small screen. */
export function MobileQuickLinks({ skillsCount }: MobileQuickLinksProps) {
  const navigate = useNavigate();

  const rows: QuickLinkRow[] = [
    {
      label: "Your Skills",
      countLabel: skillsCount > 0 ? `${skillsCount} skill${skillsCount === 1 ? "" : "s"}` : "No skills yet",
      icon: Sparkles,
      to: ROUTES.profile,
    },
    { label: "Your Projects", countLabel: "Coming soon", icon: FolderKanban, to: ROUTES.projects },
    { label: "Upcoming Challenges", countLabel: "Coming soon", icon: Trophy, to: ROUTES.challenges },
    { label: "Opportunities for you", countLabel: "Coming soon", icon: Briefcase, to: ROUTES.marketplace },
    { label: "Recent Messages", countLabel: "No messages yet", icon: MessageCircle, to: ROUTES.messages },
  ];

  return (
    <Card>
      <CardContent className="divide-y divide-border p-0">
        {rows.map((row) => (
          <button
            key={row.label}
            type="button"
            onClick={() => navigate(row.to)}
            className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-muted/50"
          >
            <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-accent text-primary">
              <row.icon className="size-4" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-sm font-medium text-foreground">{row.label}</span>
              <span className="block text-xs text-muted-foreground">{row.countLabel}</span>
            </span>
            <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
          </button>
        ))}
      </CardContent>
    </Card>
  );
}
