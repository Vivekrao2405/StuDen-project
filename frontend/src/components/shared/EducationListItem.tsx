import { GraduationCap } from "lucide-react";

import { formatYearRange } from "@/lib/format";

export interface EducationItemData {
  degree: string;
  fieldOfStudy: string | null;
  institution: string;
  startYear: number;
  endYear: number | null;
  current: boolean;
}

export function EducationListItem({ item }: { item: EducationItemData }) {
  return (
    <div className="flex gap-3 py-3">
      <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-accent">
        <GraduationCap className="size-4 text-accent-foreground" />
      </div>
      <div className="min-w-0">
        <p className="font-medium text-foreground">
          {item.degree}
          {item.fieldOfStudy ? <span className="text-muted-foreground"> · {item.fieldOfStudy}</span> : null}
        </p>
        <p className="text-sm text-muted-foreground">{item.institution}</p>
        <p className="text-xs text-muted-foreground">
          {formatYearRange(item.startYear, item.endYear, item.current)}
        </p>
      </div>
    </div>
  );
}
