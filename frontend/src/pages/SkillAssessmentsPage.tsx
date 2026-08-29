import { ClipboardCheck, Search, SearchX } from "lucide-react";
import { useMemo, useState } from "react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Input } from "@/components/ui/input";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { listAssessableSkills } from "@/lib/api/endpoints/assessments";
import { listPracticalAssessments } from "@/lib/api/endpoints/practicalAssessments";
import { useAsync } from "@/lib/hooks/useAsync";
import { cn } from "@/lib/utils";
import { PracticalAssessmentCard } from "@/pages/practical/PracticalAssessmentCard";
import { SkillAssessmentCard } from "@/pages/assessment/SkillAssessmentCard";

type Tab = "knowledge" | "practical";

const ALL_CATEGORY = "All";

// Categories come straight from the existing skill `category` field (see AssessableSkillResponse/
// PracticalAssessmentSummaryResponse) — no separate category system to keep in sync. Options are
// derived from whatever is actually published rather than a hardcoded list, so a chip never leads
// to a guaranteed-empty result.
function CategoryFilterBar({
  categories,
  selected,
  onSelect,
}: {
  categories: string[];
  selected: string;
  onSelect: (category: string) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {[ALL_CATEGORY, ...categories].map((category) => (
        <button
          key={category}
          type="button"
          onClick={() => onSelect(category)}
          className={cn(
            "rounded-full border px-4 py-1.5 text-sm font-medium whitespace-nowrap transition-colors",
            selected === category
              ? "border-primary bg-primary text-primary-foreground"
              : "border-border bg-background text-muted-foreground hover:text-foreground"
          )}
        >
          {category}
        </button>
      ))}
    </div>
  );
}

export function SkillAssessmentsPage() {
  const [tab, setTab] = useState<Tab>("knowledge");
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState(ALL_CATEGORY);

  const knowledge = useAsync(listAssessableSkills, []);
  const practical = useAsync(() => listPracticalAssessments({ size: 50 }), []);

  function changeTab(next: Tab) {
    setTab(next);
    setCategory(ALL_CATEGORY);
  }

  const knowledgeCategories = useMemo(
    () => Array.from(new Set((knowledge.data?.skills ?? []).map((skill) => skill.category))).sort(),
    [knowledge.data]
  );
  const practicalCategories = useMemo(
    () => Array.from(new Set((practical.data?.page.content ?? []).map((assessment) => assessment.category))).sort(),
    [practical.data]
  );

  const filteredKnowledge = useMemo(() => {
    if (!knowledge.data) return [];
    const term = query.trim().toLowerCase();
    return knowledge.data.skills.filter((skill) => {
      const matchesTerm = !term || skill.name.toLowerCase().includes(term) || skill.category.toLowerCase().includes(term);
      const matchesCategory = category === ALL_CATEGORY || skill.category === category;
      return matchesTerm && matchesCategory;
    });
  }, [knowledge.data, query, category]);

  const filteredPractical = useMemo(() => {
    if (!practical.data) return [];
    const term = query.trim().toLowerCase();
    return practical.data.page.content.filter((assessment) => {
      const matchesTerm =
        !term || assessment.title.toLowerCase().includes(term) || assessment.skillName.toLowerCase().includes(term);
      const matchesCategory = category === ALL_CATEGORY || assessment.category === category;
      return matchesTerm && matchesCategory;
    });
  }, [practical.data, query, category]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Skill Assessments</h1>
        <p className="text-sm text-muted-foreground">Prove your skills with knowledge and practical assessments.</p>
      </div>

      <SegmentedControl
        value={tab}
        onChange={changeTab}
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

      <CategoryFilterBar
        categories={tab === "knowledge" ? knowledgeCategories : practicalCategories}
        selected={category}
        onSelect={setCategory}
      />

      {tab === "knowledge" ? (
        knowledge.loading ? (
          <LoadingState label="Loading assessments..." />
        ) : knowledge.error ? (
          <ErrorState message={knowledge.error.message} onRetry={knowledge.refetch} />
        ) : knowledge.data && knowledge.data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
          <EmptyState
            icon={ClipboardCheck}
            title="No assessments published yet"
            description="Check back soon — new skill assessments are added regularly."
          />
        ) : filteredKnowledge.length > 0 ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {filteredKnowledge.map((skill) => (
              <SkillAssessmentCard key={skill.skillId} skill={skill} />
            ))}
          </div>
        ) : (
          <EmptyState icon={SearchX} title="No matching skills" description="Try a different search term or category." />
        )
      ) : practical.loading ? (
        <LoadingState label="Loading practical assessments..." />
      ) : practical.error ? (
        <ErrorState message={practical.error.message} onRetry={practical.refetch} />
      ) : practical.data && practical.data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
        <EmptyState
          icon={ClipboardCheck}
          title="No assessments published yet"
          description="Check back soon — new practical assessments are added regularly."
        />
      ) : filteredPractical.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filteredPractical.map((assessment) => (
            <PracticalAssessmentCard key={assessment.id} assessment={assessment} />
          ))}
        </div>
      ) : (
        <EmptyState icon={SearchX} title="No matching assessments" description="Try a different search term or category." />
      )}
    </div>
  );
}
