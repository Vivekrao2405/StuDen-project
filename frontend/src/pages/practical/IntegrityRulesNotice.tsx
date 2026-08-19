import { ShieldAlert } from "lucide-react";

import type { IntegrityPolicy } from "@/lib/api/practicalTypes";

// Pre-start "rules of engagement" notice (Phase 7.6, goal #20) -- only mentions restrictions
// that are actually configured for this assessment, and never exposes internal thresholds,
// deduction values, or detection implementation. Browser-based monitoring has real limits (it
// can't see another monitor, a phone, or a second device) -- the wording here is deliberately
// honest about that rather than implying perfect anti-cheating.
export function IntegrityRulesNotice({ policy }: { policy: IntegrityPolicy }) {
  const restrictions: string[] = [];
  if (!policy.allowPaste) restrictions.push("Pasting into the workspace is disabled.");
  if (!policy.allowCopy) restrictions.push("Copying from the workspace is disabled.");
  if (!policy.allowCut) restrictions.push("Cutting from the workspace is disabled.");
  if (policy.requireFullscreen) restrictions.push("This assessment requires fullscreen mode.");

  return (
    <div className="flex gap-3 rounded-lg border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
      <ShieldAlert className="mt-0.5 size-4 shrink-0" />
      <div className="space-y-1">
        <p className="font-medium text-foreground">During this assessment</p>
        <ul className="list-disc space-y-0.5 pl-4">
          <li>Keep this tab open — switching away is recorded.</li>
          {restrictions.map((rule) => (
            <li key={rule}>{rule}</li>
          ))}
          <li>Your activity during this assessment may be reviewed for integrity.</li>
        </ul>
      </div>
    </div>
  );
}
