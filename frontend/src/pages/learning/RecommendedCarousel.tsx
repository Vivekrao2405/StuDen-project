import { ChevronLeft, ChevronRight } from "lucide-react";
import { useRef, useState, type ReactNode } from "react";

import { cn } from "@/lib/utils";

const CARD_SELECTOR = "[data-carousel-item]";

// Matches the reference's horizontal-carousel treatment for "Recommended for You": arrow buttons +
// dot pagination, one snap-scroll row underneath (no external carousel library in this codebase, and
// a single-use case doesn't warrant adding one).
export function RecommendedCarousel({ children, itemCount }: { children: ReactNode; itemCount: number }) {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const [activeIndex, setActiveIndex] = useState(0);

  function stepWidth(el: HTMLDivElement): number {
    const card = el.querySelector<HTMLElement>(CARD_SELECTOR);
    return card ? card.offsetWidth + 16 : el.clientWidth;
  }

  function scrollByCard(direction: 1 | -1) {
    const el = scrollerRef.current;
    if (!el) return;
    el.scrollBy({ left: stepWidth(el) * direction, behavior: "smooth" });
  }

  function handleScroll() {
    const el = scrollerRef.current;
    if (!el) return;
    setActiveIndex(Math.round(el.scrollLeft / stepWidth(el)));
  }

  return (
    <div className="relative">
      <div
        ref={scrollerRef}
        onScroll={handleScroll}
        className="flex snap-x snap-mandatory gap-4 overflow-x-auto scroll-smooth pb-1 [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
      >
        {children}
      </div>

      {itemCount > 1 ? (
        <>
          <button
            type="button"
            onClick={() => scrollByCard(-1)}
            aria-label="Previous resources"
            className="absolute top-1/2 left-0 hidden -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border border-border bg-background p-1.5 text-foreground shadow-sm hover:bg-muted sm:flex"
          >
            <ChevronLeft className="size-4" />
          </button>
          <button
            type="button"
            onClick={() => scrollByCard(1)}
            aria-label="Next resources"
            className="absolute top-1/2 right-0 hidden translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full border border-border bg-background p-1.5 text-foreground shadow-sm hover:bg-muted sm:flex"
          >
            <ChevronRight className="size-4" />
          </button>
          <div className="mt-3 flex items-center justify-center gap-1.5">
            {Array.from({ length: itemCount }).map((_, i) => (
              <span
                key={i}
                className={cn("size-1.5 rounded-full transition-colors", i === activeIndex ? "bg-primary" : "bg-muted")}
              />
            ))}
          </div>
        </>
      ) : null}
    </div>
  );
}
