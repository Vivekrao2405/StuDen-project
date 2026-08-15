interface AssessmentProgressBarProps {
  current: number;
  total: number;
}

/** `Question X of N` + a filled progress bar — no existing Progress primitive in this codebase to
 * reuse, so this is a small purpose-built one. */
export function AssessmentProgressBar({ current, total }: AssessmentProgressBarProps) {
  const percent = total === 0 ? 0 : Math.round((current / total) * 100);
  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between text-xs font-medium text-muted-foreground">
        <span>
          Question {current} of {total}
        </span>
        <span>{percent}%</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${percent}%` }} />
      </div>
    </div>
  );
}
