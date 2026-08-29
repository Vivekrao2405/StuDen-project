import { useState } from "react";

import { SegmentedControl } from "@/components/ui/segmented-control";
import { CalendarTab } from "@/pages/calendar/CalendarTab";
import { OverviewTab } from "@/pages/learning/OverviewTab";
import { RoadmapTab } from "@/pages/roadmap/RoadmapTab";

type LearningTab = "overview" | "roadmap" | "calendar";

const TAB_OPTIONS: { value: LearningTab; label: string }[] = [
  { value: "overview", label: "Overview" },
  { value: "roadmap", label: "Roadmap" },
  { value: "calendar", label: "Calendar" },
];

// Roadmap ("what should I learn?") and Calendar ("when should I learn it?") live as tabs of the
// same Learning page/nav item rather than separate top-level routes -- scheduling a roadmap topic
// from the Roadmap tab and immediately switching to Calendar re-fetches fresh, so the two always
// agree without any shared client-side cache.
export function MyLearningPage() {
  const [tab, setTab] = useState<LearningTab>("overview");

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">My Learning</h1>
          <p className="text-sm text-muted-foreground">
            Your personalized learning path based on your assessment performance.
          </p>
        </div>
        <SegmentedControl value={tab} onChange={setTab} options={TAB_OPTIONS} />
      </div>

      {tab === "overview" ? <OverviewTab /> : tab === "roadmap" ? <RoadmapTab /> : <CalendarTab />}
    </div>
  );
}
