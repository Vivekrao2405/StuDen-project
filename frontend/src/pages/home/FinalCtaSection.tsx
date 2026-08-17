import { ArrowRight } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useAuth } from "@/features/auth/useAuth";
import { BrandName } from "@/components/shared/BrandName";
import { ROUTES } from "@/lib/routes";

export function FinalCtaSection() {
  const { status } = useAuth();
  const ctaTo = status === "authenticated" ? ROUTES.profile : ROUTES.register;

  return (
    <section className="py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="flex flex-col items-center gap-6 rounded-3xl bg-slate-950 p-8 text-center sm:p-14">
          <h2 className="text-2xl font-bold text-white sm:text-3xl">
            Start building your future with <BrandName />
          </h2>
          <p className="text-sm text-slate-300 sm:text-base">One platform. Endless opportunities.</p>
          <Button size="lg" className="h-11 rounded-full px-8" render={<Link to={ctaTo} />}>
            Get Started Free <ArrowRight className="size-4" />
          </Button>
        </div>
      </div>
    </section>
  );
}
