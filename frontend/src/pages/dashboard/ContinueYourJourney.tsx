import { BarChart3, Check, ChevronRight, GraduationCap, Plus, Trophy } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

interface JourneyTile {
  key: string;
  icon: LucideIcon;
  iconBg: string;
  iconColor: string;
  title: string;
  doneLabel?: string;
  subtitle: string;
  done: boolean;
  comingSoon: boolean;
  to: string;
}

interface ContinueYourJourneyProps {
  hasEducation: boolean;
}

/** Every tile is a real navigation action. "Add your education" is the one real, completable
 * item today — the other three route to honest coming-soon pages (Projects, Skill Assessments
 * and Challenges have no backend yet) and are tagged accordingly rather than ever showing a
 * false "done" state. */
export function ContinueYourJourney({ hasEducation }: ContinueYourJourneyProps) {
  const navigate = useNavigate();

  const tiles: JourneyTile[] = [
    {
      key: "project",
      icon: Plus,
      iconBg: "bg-accent",
      iconColor: "text-primary",
      title: "Add your first project",
      subtitle: "Showcase what you've built",
      done: false,
      comingSoon: true,
      to: ROUTES.projects,
    },
    {
      key: "assessment",
      icon: BarChart3,
      iconBg: "bg-emerald-500/10",
      iconColor: "text-emerald-600",
      title: "Take a skill assessment",
      subtitle: "Prove your skills",
      done: false,
      comingSoon: true,
      to: ROUTES.skillAssessments,
    },
    {
      key: "education",
      icon: GraduationCap,
      iconBg: "bg-violet-500/10",
      iconColor: "text-violet-600",
      title: "Add your education",
      doneLabel: "Education added",
      subtitle: "Help others know you better",
      done: hasEducation,
      comingSoon: false,
      to: ROUTES.profile,
    },
    {
      key: "challenge",
      icon: Trophy,
      iconBg: "bg-amber-500/10",
      iconColor: "text-amber-600",
      title: "Join a challenge",
      subtitle: "Win rewards and build skills",
      done: false,
      comingSoon: true,
      to: ROUTES.challenges,
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Continue your journey</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {tiles.map((tile) => (
            <button
              key={tile.key}
              type="button"
              onClick={() => navigate(tile.to)}
              className="flex items-center gap-3 rounded-lg border border-border p-3 text-left transition-colors hover:bg-muted/50"
            >
              <span
                className={cn(
                  "flex size-9 shrink-0 items-center justify-center rounded-lg",
                  tile.done ? "bg-primary/10" : tile.iconBg
                )}
              >
                {tile.done ? (
                  <Check className="size-4 text-primary" />
                ) : (
                  <tile.icon className={cn("size-4", tile.iconColor)} />
                )}
              </span>
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-center gap-x-1.5 gap-y-0.5">
                  <span className="text-sm font-medium whitespace-normal break-words text-foreground">
                    {tile.done ? tile.doneLabel : tile.title}
                  </span>
                  {tile.comingSoon ? (
                    <span className="shrink-0 rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                      Soon
                    </span>
                  ) : null}
                </span>
                {!tile.done ? (
                  <span className="block text-xs whitespace-normal break-words text-muted-foreground">
                    {tile.subtitle}
                  </span>
                ) : null}
              </span>
              <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
            </button>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
