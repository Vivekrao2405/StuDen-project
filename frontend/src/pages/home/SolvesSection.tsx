import { Clock3, ShieldCheck, UserSearch, Wallet } from "lucide-react";

const PROBLEMS = [
  {
    icon: ShieldCheck,
    title: "Hard to find trusted talent?",
    description: "Find students with real skills and verified portfolios.",
  },
  {
    icon: Wallet,
    title: "Need to earn on your terms?",
    description: "Offer your skills and choose your opportunities.",
  },
  {
    icon: UserSearch,
    title: "Want to know who you hire?",
    description: "Explore student profiles, skills and previous work.",
  },
  {
    icon: Clock3,
    title: "No time to waste?",
    description: "Search, connect and get work done quickly.",
  },
];

export function SolvesSection() {
  return (
    <section className="border-t border-border py-16 sm:py-20">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 className="text-center text-2xl font-bold text-foreground sm:text-3xl">What StuDen solves</h2>

        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {PROBLEMS.map((problem) => {
            const Icon = problem.icon;
            return (
              <div
                key={problem.title}
                className="rounded-xl border border-border bg-card p-5 transition-colors hover:border-primary/30"
              >
                <div className="flex size-10 items-center justify-center rounded-full bg-accent">
                  <Icon className="size-5 text-accent-foreground" />
                </div>
                <h3 className="mt-3 font-semibold text-foreground">{problem.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{problem.description}</p>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
