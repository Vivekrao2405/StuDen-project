import { Briefcase, ClipboardCheck, ListPlus, Search, SearchX } from "lucide-react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
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
function EligibilityEmptyState({ state, onCreatePortfolio, onUpdatePortfolio }: {
  state: Exclude<EligibilityState, "HAS_AVAILABLE_ASSESSMENTS">;
  onCreatePortfolio: () => void;
  onUpdatePortfolio: () => void;
}) {
  if (state === "NO_PORTFOLIO") {
    return (
      <EmptyState
        icon={Briefcase}
        title="Create your portfolio first"
        description="Your portfolio helps StuDen understand your current skills and recommend the right assessments."
        action={<Button onClick={onCreatePortfolio}>Create My Portfolio</Button>}
      />
    );
  }
  if (state === "NO_SKILLS") {
    return (
      <EmptyState
        icon={ListPlus}
        title="We couldn't identify any skills yet"
        description="Add skills to your portfolio to unlock personalized assessments."
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
          Build more skills
        </Button>
      }
    />
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

  const knowledgeSkillNames = knowledge.data?.skills.map((s) => s.name) ?? [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Skill Assessments</h1>
        <p className="text-sm text-muted-foreground">
          {knowledge.data?.state === "HAS_AVAILABLE_ASSESSMENTS" || practical.data?.state === "HAS_AVAILABLE_ASSESSMENTS"
            ? "Assessments for your skills."
            : "Prove your skills with knowledge and practical assessments."}
        </p>
        {tab === "knowledge" && knowledgeSkillNames.length > 0 ? (
          <p className="mt-1 text-xs text-muted-foreground">Your skills: {knowledgeSkillNames.join(", ")}</p>
        ) : null}
      </div>

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
        ) : filteredKnowledge.length > 0 ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {filteredKnowledge.map((skill) => (
              <SkillAssessmentCard key={skill.skillId} skill={skill} />
            ))}
          </div>
        ) : (
          <EmptyState icon={SearchX} title="No matching skills" description="Try a different search term." />
        )
      ) : practical.loading ? (
        <LoadingState label="Loading practical assessments..." />
      ) : practical.error ? (
        <ErrorState message={practical.error.message} onRetry={practical.refetch} />
      ) : practical.data && practical.data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
        <EligibilityEmptyState state={practical.data.state} onCreatePortfolio={goToPortfolio} onUpdatePortfolio={goToPortfolio} />
      ) : filteredPractical.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filteredPractical.map((assessment) => (
            <PracticalAssessmentCard key={assessment.id} assessment={assessment} />
          ))}
        </div>
      ) : (
        <EmptyState icon={SearchX} title="No matching assessments" description="Try a different search term." />
      )}
    </div>
  );
}
