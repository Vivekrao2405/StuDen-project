import { Check, Trophy } from "lucide-react";

import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { CircularScore } from "@/pages/home/CircularScore";

const DEMO_SKILLS = ["React", "JavaScript", "SQL", "Node.js"];

interface DemoProfileCardProps {
  /** "hero": avatar + availability + readiness bar + ring + skills + 3 stats + achievement
   * banner. "identity": avatar + ring + skills + 4 stats (adds Rank). Two spots in the landing
   * page reference reuse the same card with these two configurations. */
  variant: "hero" | "identity";
  className?: string;
}

/**
 * Illustrative product-preview card for the landing page — NOT the visitor's real data. Every
 * value here is fixed demo content (spec: landing page must never fetch real profile/score data
 * just to populate this decoration).
 */
export function DemoProfileCard({ variant, className }: DemoProfileCardProps) {
  const isHero = variant === "hero";

  return (
    <div
      className={
        "rounded-2xl border border-border bg-card p-5 shadow-xl ring-1 ring-black/5 sm:p-6" +
        (className ? ` ${className}` : "")
      }
    >
      <div className="flex items-start justify-between gap-4">
        <div className="flex items-center gap-3">
          <Avatar size="lg">
            <AvatarFallback>VR</AvatarFallback>
          </Avatar>
          <div>
            <p className="font-semibold text-foreground">Vivek Rao</p>
            <p className="text-sm text-muted-foreground">Full Stack Developer</p>
            {isHero ? (
              <span className="mt-1 inline-flex items-center gap-1.5 text-xs font-medium text-primary">
                <span className="size-1.5 rounded-full bg-primary" /> Available
              </span>
            ) : null}
          </div>
        </div>

        <div className="relative flex shrink-0 items-center justify-center">
          <CircularScore value={82} size={72} strokeWidth={6} />
          <div className="absolute flex flex-col items-center leading-none">
            <span className="text-lg font-bold text-foreground">82</span>
            <span className="text-[10px] text-muted-foreground">/100</span>
          </div>
        </div>
      </div>

      {isHero ? (
        <div className="mt-4">
          <p className="text-xs font-medium text-foreground">Placement Readiness</p>
          <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-muted">
            <div className="h-full rounded-full bg-primary" style={{ width: "82%" }} />
          </div>
        </div>
      ) : null}

      <p className="mt-4 text-xs font-semibold text-muted-foreground">{isHero ? "Top Skills" : "Skills"}</p>
      <div className="mt-2 flex flex-wrap gap-1.5">
        {DEMO_SKILLS.map((skill) => (
          <span
            key={skill}
            className="inline-flex items-center gap-1 rounded-full bg-accent px-2.5 py-1 text-xs font-medium text-accent-foreground"
          >
            {skill}
            <Check className="size-3" />
          </span>
        ))}
      </div>

      <div className={`mt-4 grid gap-2 border-t border-border pt-4 text-center ${isHero ? "grid-cols-3" : "grid-cols-4"}`}>
        <div>
          <p className="text-lg font-bold text-foreground">6</p>
          <p className="text-[11px] text-muted-foreground">Projects</p>
        </div>
        <div>
          <p className="text-lg font-bold text-foreground">12</p>
          <p className="text-[11px] text-muted-foreground">Assessments</p>
        </div>
        <div>
          <p className="text-lg font-bold text-foreground">8</p>
          <p className="text-[11px] text-muted-foreground">Badges</p>
        </div>
        {!isHero ? (
          <div>
            <p className="text-lg font-bold text-primary">Top 18%</p>
            <p className="text-[11px] text-muted-foreground">Rank</p>
          </div>
        ) : null}
      </div>

      {isHero ? (
        <div className="mt-4 flex items-center gap-3 rounded-xl bg-slate-950 p-3">
          <Trophy className="size-5 shrink-0 text-amber-400" aria-hidden="true" />
          <div>
            <p className="text-sm font-semibold text-white">Top 8%</p>
            <p className="text-xs text-slate-300">Keep going! You're doing great.</p>
          </div>
        </div>
      ) : null}
    </div>
  );
}
