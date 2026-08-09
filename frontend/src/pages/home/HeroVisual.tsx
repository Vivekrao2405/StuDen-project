import { Camera, Code2, Search, UserRound } from "lucide-react";

import { CATEGORIES } from "@/pages/home/categoriesData";

const PREVIEW_CATEGORIES = CATEGORIES.slice(0, 3);

export function HeroVisual() {
  return (
    <div className="relative mx-auto mt-10 w-full max-w-xs sm:max-w-md lg:mt-0 lg:max-w-none">
      {/* Laptop / browser mockup — the primary visual, hidden only on the smallest screens */}
      <div className="hidden sm:block">
        <div className="relative mx-auto max-w-sm rounded-2xl border border-border bg-card p-4 shadow-xl ring-1 ring-black/5 lg:max-w-md">
          <div className="mb-3 flex items-center gap-1.5">
            <span className="size-2 rounded-full bg-muted-foreground/25" />
            <span className="size-2 rounded-full bg-muted-foreground/25" />
            <span className="size-2 rounded-full bg-muted-foreground/25" />
          </div>

          <div className="flex items-center gap-2 rounded-full border border-border bg-muted/40 px-3 py-2">
            <Search className="size-3.5 shrink-0 text-muted-foreground" />
            <span className="text-xs text-muted-foreground">Search for a service...</span>
          </div>

          <p className="mb-2 mt-4 text-xs font-semibold text-foreground">Popular</p>
          <div className="grid grid-cols-3 gap-2">
            {PREVIEW_CATEGORIES.map((category) => {
              const Icon = category.icon;
              return (
                <div
                  key={category.id}
                  className="flex flex-col items-center gap-1.5 rounded-xl border border-border bg-muted/20 py-3"
                >
                  <Icon className="size-4 text-primary" />
                  <span className="text-center text-[10px] font-medium leading-tight text-foreground">
                    {category.label}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Phone / student profile card mockup — always visible; overlaps the laptop from sm+, stands alone on mobile */}
      <div className="relative z-10 mx-auto w-44 sm:absolute sm:-bottom-8 sm:-right-6 sm:mx-0 sm:w-36 lg:w-44">
        <div className="rounded-[1.5rem] border-4 border-foreground bg-card p-3 shadow-2xl">
          <div className="flex items-center gap-2">
            <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-accent">
              <UserRound className="size-4 text-accent-foreground" />
            </div>
            <div className="min-w-0">
              <p className="truncate text-xs font-semibold text-foreground">Priya S.</p>
              <p className="truncate text-[10px] text-muted-foreground">Full-Stack Developer</p>
            </div>
          </div>

          <span className="mt-2 inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-[9px] font-medium text-primary">
            <span className="size-1.5 rounded-full bg-primary" /> Available
          </span>

          <div className="mt-2 flex flex-wrap gap-1">
            <span className="rounded-full bg-muted px-2 py-0.5 text-[9px] text-muted-foreground">Web Dev</span>
            <span className="rounded-full bg-muted px-2 py-0.5 text-[9px] text-muted-foreground">UI Design</span>
          </div>
        </div>
      </div>

      {/* Decorative floating accents — desktop only, anchored to the composition itself */}
      <div className="pointer-events-none absolute -left-6 -top-6 hidden size-12 items-center justify-center rounded-2xl bg-card shadow-lg ring-1 ring-border lg:flex">
        <Code2 className="size-5 text-primary" />
      </div>
      <div className="pointer-events-none absolute -right-4 top-1/3 hidden size-12 items-center justify-center rounded-2xl bg-card shadow-lg ring-1 ring-border lg:flex">
        <Camera className="size-5 text-primary" />
      </div>
    </div>
  );
}
