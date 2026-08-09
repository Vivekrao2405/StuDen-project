import { CategoryCarousel, type CarouselItem } from "@/components/shared/CategoryCarousel";
import { useToast } from "@/hooks/useToast";
import { CATEGORIES } from "@/pages/home/categoriesData";
import { CtaSection } from "@/pages/home/CtaSection";
import { HeroSection } from "@/pages/home/HeroSection";
import { HowItWorksSection } from "@/pages/home/HowItWorksSection";
import { SolvesSection } from "@/pages/home/SolvesSection";

export function HomePage() {
  const toast = useToast();

  function handleSelectCategory(item: CarouselItem) {
    toast.info(`"${item.label}" is coming soon — check back later!`);
  }

  return (
    <>
      <HeroSection />

      <section className="mx-auto max-w-7xl px-4 pb-8 sm:px-6 lg:px-8">
        <h2 className="mb-4 text-lg font-semibold text-foreground">Popular</h2>
        <CategoryCarousel items={CATEGORIES} onSelect={handleSelectCategory} />
      </section>

      <HowItWorksSection />
      <CtaSection />
      <SolvesSection />
    </>
  );
}
