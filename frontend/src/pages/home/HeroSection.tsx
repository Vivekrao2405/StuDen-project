import { Zap } from "lucide-react";

import { TypingHeadline } from "@/components/shared/TypingHeadline";
import { HeroVisual } from "@/pages/home/HeroVisual";
import { SearchBar } from "@/pages/home/SearchBar";

export function HeroSection() {
  return (
    <section className="mx-auto max-w-7xl px-4 pb-16 pt-12 sm:px-6 sm:pt-16 lg:px-8 lg:pt-20">
      <div className="grid items-center gap-12 lg:grid-cols-2">
        <div>
          <div className="mb-6 inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1.5 text-xs font-medium text-muted-foreground">
            <Zap className="size-3.5 text-primary" />
            Built for Students. Powered by Skills.
          </div>

          <TypingHeadline />

          <p className="mt-6 text-sm font-medium text-foreground">What are you looking for?</p>
          <div className="mt-3">
            <SearchBar />
          </div>
        </div>

        <HeroVisual />
      </div>
    </section>
  );
}
