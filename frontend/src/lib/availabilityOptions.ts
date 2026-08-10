import { Briefcase, Clock, Code2, FolderKanban, GitBranch, GraduationCap, Users } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import type { AvailabilityOption } from "@/lib/api/types";

export const AVAILABILITY_OPTIONS: { value: AvailabilityOption; label: string; icon: LucideIcon }[] = [
  { value: "FREELANCE_PROJECTS", label: "Freelance Projects", icon: Briefcase },
  { value: "COLLABORATIONS", label: "Collaborations", icon: Users },
  { value: "HACKATHONS", label: "Hackathons", icon: Code2 },
  { value: "STUDENT_PROJECTS", label: "Student Projects", icon: FolderKanban },
  { value: "INTERNSHIPS", label: "Internships", icon: GraduationCap },
  { value: "OPEN_SOURCE", label: "Open Source", icon: GitBranch },
  { value: "PART_TIME", label: "Part-time Opportunities", icon: Clock },
];

const LABELS_BY_VALUE = new Map(AVAILABILITY_OPTIONS.map((o) => [o.value, o]));

export function getAvailabilityOption(value: AvailabilityOption) {
  return LABELS_BY_VALUE.get(value) ?? { value, label: value, icon: Briefcase };
}
