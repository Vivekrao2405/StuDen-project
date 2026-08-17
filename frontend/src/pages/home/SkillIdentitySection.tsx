import { ArrowRight, CheckCircle2 } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";
import { DemoProfileCard } from "@/pages/home/DemoProfileCard";

const POINTS = [
  "Showcase your verified skills",
  "Highlight projects & achievements",
  "Track your progress over time",
  "Build your professional identity",
];

export function SkillIdentitySection() {
  const { status } = useAuth();
  const ctaTo = status === "authenticated" ? ROUTES.profile : ROUTES.register;

  return (
    <section className="border-t border-border py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="grid items-center gap-10 lg:grid-cols-2 lg:gap-16">
          <div>
            <h2 className="text-2xl font-bold text-foreground sm:text-3xl">
              Don't just list your skills.
              <br />
              Prove them.
            </h2>

            <ul className="mt-6 space-y-3">
              {POINTS.map((point) => (
                <li key={point} className="flex items-center gap-2.5 text-sm text-foreground sm:text-base">
                  <CheckCircle2 className="size-5 shrink-0 text-primary" />
                  {point}
                </li>
              ))}
            </ul>

            <Button size="lg" className="mt-8 h-11 rounded-full px-6" render={<Link to={ctaTo} />}>
              Create Your Skill Identity <ArrowRight className="size-4" />
            </Button>
          </div>

          <div className="mx-auto w-full max-w-sm lg:max-w-md">
            <DemoProfileCard variant="identity" />
          </div>
        </div>
      </div>
    </section>
  );
}
