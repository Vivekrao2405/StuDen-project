import { Briefcase } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

export function CtaSection() {
  const { status } = useAuth();

  return (
    <section id="cta-become-freelancer" className="py-16 sm:py-20">
      <div className="mx-auto max-w-5xl px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col items-center gap-6 rounded-2xl bg-primary/5 p-8 text-center ring-1 ring-primary/10 sm:p-12">
          <div className="flex size-14 items-center justify-center rounded-full bg-primary">
            <Briefcase className="size-6 text-primary-foreground" />
          </div>
          <div>
            <h2 className="text-2xl font-bold text-foreground sm:text-3xl">Have a skill to offer?</h2>
            <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground sm:text-base">
              Create your profile. Showcase your work. Start earning from your skills.
            </p>
          </div>
          <Button
            size="lg"
            className="h-11 rounded-full px-8"
            render={<Link to={status === "authenticated" ? ROUTES.profile : ROUTES.register} />}
          >
            Start earning
          </Button>
        </div>
      </div>
    </section>
  );
}
