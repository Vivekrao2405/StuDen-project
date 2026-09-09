import { ArrowRight, BarChart3, Building, Building2, Heart, Lightbulb, Pencil, Sparkles, Target } from "lucide-react";
import { Link } from "react-router-dom";

import { SkillChip } from "@/components/shared/SkillChip";
import { Button } from "@/components/ui/button";
import placementHero from "@/assets/placement-hero.webp";
import placementMotivation from "@/assets/placement-motivation.webp";
import type { PlacementProfileResponse } from "@/lib/api/placementTypes";
import { ROUTES } from "@/lib/routes";
import { COMPANY_TYPE_LABEL, EXPERIENCE_LEVEL_LABEL } from "@/pages/placement/placementDisplay";

interface PlacementProfileOverviewProps {
  profile: PlacementProfileResponse;
  onEdit: () => void;
}

const HERO_BENEFITS = [
  { icon: Target, label: "Know your current level" },
  { icon: BarChart3, label: "Find skill gaps" },
  { icon: Sparkles, label: "Get a personalized learning path" },
];

/** The student's existing Placement experience, per Phase 2: just their saved profile plus an
 * Edit entry point. Readiness scoring, skill gaps, recommendations and the rest of the Placement
 * dashboard are later phases and deliberately not shown here. */
export function PlacementProfileOverview({ profile, onEdit }: PlacementProfileOverviewProps) {
  const companyTypesLabel = profile.companyTypes.length > 0 ? profile.companyTypes.map((t) => COMPANY_TYPE_LABEL[t]).join(", ") : "Not set";
  const targetCompanyNames = [...profile.targetCompanies.map((c) => c.name), ...profile.manualTargetCompanies];
  const targetCompaniesLabel = targetCompanyNames.length > 0 ? targetCompanyNames.join(", ") : "Not set";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-medium text-muted-foreground">
            Placement <span className="mx-1 text-muted-foreground/60">/</span> Profile
          </p>
          <h1 className="mt-1 text-2xl font-bold text-foreground">Your Placement Profile</h1>
          <p className="text-sm text-muted-foreground">This is what StuDen uses to personalize your placement prep.</p>
        </div>
        <Button size="sm" onClick={onEdit}>
          <Pencil className="size-4" /> Edit Profile
        </Button>
      </div>

      <div className="overflow-hidden rounded-3xl border border-primary/10 bg-gradient-to-br from-primary/[0.04] via-background to-primary/10">
        <div className="flex flex-col items-center gap-8 p-6 sm:p-8 lg:flex-row lg:items-center lg:justify-between lg:p-10">
          <div className="max-w-xl">
            <p className="text-xs font-semibold tracking-wide text-primary uppercase">Placement Readiness</p>
            <h2 className="mt-2 text-2xl font-bold text-foreground sm:text-3xl">Are you ready for your dream role?</h2>
            <p className="mt-3 text-sm text-muted-foreground sm:text-base">
              Take a role-specific readiness assessment to understand your strengths, identify skill gaps, and get
              personalized learning recommendations.
            </p>
            <Button size="lg" className="mt-5" render={<Link to={ROUTES.placementReadiness} />}>
              Check Readiness <ArrowRight className="size-4" />
            </Button>

            <div className="mt-7 flex flex-wrap gap-x-7 gap-y-4">
              {HERO_BENEFITS.map((benefit) => (
                <div key={benefit.label} className="flex items-center gap-2.5">
                  <benefit.icon className="size-5 shrink-0 text-primary" aria-hidden="true" />
                  <span className="text-sm leading-tight font-medium text-foreground">{benefit.label}</span>
                </div>
              ))}
            </div>
          </div>

          <img
            src={placementHero}
            alt="Illustration of a student studying with a laptop, with signposts pointing toward better skills, more opportunities and a dream job"
            className="w-full max-w-sm shrink-0 rounded-2xl object-contain lg:max-w-md"
            loading="lazy"
          />
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="rounded-2xl border border-border bg-card p-6 lg:col-span-2">
          <div className="flex items-center gap-3">
            <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10">
              <Target className="size-5 text-primary" aria-hidden="true" />
            </div>
            <div>
              <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">Target Role</p>
              <p className="text-base font-semibold text-foreground">{profile.targetRoleName}</p>
            </div>
          </div>

          <div className="mt-6 grid gap-4 sm:grid-cols-3">
            <div className="flex items-center gap-2.5">
              <Building2 className="size-4.5 shrink-0 text-primary" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">Company Types</p>
                <p className="truncate text-sm font-medium text-foreground" title={companyTypesLabel}>
                  {companyTypesLabel}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2.5">
              <Building className="size-4.5 shrink-0 text-primary" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">Target Companies</p>
                <p className="truncate text-sm font-medium text-foreground" title={targetCompaniesLabel}>
                  {targetCompaniesLabel}
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2.5">
              <BarChart3 className="size-4.5 shrink-0 text-primary" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-[11px] font-medium tracking-wide text-muted-foreground uppercase">Experience Level</p>
                <p className="text-sm font-medium text-foreground">{EXPERIENCE_LEVEL_LABEL[profile.experienceLevel]}</p>
              </div>
            </div>
          </div>

          <div className="mt-6 border-t border-border pt-5">
            <p className="mb-3 text-xs font-medium tracking-wide text-muted-foreground uppercase">Current Skills</p>
            {profile.currentSkills.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {profile.currentSkills.map((skill) => (
                  <SkillChip key={skill.id} skill={skill} variant="compact" />
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No skills added yet — edit your profile to add some.</p>
            )}
          </div>
        </div>

        <div className="flex flex-col overflow-hidden rounded-2xl border border-border bg-card p-6">
          <p className="text-base font-semibold text-balance text-foreground italic">
            “A dream job is not a destination, it&rsquo;s the result of consistent effort.”
          </p>
          <p className="mt-2 text-sm text-muted-foreground">— StuDen</p>
          <img
            src={placementMotivation}
            alt="Illustration of a person running up a set of stairs toward a flag at the top"
            className="mt-auto w-full object-contain pt-6"
            loading="lazy"
          />
        </div>
      </div>

      <div className="flex flex-col items-start justify-between gap-4 rounded-2xl bg-primary/10 p-5 sm:flex-row sm:items-center sm:p-6">
        <div className="flex items-center gap-4">
          <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-card">
            <Lightbulb className="size-5 text-primary" aria-hidden="true" />
          </div>
          <div>
            <p className="text-sm font-semibold text-foreground sm:text-base">Small steps today, a bigger tomorrow.</p>
            <p className="text-sm text-muted-foreground">Keep learning, keep practicing, keep showing up.</p>
          </div>
        </div>
        <p className="inline-flex items-center gap-1.5 text-sm text-muted-foreground">
          You&rsquo;ve got this <Heart className="size-4 fill-primary text-primary" aria-hidden="true" />
        </p>
      </div>
    </div>
  );
}
