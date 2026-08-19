import { Briefcase, Check, ClipboardCheck, ListPlus, Search, SearchX, Sparkles } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import type { EligibilityState } from "@/lib/api/types";
import { listAssessableSkills } from "@/lib/api/endpoints/assessments";
import { listPracticalAssessments } from "@/lib/api/endpoints/practicalAssessments";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { PracticalAssessmentCard } from "@/pages/practical/PracticalAssessmentCard";
import { SkillAssessmentCard } from "@/pages/assessment/SkillAssessmentCard";

type Tab = "knowledge" | "practical";

// Assessment eligibility (skill-visibility fix): a student only ever sees assessments matching
// skills on their own portfolio (see com.studen.portfolio.PortfolioSkillProfileService) — this
// page never falls back to showing every assessment in the catalog. The backend already enforces
// this (GET /assessments/skills and GET /practical-assessments both return an explicit
// EligibilityState), so this component only has to render whichever of the 4 states it's given.
// Reused, same brand-blue tint as the notifications onboarding card elsewhere in the app, so this
// reads as "the same product" rather than a one-off banner style.
function PersonalizationBanner() {
  return (
    <Card size="sm" className="border-primary/20 bg-primary/5">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Sparkles className="size-4 text-primary" /> Assessments are personalized to your skills
        </CardTitle>
        <CardDescription>
          Only assessments for the skills you've added to your portfolio are shown here. Update your portfolio anytime
          to unlock assessments for additional skills.
        </CardDescription>
      </CardHeader>
    </Card>
  );
}

function EligibilityEmptyState({ state, onCreatePortfolio, onUpdatePortfolio }: {
  state: Exclude<EligibilityState, "HAS_AVAILABLE_ASSESSMENTS">;
  onCreatePortfolio: () => void;
  onUpdatePortfolio: () => void;
}) {
  if (state === "NO_PORTFOLIO") {
    return (
      <EmptyState
        icon={Briefcase}
        title="Build your portfolio first"
        description="Your Skill Assessments are personalized based on the skills in your portfolio. Create your portfolio so StuDen can identify your skills and show you the assessments that are relevant to you."
        action={<Button onClick={onCreatePortfolio}>Create My Portfolio</Button>}
      />
    );
  }
  if (state === "NO_SKILLS") {
    return (
      <EmptyState
        icon={ListPlus}
        title="Add your skills to unlock assessments"
        description="Add skills to your portfolio and we'll show assessments relevant to your current skill set."
        action={<Button onClick={onUpdatePortfolio}>Update Portfolio</Button>}
      />
    );
  }
  return (
    <EmptyState
      icon={ClipboardCheck}
      title="You're all set for now"
      description="We don't have an assessment for your current skills yet — check back soon."
      action={
        <Button variant="outline" onClick={onUpdatePortfolio}>
          Update Portfolio
        </Button>
      }
    />
  );
}

// "You're seeing these assessments because these are the skills currently listed in your
// portfolio" — made concrete and scannable (a checklist, not another paragraph) right above the
// results, so personalization reads as intentional rather than a bug ("why only 3 assessments?").
function AssessedSkillsBar({ skillNames, onUpdatePortfolio }: { skillNames: string[]; onUpdatePortfolio: () => void }) {
  if (skillNames.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-muted/30 px-4 py-3">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-sm">
        <span className="text-muted-foreground">Your assessed skills:</span>
        {skillNames.map((name) => (
          <span key={name} className="inline-flex items-center gap-1 font-medium text-foreground">
            {name} <Check className="size-3.5 text-primary" />
          </span>
        ))}
      </div>
      <button type="button" onClick={onUpdatePortfolio} className="text-sm font-medium text-primary hover:underline">
        Update your portfolio →
      </button>
    </div>
  );
}

export function SkillAssessmentsPage() {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>("knowledge");
  const [query, setQuery] = useState("");

  const knowledge = useAsync(listAssessableSkills, []);
  const practical = useAsync(() => listPracticalAssessments({ size: 50 }), []);

  const filteredKnowledge = useMemo(() => {
    if (!knowledge.data) return [];
    const term = query.trim().toLowerCase();
    if (!term) return knowledge.data.skills;
    return knowledge.data.skills.filter((skill) => skill.name.toLowerCase().includes(term) || skill.category.toLowerCase().includes(term));
  }, [knowledge.data, query]);

  const filteredPractical = useMemo(() => {
    if (!practical.data) return [];
    const term = query.trim().toLowerCase();
    if (!term) return practical.data.page.content;
    return practical.data.page.content.filter(
      (a) => a.title.toLowerCase().includes(term) || a.skillName.toLowerCase().includes(term)
    );
  }, [practical.data, query]);

  function goToPortfolio() {
    navigate(ROUTES.profile);
  }

  const knowledgeSkillNames = useMemo(() => knowledge.data?.skills.map((s) => s.name) ?? [], [knowledge.data]);
  const practicalSkillNames = useMemo(
    () => Array.from(new Set((practical.data?.page.content ?? []).map((a) => a.skillName))),
    [practical.data]
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Skill Assessments</h1>
        <p className="text-sm text-muted-foreground">Prove your skills with knowledge and practical assessments.</p>
      </div>

      <PersonalizationBanner />

      <SegmentedControl
        value={tab}
        onChange={setTab}
        options={[
          { value: "knowledge", label: "Knowledge" },
          { value: "practical", label: "Practical" },
        ]}
      />

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={tab === "knowledge" ? "Search skills..." : "Search practical assessments..."}
          className="h-11 rounded-full pl-10"
        />
      </div>

      {tab === "knowledge" ? (
        knowledge.loading ? (
          <LoadingState label="Loading assessments..." />
        ) : knowledge.error ? (
          <ErrorState message={knowledge.error.message} onRetry={knowledge.refetch} />
        ) : knowledge.data && knowledge.data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
          <EligibilityEmptyState state={knowledge.data.state} onCreatePortfolio={goToPortfolio} onUpdatePortfolio={goToPortfolio} />
        ) : (
          <div className="space-y-4">
            <AssessedSkillsBar skillNames={knowledgeSkillNames} onUpdatePortfolio={goToPortfolio} />
            {filteredKnowledge.length > 0 ? (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
                {filteredKnowledge.map((skill) => (
                  <SkillAssessmentCard key={skill.skillId} skill={skill} />
                ))}
              </div>
            ) : (
              <EmptyState icon={SearchX} title="No matching skills" description="Try a different search term." />
            )}
          </div>
        )
      ) : practical.loading ? (
        <LoadingState label="Loading practical assessments..." />
      ) : practical.error ? (
        <ErrorState message={practical.error.message} onRetry={practical.refetch} />
      ) : practical.data && practical.data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
        <EligibilityEmptyState state={practical.data.state} onCreatePortfolio={goToPortfolio} onUpdatePortfolio={goToPortfolio} />
      ) : (
        <div className="space-y-4">
          <AssessedSkillsBar skillNames={practicalSkillNames} onUpdatePortfolio={goToPortfolio} />
          {filteredPractical.length > 0 ? (
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
              {filteredPractical.map((assessment) => (
                <PracticalAssessmentCard key={assessment.id} assessment={assessment} />
              ))}
            </div>
          ) : (
            <EmptyState icon={SearchX} title="No matching assessments" description="Try a different search term." />
          )}
        </div>
      )}
    </div>
  );
}
