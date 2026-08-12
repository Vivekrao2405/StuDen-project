import type {
  CertificateResponse,
  EducationResponse,
  PortfolioResponse,
  ProjectResponse,
  UserResponse,
} from "@/lib/api/types";

export interface ProfileCompletionCheck {
  label: string;
  done: boolean;
}

export interface ProfileCompletion {
  percent: number;
  checks: ProfileCompletionCheck[];
}

/**
 * Six equally-weighted checks against real, existing data only. Challenges is deliberately
 * excluded — that feature doesn't exist yet, and including an uncompletable check would make
 * 100% unreachable for every student.
 */
export function computeProfileCompletion(
  user: Pick<UserResponse, "profileImageUrl">,
  portfolio: Pick<PortfolioResponse, "headline" | "location" | "skills"> | null,
  education: EducationResponse[],
  certificates: CertificateResponse[],
  projects: ProjectResponse[]
): ProfileCompletion {
  const checks: ProfileCompletionCheck[] = [
    { label: "Profile photo", done: Boolean(user.profileImageUrl) },
    { label: "Basic info", done: Boolean(portfolio?.headline?.trim() && portfolio?.location?.trim()) },
    { label: "Skills", done: Boolean(portfolio && portfolio.skills.length > 0) },
    { label: "Education", done: education.length > 0 },
    { label: "Certificates", done: certificates.length > 0 },
    { label: "Projects", done: projects.length > 0 },
  ];

  const doneCount = checks.filter((c) => c.done).length;
  const percent = Math.round((doneCount / checks.length) * 100);

  return { percent, checks };
}
