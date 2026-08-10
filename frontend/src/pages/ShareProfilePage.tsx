import { Award, Check, CircleDot, Copy, GraduationCap, MapPin, Quote, Share2, Sparkles } from "lucide-react";
import { useState } from "react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { BrandName } from "@/components/shared/BrandName";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { Logo } from "@/components/layout/Logo";
import { QRCode } from "@/components/shared/QRCode";
import { SkillIcon } from "@/components/shared/SkillIcon";
import { useAuth } from "@/features/auth/useAuth";
import { useToast } from "@/hooks/useToast";
import { listCertificates } from "@/lib/api/endpoints/certificates";
import { listEducation } from "@/lib/api/endpoints/education";
import { getMyPortfolio, getShareMetadata } from "@/lib/api/endpoints/portfolio";
import { getCurrentUser } from "@/lib/api/endpoints/users";
import type { AvailabilityOption } from "@/lib/api/types";
import { getAvailabilityOption } from "@/lib/availabilityOptions";
import { useAsync } from "@/lib/hooks/useAsync";
import { generateTagline } from "@/lib/tagline";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

async function loadShareCard() {
  const [user, portfolio, education, certificates, shareMeta] = await Promise.all([
    getCurrentUser(),
    getMyPortfolio(),
    listEducation(),
    listCertificates(),
    getShareMetadata(),
  ]);
  return { user, portfolio, education, certificates, shareMeta };
}

export function ShareProfilePage() {
  const { user: authUser } = useAuth();
  const { data, error, loading, refetch } = useAsync(loadShareCard, []);
  const [copied, setCopied] = useState(false);
  const toast = useToast();

  async function handleCopy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      toast.success("Link copied to clipboard.");
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error("Couldn't copy the link. Please copy it manually.");
    }
  }

  async function handleShare(url: string) {
    try {
      await navigator.share({ url, title: "My StuDen profile" });
    } catch {
      // user cancelled the native share sheet — nothing to do
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Share Your Profile</h1>
        <p className="text-sm text-muted-foreground">
          Share your public <BrandName /> profile link anywhere — LinkedIn, WhatsApp, resumes and more.
        </p>
      </div>

      {loading ? (
        <LoadingState label="Loading your profile card..." />
      ) : error ? (
        <ErrorState
          message={
            error.message.includes("portfolio")
              ? "You need to create a portfolio before you can share your profile."
              : error.message
          }
          onRetry={refetch}
        />
      ) : data ? (
        <>
          <ShareCard
            fullName={data.user.fullName}
            profileImageUrl={data.user.profileImageUrl}
            headline={data.portfolio.headline}
            location={data.portfolio.location}
            available={data.portfolio.available}
            skills={data.portfolio.skills}
            availableFor={data.portfolio.availableFor}
            education={data.education}
            certificateCount={data.certificates.length}
            profileUrl={data.shareMeta.profileUrl}
          />

          <Card>
            <CardHeader>
              <CardTitle>Your public profile link</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {authUser ? (
                <div className="flex items-center gap-3 rounded-lg border border-border p-3">
                  <Avatar className="size-10">
                    {authUser.profileImageUrl ? (
                      <AvatarImage src={authUser.profileImageUrl} alt={authUser.fullName} />
                    ) : null}
                    <AvatarFallback>{getInitials(authUser.fullName)}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{authUser.fullName}</p>
                    <p className="text-xs text-muted-foreground">
                      This is what people will see when they open your link.
                    </p>
                  </div>
                </div>
              ) : null}

              <div className="flex gap-2">
                <Input readOnly value={data.shareMeta.profileUrl} className="h-10" />
                <Button
                  variant="outline"
                  size="icon-lg"
                  aria-label="Copy link"
                  onClick={() => handleCopy(data.shareMeta.profileUrl)}
                >
                  {copied ? <Check className="text-primary" /> : <Copy />}
                </Button>
              </div>

              {typeof navigator !== "undefined" && "share" in navigator ? (
                <Button variant="outline" onClick={() => handleShare(data.shareMeta.profileUrl)}>
                  <Share2 /> Share
                </Button>
              ) : null}
            </CardContent>
          </Card>
        </>
      ) : null}
    </div>
  );
}

interface ShareCardProps {
  fullName: string;
  profileImageUrl: string | null;
  headline: string;
  location: string | null;
  available: boolean;
  skills: { id: string; name: string; category: string; iconSlug: string | null }[];
  availableFor: AvailabilityOption[];
  education: { degree: string; fieldOfStudy: string | null; institution: string }[];
  certificateCount: number;
  profileUrl: string;
}

function ShareCard({
  fullName,
  profileImageUrl,
  headline,
  location,
  available,
  skills,
  availableFor,
  education,
  certificateCount,
  profileUrl,
}: ShareCardProps) {
  const latestEducation = education[0];
  const educationLine = latestEducation
    ? [latestEducation.degree, latestEducation.fieldOfStudy].filter(Boolean).join(" ") +
      (latestEducation.institution ? ` • ${latestEducation.institution}` : "")
    : null;

  const tagline = generateTagline(skills);
  const displayUrl = profileUrl.replace(/^https?:\/\//, "");

  const stats = [
    { label: "Skills", value: skills.length, icon: Sparkles },
    { label: "Education", value: education.length, icon: GraduationCap },
    { label: "Certificates", value: certificateCount, icon: Award },
  ];

  return (
    <Card className="overflow-hidden border-2 p-0">
      <div className="space-y-6 p-5 sm:p-8">
        <div className="flex items-center justify-between">
          <Logo size="sm" />
          <Badge variant={available ? "default" : "secondary"} className="gap-1.5 rounded-full px-3 py-1">
            <CircleDot className="size-2.5" />
            {available ? "Available" : "Not available"}
          </Badge>
        </div>

        <div className="flex flex-col items-center gap-4 text-center sm:flex-row sm:items-start sm:text-left">
          <Avatar className="size-24 border-4 border-card shadow-sm sm:size-28">
            {profileImageUrl ? <AvatarImage src={profileImageUrl} alt={fullName} /> : null}
            <AvatarFallback className="text-2xl">{getInitials(fullName)}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1 space-y-1">
            <h2 className="text-2xl font-bold text-foreground">{fullName}</h2>
            <p className="text-lg font-semibold text-primary">{headline}</p>
            {location ? (
              <p className="inline-flex items-center justify-center gap-1 text-sm text-muted-foreground sm:justify-start">
                <MapPin className="size-3.5" /> {location}
              </p>
            ) : null}
            {educationLine ? (
              <p className="inline-flex items-center justify-center gap-1 text-sm text-muted-foreground sm:justify-start">
                <GraduationCap className="size-3.5" /> {educationLine}
              </p>
            ) : null}
          </div>
        </div>

        {skills.length > 0 ? (
          <div className="flex flex-wrap justify-center gap-2 sm:justify-start">
            {skills.map((skill) => (
              <span
                key={skill.id}
                className="inline-flex items-center gap-1.5 rounded-full border border-border bg-card px-3 py-1.5 text-sm text-foreground"
              >
                <SkillIcon iconSlug={skill.iconSlug} className="size-4" />
                {skill.name}
              </span>
            ))}
          </div>
        ) : null}

        <Separator />

        <div className="grid gap-6 md:grid-cols-2">
          {availableFor.length > 0 ? (
            <div>
              <h3 className="mb-3 text-base font-semibold text-foreground">
                I&apos;m <span className="text-primary">available</span> for
              </h3>
              <ul className="space-y-2">
                {availableFor.map((option) => {
                  const { label, icon: Icon } = getAvailabilityOption(option);
                  return (
                    <li
                      key={option}
                      className="flex items-center justify-between rounded-lg border border-border px-3 py-2"
                    >
                      <span className="flex items-center gap-2 text-sm text-foreground">
                        <span className="flex size-7 items-center justify-center rounded-md bg-accent text-accent-foreground">
                          <Icon className="size-3.5" />
                        </span>
                        {label}
                      </span>
                      <Check className="size-4 text-primary" />
                    </li>
                  );
                })}
              </ul>
            </div>
          ) : null}

          <div
            className={
              "flex flex-col justify-center rounded-xl bg-accent p-5" +
              (availableFor.length === 0 ? " md:col-span-2" : "")
            }
          >
            <Quote className="mb-2 size-6 text-primary" />
            <p className="text-lg leading-snug font-semibold text-foreground">{tagline}</p>
          </div>
        </div>

        <div className="grid grid-cols-3 gap-px overflow-hidden rounded-xl bg-primary text-primary-foreground">
          {stats.map((stat) => (
            <div key={stat.label} className="flex items-center gap-2 bg-primary px-3 py-3">
              <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-white/15">
                <stat.icon className="size-4" />
              </span>
              <span className="min-w-0">
                <span className="block text-lg leading-none font-bold">{stat.value}</span>
                <span className="block truncate text-xs opacity-90">{stat.label}</span>
              </span>
            </div>
          ))}
        </div>

        <div className="flex flex-col items-center justify-between gap-4 rounded-xl border border-border p-4 sm:flex-row">
          <div>
            <p className="text-sm text-muted-foreground">View my full profile</p>
            <a href={profileUrl} className="text-sm font-semibold text-primary hover:underline">
              {displayUrl}
            </a>
          </div>
          <QRCode value={profileUrl} size={96} className="rounded-md border border-border p-1" />
        </div>
      </div>
    </Card>
  );
}
