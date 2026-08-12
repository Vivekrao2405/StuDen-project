import type { CertificateResponse, EducationResponse, PortfolioResponse, UserResponse } from "@/lib/api/types";

export interface ProfileCompletionCheck {
  label: string;
  done: boolean;
}

export interface ProfileCompletion {
  percent: number;
  checks: ProfileCompletionCheck[];
}

/**
 * Five equally-weighted (20% each) checks against real, existing data only. Projects and
 * challenges are deliberately excluded — neither feature exists yet, and including an
 * uncompletable check would make 100% unreachable for every student.
 */
export function computeProfileCompletion(
  user: Pick<UserResponse, "profileImageUrl">,
  portfolio: Pick<PortfolioResponse, "headline" | "location" | "skills"> | null,
  education: EducationResponse[],
  certificates: CertificateResponse[]
): ProfileCompletion {
  const checks: ProfileCompletionCheck[] = [
    { label: "Profile photo", done: Boolean(user.profileImageUrl) },
    { label: "Basic info", done: Boolean(portfolio?.headline?.trim() && portfolio?.location?.trim()) },
    { label: "Skills", done: Boolean(portfolio && portfolio.skills.length > 0) },
    { label: "Education", done: education.length > 0 },
    { label: "Certificates", done: certificates.length > 0 },
  ];

  const doneCount = checks.filter((c) => c.done).length;
  const percent = Math.round((doneCount / checks.length) * 100);

  return { percent, checks };
}
