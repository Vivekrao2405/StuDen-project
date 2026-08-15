import { ClipboardCheck, Search, SearchX } from "lucide-react";
import { useMemo, useState } from "react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Input } from "@/components/ui/input";
import { listAssessableSkills } from "@/lib/api/endpoints/assessments";
import { useAsync } from "@/lib/hooks/useAsync";
import { SkillAssessmentCard } from "@/pages/assessment/SkillAssessmentCard";

export function SkillAssessmentsPage() {
  const [query, setQuery] = useState("");
  const { data, error, loading, refetch } = useAsync(listAssessableSkills, []);

  const filtered = useMemo(() => {
    if (!data) return [];
    const term = query.trim().toLowerCase();
    if (!term) return data;
    return data.filter((skill) => skill.name.toLowerCase().includes(term) || skill.category.toLowerCase().includes(term));
  }, [data, query]);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Skill Assessments</h1>
        <p className="text-sm text-muted-foreground">Prove your skills with a graded knowledge assessment.</p>
      </div>

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search skills..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      {loading ? (
        <LoadingState label="Loading assessments..." />
      ) : error ? (
        <ErrorState message={error.message} onRetry={refetch} />
      ) : filtered.length > 0 ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((skill) => (
            <SkillAssessmentCard key={skill.skillId} skill={skill} />
          ))}
        </div>
      ) : data && data.length > 0 ? (
        <EmptyState icon={SearchX} title="No matching skills" description="Try a different search term." />
      ) : (
        <EmptyState
          icon={ClipboardCheck}
          title="No assessments available yet"
          description="Check back soon — skill assessments are added regularly."
        />
      )}
    </div>
  );
}
