import { FinalCtaSection } from "@/pages/home/FinalCtaSection";
import { GoalsSection } from "@/pages/home/GoalsSection";
import { HeroSection } from "@/pages/home/HeroSection";
import { PlacementReadinessSection } from "@/pages/home/PlacementReadinessSection";
import { PlatformFeaturesSection } from "@/pages/home/PlatformFeaturesSection";
import { SkillIdentitySection } from "@/pages/home/SkillIdentitySection";
import { SkillsGridSection } from "@/pages/home/SkillsGridSection";

export function HomePage() {
  return (
    <>
      <HeroSection />
      <GoalsSection />

      <section className="border-t border-border py-16 sm:py-20">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="grid gap-6 lg:grid-cols-2 lg:items-stretch">
            <PlacementReadinessSection />
            <SkillsGridSection />
          </div>
        </div>
      </section>

      <SkillIdentitySection />
      <PlatformFeaturesSection />
      <FinalCtaSection />
    </>
  );
}
