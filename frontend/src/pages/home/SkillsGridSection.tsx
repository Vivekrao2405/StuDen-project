import { Braces, Code2, Database, FileCode2, Palette, Sparkles } from "lucide-react";

const SKILLS = [
  { icon: Braces, name: "DSA", description: "Problem solving & algorithms" },
  { icon: Database, name: "SQL", description: "Queries, joins & data analysis" },
  { icon: Code2, name: "Python", description: "Programming fundamentals" },
  { icon: FileCode2, name: "Java", description: "Object-oriented programming" },
  { icon: Sparkles, name: "React", description: "Build modern user interfaces" },
  { icon: Palette, name: "UI/UX", description: "Design beautiful experiences" },
];

/** Pairs with PlacementReadinessSection in a two-column row (see HomePage.tsx). */
export function SkillsGridSection() {
  return (
    <div className="flex flex-col rounded-3xl border border-border bg-card p-6 sm:p-8">
      <h2 className="text-2xl font-bold text-foreground sm:text-3xl">Build skills that matter</h2>

      <div className="mt-6 grid flex-1 grid-cols-2 gap-3 sm:grid-cols-3">
        {SKILLS.map((skill) => {
          const Icon = skill.icon;
          return (
            <div
              key={skill.name}
              className="rounded-xl border border-border bg-background p-4 transition-colors hover:border-primary/30"
            >
              <div className="flex size-9 items-center justify-center rounded-lg bg-accent">
                <Icon className="size-4 text-primary" />
              </div>
              <h3 className="mt-3 text-sm font-semibold text-foreground">{skill.name}</h3>
              <p className="mt-0.5 text-xs leading-snug text-muted-foreground">{skill.description}</p>
            </div>
          );
        })}
      </div>
    </div>
  );
}
