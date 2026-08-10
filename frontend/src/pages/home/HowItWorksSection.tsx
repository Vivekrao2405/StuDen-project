import { CheckCircle2, MessageCircle, Search, UserRound } from "lucide-react";

import { BrandName } from "@/components/shared/BrandName";

const STEPS = [
  {
    number: "01",
    icon: Search,
    title: "Find what you need",
    description: "Search for a skill or service from students.",
  },
  {
    number: "02",
    icon: UserRound,
    title: "Explore student profiles",
    description: "See their skills, portfolio and experience.",
  },
  {
    number: "03",
    icon: MessageCircle,
    title: "Connect & discuss",
    description: "Send a request and discuss the details with the student.",
  },
  {
    number: "04",
    icon: CheckCircle2,
    title: "Get it done",
    description: "Work together and get your task completed.",
  },
];

export function HowItWorksSection() {
  return (
    <section id="how-it-works" className="border-t border-border bg-muted/20 py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 className="text-center text-2xl font-bold text-foreground sm:text-3xl">
          How <BrandName /> works
        </h2>

        <div className="mt-12 grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {STEPS.map((step) => {
            const Icon = step.icon;
            return (
              <div key={step.number} className="flex flex-col items-center text-center">
                <div className="flex size-16 items-center justify-center rounded-full bg-card ring-1 ring-border">
                  <Icon className="size-6 text-primary" />
                </div>
                <span className="mt-3 text-sm font-semibold text-primary">{step.number}</span>
                <h3 className="mt-1 font-semibold text-foreground">{step.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{step.description}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
