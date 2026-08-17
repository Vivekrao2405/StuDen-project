import { ClipboardCheck, FolderKanban, LineChart, Store } from "lucide-react";

const FEATURES = [
  {
    icon: ClipboardCheck,
    title: "Assess",
    description: "Test your knowledge and track your performance.",
    tint: "bg-blue-500/10 text-blue-600 dark:text-blue-400",
  },
  {
    icon: FolderKanban,
    title: "Showcase",
    description: "Show your projects and skills to the world.",
    tint: "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400",
  },
  {
    icon: Store,
    title: "Offer",
    description: "Provide services and get paid for your skills.",
    tint: "bg-violet-500/10 text-violet-600 dark:text-violet-400",
  },
  {
    icon: LineChart,
    title: "Grow",
    description: "Identify gaps and keep improving every day.",
    tint: "bg-amber-500/10 text-amber-600 dark:text-amber-400",
  },
];

export function PlatformFeaturesSection() {
  return (
    <section className="border-t border-border py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 className="text-center text-2xl font-bold text-foreground sm:text-3xl">
          Everything you build, in one place
        </h2>

        <div className="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((feature) => {
            const Icon = feature.icon;
            return (
              <div key={feature.title} className="rounded-xl border border-border bg-card p-5">
                <div className={`flex size-11 items-center justify-center rounded-xl ${feature.tint}`}>
                  <Icon className="size-5" />
                </div>
                <h3 className="mt-4 text-sm font-semibold tracking-wide text-foreground uppercase">
                  {feature.title}
                </h3>
                <p className="mt-1 text-sm text-muted-foreground">{feature.description}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
