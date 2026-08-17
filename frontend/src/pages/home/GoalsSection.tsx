import { Briefcase, IndianRupee, ShieldCheck, Target } from "lucide-react";

const GOALS = [
  {
    icon: Target,
    title: "Get Placement Ready",
    description: "Practice DSA, SQL, Aptitude and more.",
  },
  {
    icon: ShieldCheck,
    title: "Prove My Skills",
    description: "Take assessments and earn verified badges.",
  },
  {
    icon: Briefcase,
    title: "Build My Portfolio",
    description: "Showcase projects, skills and achievements.",
  },
  {
    icon: IndianRupee,
    title: "Earn From My Skills",
    description: "Offer services and connect with students.",
  },
];

export function GoalsSection() {
  return (
    <section id="goals" className="border-t border-border py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 className="text-2xl font-bold text-foreground sm:text-3xl">What do you want to achieve?</h2>

        <div className="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {GOALS.map((goal) => {
            const Icon = goal.icon;
            return (
              <div
                key={goal.title}
                className="rounded-xl border border-border bg-card p-5 transition-all hover:-translate-y-0.5 hover:shadow-md hover:ring-1 hover:ring-primary/20"
              >
                <div className="flex size-11 items-center justify-center rounded-full bg-accent">
                  <Icon className="size-5 text-primary" />
                </div>
                <h3 className="mt-4 font-semibold text-foreground">{goal.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{goal.description}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
