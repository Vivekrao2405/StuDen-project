import { ArrowRight, Zap } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";
import { DemoProfileCard } from "@/pages/home/DemoProfileCard";

export function HeroSection() {
  const { status } = useAuth();
  const getStartedTo = status === "authenticated" ? ROUTES.profile : ROUTES.register;

  return (
    <section className="mx-auto max-w-7xl px-4 pb-16 pt-12 sm:px-6 sm:pt-16 lg:px-8 lg:pt-20">
      <div className="grid items-center gap-12 lg:grid-cols-2 lg:gap-16">
        <div>
          <div className="mb-6 inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground">
            <Zap className="size-3.5 text-primary" />
            Built for Students. <span className="text-primary">Powered by Skills.</span>
          </div>

          <h1 className="text-4xl font-bold tracking-tight text-foreground sm:text-5xl lg:text-6xl">
            Build skills.
            <br />
            Prove yourself.
            <br />
            Shape your <span className="text-primary">future.</span>
          </h1>

          <p className="mt-6 max-w-md text-base text-muted-foreground sm:text-lg">
            Prepare for placements, showcase your skills and discover opportunities — all in one
            place.
          </p>

          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Button size="lg" className="h-11 rounded-full px-6" render={<Link to={getStartedTo} />}>
              Get Started Free <ArrowRight className="size-4" />
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="h-11 rounded-full px-6"
              render={<a href="#goals" />}
            >
              Explore StuDen <ArrowRight className="size-4" />
            </Button>
          </div>
        </div>

        <div className="animate-float mx-auto w-full max-w-sm lg:max-w-md">
          <DemoProfileCard variant="hero" />
        </div>
      </div>
    </section>
  );
}
