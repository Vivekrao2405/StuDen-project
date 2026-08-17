import { ArrowRight, Star, TrendingUp, Trophy } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";
import { CircularScore } from "@/pages/home/CircularScore";

const SKILLS = [
  { label: "DSA", value: 68 },
  { label: "SQL", value: 84 },
  { label: "Aptitude", value: 73 },
  { label: "Java", value: 81 },
];

const CHIPS = [
  { icon: Trophy, label: "Your Rank: Top 18%" },
  { icon: Star, label: "Strong in SQL" },
  { icon: TrendingUp, label: "Improve DSA" },
];

/**
 * Self-contained dark card — pairs with SkillsGridSection in a two-column row (see HomePage.tsx).
 * Illustrative demo scores throughout — not the visitor's real assessment data.
 */
export function PlacementReadinessSection() {
  return (
    <div className="flex flex-col rounded-3xl bg-slate-950 p-6 sm:p-8">
      <p className="text-xs font-semibold tracking-wide text-primary uppercase">Placement Readiness</p>
      <h2 className="mt-2 text-2xl font-bold text-white sm:text-3xl">How ready are you?</h2>
      <p className="mt-3 max-w-md text-sm text-slate-300">
        Take a free assessment and discover your strengths and gaps.
      </p>

      <div className="mt-6 flex flex-wrap items-center gap-6">
        <div className="relative flex shrink-0 items-center justify-center">
          <CircularScore value={72} size={112} strokeWidth={9} trackClassName="stroke-white/10" />
          <div className="absolute flex flex-col items-center leading-none">
            <span className="text-2xl font-bold text-white">72</span>
            <span className="text-xs text-slate-400">/100</span>
          </div>
        </div>

        <div className="min-w-48 flex-1 space-y-2.5">
          {SKILLS.map((skill) => (
            <div key={skill.label}>
              <div className="flex items-center justify-between text-xs text-slate-300">
                <span>{skill.label}</span>
                <span className="font-medium text-white">{skill.value}%</span>
              </div>
              <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-white/10">
                <div className="h-full rounded-full bg-primary" style={{ width: `${skill.value}%` }} />
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-6 flex flex-wrap gap-2">
        {CHIPS.map((chip) => {
          const Icon = chip.icon;
          return (
            <span
              key={chip.label}
              className="inline-flex items-center gap-1.5 rounded-full bg-white/10 px-3 py-1 text-xs font-medium text-white ring-1 ring-white/15"
            >
              <Icon className="size-3.5 text-primary" /> {chip.label}
            </span>
          );
        })}
      </div>

      <Button size="lg" className="mt-8 h-11 w-fit rounded-full px-6" render={<Link to={ROUTES.skillAssessments} />}>
        Take Free Assessment <ArrowRight className="size-4" />
      </Button>
    </div>
  );
}
