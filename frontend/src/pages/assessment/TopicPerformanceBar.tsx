import type { TopicPerformanceView } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { tierBarClasses } from "@/pages/assessment/assessmentLevelDisplay";

interface TopicPerformanceBarProps {
  topic: TopicPerformanceView;
}

// Same purpose-built bar idiom as AssessmentProgressBar (no Progress primitive in this codebase) —
// color follows the topic's tier (strong/developing/needs improvement) rather than a fixed color.
export function TopicPerformanceBar({ topic }: TopicPerformanceBarProps) {
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="min-w-0 truncate font-medium text-foreground">{topic.topicName}</span>
        <span className="shrink-0 text-muted-foreground">
          {topic.correctCount}/{topic.totalQuestions} · {topic.percentage}%
        </span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className={cn("h-full rounded-full transition-all", tierBarClasses(topic.tier))}
          style={{ width: `${topic.percentage}%` }}
        />
      </div>
    </div>
  );
}
