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
import { PracticalAssessmentCard } from "@/pages/practical/PracticalAssessmentCard";
import { SkillAssessmentCard } from "@/pages/assessment/SkillAssessmentCard";

type Tab = "knowledge" | "practical";

export function SkillAssessmentsPage() {
  const [tab, setTab] = useState<Tab>("knowledge");
  const [query, setQuery] = useState("");

  const knowledge = useAsync(listAssessableSkills, []);
  const practical = useAsync(() => listPracticalAssessments({ size: 50 }), []);

  const filteredKnowledge = useMemo(() => {
    if (!knowledge.data) return [];
    const term = query.trim().toLowerCase();
    if (!term) return knowledge.data;
    return knowledge.data.filter((skill) => skill.name.toLowerCase().includes(term) || skill.category.toLowerCase().includes(term));
  }, [knowledge.data, query]);

  const filteredPractical = useMemo(() => {
    if (!practical.data) return [];
    const term = query.trim().toLowerCase();
    if (!term) return practical.data.content;
    return practical.data.content.filter(
      (a) => a.title.toLowerCase().includes(term) || a.skillName.toLowerCase().includes(term)
    );
  }, [practical.data, query]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Skill Assessments</h1>
        <p className="text-sm text-muted-foreground">Prove your skills with knowledge and practical assessments.</p>
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
        ) : filteredKnowledge.length > 0 ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {filteredKnowledge.map((skill) => (
              <SkillAssessmentCard key={skill.skillId} skill={skill} />
            ))}
          </div>
        ) : knowledge.data && knowledge.data.length > 0 ? (
          <EmptyState icon={SearchX} title="No matching skills" description="Try a different search term." />
        ) : (
          <EmptyState
            icon={ClipboardCheck}
            title="No assessments available yet"
            description="Check back soon — skill assessments are added regularly."
          />
        )
      ) : practical.loading ? (
        <LoadingState label="Loading practical assessments..." />
      ) : practical.error ? (
        <ErrorState message={practical.error.message} onRetry={practical.refetch} />
      ) : filteredPractical.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filteredPractical.map((assessment) => (
            <PracticalAssessmentCard key={assessment.id} assessment={assessment} />
          ))}
        </div>
      ) : practical.data && practical.data.content.length > 0 ? (
        <EmptyState icon={SearchX} title="No matching assessments" description="Try a different search term." />
      ) : (
        <EmptyState
          icon={ClipboardCheck}
          title="No practical assessments available yet"
          description="Check back soon — practical assessments are added regularly."
        />
      )}
    </div>
  );
}
