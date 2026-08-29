import { Briefcase, Check, ClipboardCheck, ListPlus, Sparkles } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { getMyLearning } from "@/lib/api/endpoints/resources";
import type { EligibilityState, ResourceCard as ResourceCardData, WeakAreaGroup } from "@/lib/api/resourceTypes";
import { formatShortDate } from "@/lib/format";
import { useAsync } from "@/lib/hooks/useAsync";
import { parseTag, primaryTopicForResource, topicLabel } from "@/lib/learningTags";
import { ROUTES } from "@/lib/routes";
import { FocusAreaCard } from "@/pages/learning/FocusAreaCard";
import { LearningOverviewCard } from "@/pages/learning/LearningOverviewCard";
import { RecommendedCarousel } from "@/pages/learning/RecommendedCarousel";
import { progressStatusLabel, resourceTypeIcon } from "@/pages/learning/resourceDisplay";
import { ResourceCard } from "@/pages/learning/ResourceCard";

// Own local copy of the native-<select> styling convention (SkillPicker.SELECT_CLASS /
// marketplaceSelectClass.ts) — not imported, same reasoning those already give: keep unrelated
// pages independent of each other's styling constants.
const LEARNING_SELECT_CLASS =
  "h-9 min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30";

type FilterValue = "all" | "recommended" | "inProgress" | "completed";
type SortValue = "latest" | "progress" | "difficulty";

const FILTER_OPTIONS: { value: FilterValue; label: string }[] = [
  { value: "all", label: "All" },
  { value: "recommended", label: "Recommended" },
  { value: "inProgress", label: "In Progress" },
  { value: "completed", label: "Completed" },
];

const SORT_OPTIONS: { value: SortValue; label: string }[] = [
  { value: "latest", label: "Latest" },
  { value: "progress", label: "Progress" },
  { value: "difficulty", label: "Difficulty" },
];

const PROGRESS_RANK: Record<ResourceCardData["progressStatus"], number> = {
  COMPLETED: 2,
  IN_PROGRESS: 1,
  NOT_STARTED: 0,
};
const DIFFICULTY_RANK: Record<string, number> = { EASY: 0, MEDIUM: 1, HARD: 2 };

function EligibilityEmptyState({
  state,
  onGoToPortfolio,
}: {
  state: Exclude<EligibilityState, "HAS_AVAILABLE_ASSESSMENTS">;
  onGoToPortfolio: () => void;
}) {
  if (state === "NO_PORTFOLIO") {
    return (
      <EmptyState
        icon={Briefcase}
        title="Create your portfolio to unlock personalized learning"
        description="Your learning recommendations are based on the skills in your portfolio and your assessment performance."
        action={<Button onClick={onGoToPortfolio}>Create Portfolio</Button>}
      />
    );
  }
  return (
    <EmptyState
      icon={ListPlus}
      title="Add your skills to unlock personalized learning"
      description="Add skills to your portfolio and we'll recommend resources once you've taken an assessment."
      action={<Button onClick={onGoToPortfolio}>Update Portfolio</Button>}
    />
  );
}

interface FlatResource {
  resource: ResourceCardData;
  weakTopics: Set<string>;
}

function flattenGroup(group: WeakAreaGroup): FlatResource[] {
  const weakTopics = new Set(group.weakTags.flatMap((tag) => parseTag(tag).topics));
  return group.resources.map((resource) => ({ resource, weakTopics }));
}

function ContinueLearningRow({ resource, weakTopics }: FlatResource) {
  const navigate = useNavigate();
  const Icon = resourceTypeIcon(resource.resourceType);
  const topic = primaryTopicForResource(resource.tags, weakTopics);

  return (
    <div className="flex items-center gap-3 py-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
        <Icon className="size-4 text-primary" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-foreground">{resource.title}</p>
        <p className="text-xs text-muted-foreground">
          {[topic ? topicLabel(topic) : null, progressStatusLabel(resource.progressStatus)].filter(Boolean).join(" · ")}
        </p>
      </div>
      <Button size="sm" onClick={() => navigate(ROUTES.myLearningResourceDetail(resource.id))}>
        Continue
      </Button>
    </div>
  );
}

function RecentlyCompletedRow({ resource, weakTopics }: FlatResource) {
  const navigate = useNavigate();
  const topic = primaryTopicForResource(resource.tags, weakTopics);
  const completedLabel = formatShortDate(resource.completedAt);

  return (
    <div className="flex items-center gap-3 py-3">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-emerald-500/10">
        <Check className="size-4 text-emerald-600 dark:text-emerald-400" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-foreground">{resource.title}</p>
        <p className="text-xs text-muted-foreground">
          {[topic ? topicLabel(topic) : null, completedLabel ? `Completed ${completedLabel}` : null]
            .filter(Boolean)
            .join(" · ")}
        </p>
      </div>
      <Button size="sm" variant="outline" onClick={() => navigate(ROUTES.myLearningResourceDetail(resource.id))}>
        Review Again
      </Button>
    </div>
  );
}

export function OverviewTab() {
  const navigate = useNavigate();
  const { data, error, loading, refetch } = useAsync(getMyLearning, []);
  const [selectedSkillId, setSelectedSkillId] = useState<string>("all");
  const [filter, setFilter] = useState<FilterValue>("all");
  const [sort, setSort] = useState<SortValue>("latest");

  if (loading) {
    return <LoadingState label="Loading your learning..." />;
  }
  if (error || !data) {
    return <ErrorState message={error?.message ?? "Couldn't load My Learning."} onRetry={refetch} />;
  }

  const eligible = data.state === "HAS_AVAILABLE_ASSESSMENTS";
  const groups = data.groups;
  const visibleGroups = selectedSkillId === "all" ? groups : groups.filter((g) => g.skillId === selectedSkillId);

  const flatResources = visibleGroups.flatMap(flattenGroup);
  const focusTopics = visibleGroups.flatMap((group) =>
    group.topics.map((topic) => ({ ...topic, skillName: group.skillName }))
  );

  const filteredResources = flatResources.filter(({ resource }) => {
    switch (filter) {
      case "recommended":
        return resource.progressStatus === "NOT_STARTED";
      case "inProgress":
        return resource.progressStatus === "IN_PROGRESS";
      case "completed":
        return resource.progressStatus === "COMPLETED";
      default:
        return true;
    }
  });

  const sortedResources = [...filteredResources].sort((a, b) => {
    switch (sort) {
      case "progress":
        return PROGRESS_RANK[b.resource.progressStatus] - PROGRESS_RANK[a.resource.progressStatus];
      case "difficulty":
        return (DIFFICULTY_RANK[a.resource.difficulty ?? "EASY"] ?? 0) - (DIFFICULTY_RANK[b.resource.difficulty ?? "EASY"] ?? 0);
      default:
        return new Date(b.resource.createdAt).getTime() - new Date(a.resource.createdAt).getTime();
    }
  });

  const continueLearning = flatResources
    .filter(({ resource }) => resource.progressStatus === "IN_PROGRESS")
    .sort((a, b) => new Date(b.resource.startedAt ?? 0).getTime() - new Date(a.resource.startedAt ?? 0).getTime())
    .slice(0, 6);

  const recentlyCompleted = flatResources
    .filter(({ resource }) => resource.progressStatus === "COMPLETED")
    .sort((a, b) => new Date(b.resource.completedAt ?? 0).getTime() - new Date(a.resource.completedAt ?? 0).getTime())
    .slice(0, 6);

  return (
    <div className="space-y-6">
      {eligible && groups.length > 1 ? (
        <div className="flex justify-end">
          <select
            value={selectedSkillId}
            onChange={(e) => setSelectedSkillId(e.target.value)}
            aria-label="Filter by skill"
            className={LEARNING_SELECT_CLASS}
          >
            <option value="all">All Skills</option>
            {groups.map((group) => (
              <option key={group.skillId} value={group.skillId}>
                {group.skillName}
              </option>
            ))}
          </select>
        </div>
      ) : null}

      {data.state !== "HAS_AVAILABLE_ASSESSMENTS" ? (
        <EligibilityEmptyState state={data.state} onGoToPortfolio={() => navigate(ROUTES.profile)} />
      ) : groups.length === 0 ? (
        <EmptyState
          icon={ClipboardCheck}
          title="No weak areas found yet"
          description="Take a skill assessment and we'll recommend resources for anything that needs improvement."
          action={
            <Button onClick={() => navigate(ROUTES.skillAssessments)}>
              <Sparkles className="size-4" /> Take an Assessment
            </Button>
          }
        />
      ) : (
        <>
          <LearningOverviewCard overview={data.overview} />

          <section className="space-y-3">
            <div>
              <h2 className="text-lg font-semibold text-foreground">Your Focus Areas</h2>
              <p className="text-sm text-muted-foreground">Areas where you need the most improvement.</p>
            </div>
            {focusTopics.length > 0 ? (
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {focusTopics.map((topic, index) => (
                  <FocusAreaCard key={`${topic.skillName}-${topic.topic}`} topic={topic} index={index} />
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                No specific weak topics identified yet — resources below are matched by skill.
              </p>
            )}
          </section>

          <section className="space-y-3">
            <div>
              <h2 className="text-lg font-semibold text-foreground">Recommended for You</h2>
              <p className="text-sm text-muted-foreground">Resources picked specifically to help you improve.</p>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="max-w-full overflow-x-auto">
                <SegmentedControl value={filter} onChange={setFilter} options={FILTER_OPTIONS} />
              </div>
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value as SortValue)}
                aria-label="Sort resources"
                className={LEARNING_SELECT_CLASS}
              >
                {SORT_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    Sort: {option.label}
                  </option>
                ))}
              </select>
            </div>

            {sortedResources.length > 0 ? (
              <RecommendedCarousel itemCount={sortedResources.length}>
                {sortedResources.map(({ resource, weakTopics }) => (
                  <div
                    key={resource.id}
                    data-carousel-item
                    className="w-64 shrink-0 snap-start sm:w-72"
                  >
                    <ResourceCard resource={resource} weakTopics={weakTopics} />
                  </div>
                ))}
              </RecommendedCarousel>
            ) : (
              <p className="text-sm text-muted-foreground">No resources match this filter.</p>
            )}
          </section>

          <section className="space-y-1">
            <div>
              <h2 className="text-lg font-semibold text-foreground">Continue Learning</h2>
              <p className="text-sm text-muted-foreground">Pick up where you left off.</p>
            </div>
            {continueLearning.length > 0 ? (
              <Card>
                <CardContent className="divide-y divide-border">
                  {continueLearning.map((item) => (
                    <ContinueLearningRow key={item.resource.id} {...item} />
                  ))}
                </CardContent>
              </Card>
            ) : (
              <p className="py-2 text-sm text-muted-foreground">You haven't started learning yet.</p>
            )}
          </section>

          <section className="space-y-1">
            <div>
              <h2 className="text-lg font-semibold text-foreground">Recently Completed</h2>
              <p className="text-sm text-muted-foreground">Great job! Keep it up.</p>
            </div>
            {recentlyCompleted.length > 0 ? (
              <Card>
                <CardContent className="divide-y divide-border">
                  {recentlyCompleted.map((item) => (
                    <RecentlyCompletedRow key={item.resource.id} {...item} />
                  ))}
                </CardContent>
              </Card>
            ) : (
              <p className="py-2 text-sm text-muted-foreground">Nothing completed yet — finish a resource to see it here.</p>
            )}
          </section>
        </>
      )}
    </div>
  );
}
