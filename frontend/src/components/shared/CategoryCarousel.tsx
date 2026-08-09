import { ChevronLeft, ChevronRight, type LucideIcon } from "lucide-react";
import { useRef } from "react";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

export interface CarouselItem {
  id: string;
  label: string;
  icon: LucideIcon;
}

interface CategoryCarouselProps {
  items: CarouselItem[];
  onSelect?: (item: CarouselItem) => void;
  className?: string;
}

export function CategoryCarousel({ items, onSelect, className }: CategoryCarouselProps) {
  const scrollerRef = useRef<HTMLDivElement>(null);

  function scrollByAmount(amount: number) {
    scrollerRef.current?.scrollBy({ left: amount, behavior: "smooth" });
  }

  return (
    <div className={cn("relative", className)}>
      <Button
        variant="outline"
        size="icon"
        aria-label="Scroll categories left"
        onClick={() => scrollByAmount(-320)}
        className="absolute -left-4 top-1/2 z-10 hidden -translate-y-1/2 rounded-full bg-background shadow-md md:flex"
      >
        <ChevronLeft />
      </Button>

      <div
        ref={scrollerRef}
        className="no-scrollbar flex snap-x snap-mandatory gap-3 overflow-x-auto scroll-smooth"
        style={{ touchAction: "pan-x", overscrollBehaviorX: "contain" }}
      >
        {items.map((item) => {
          const Icon = item.icon;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => onSelect?.(item)}
              className="flex flex-none snap-start items-center gap-2 rounded-full border border-border bg-card px-4 py-2.5 text-sm font-medium text-foreground transition-colors hover:border-primary/40 hover:bg-accent hover:text-accent-foreground"
            >
              <Icon className="size-4 text-primary" />
              {item.label}
            </button>
          );
        })}
      </div>

      <Button
        variant="outline"
        size="icon"
        aria-label="Scroll categories right"
        onClick={() => scrollByAmount(320)}
        className="absolute -right-4 top-1/2 z-10 hidden -translate-y-1/2 rounded-full bg-background shadow-md md:flex"
      >
        <ChevronRight />
      </Button>
    </div>
  );
}
