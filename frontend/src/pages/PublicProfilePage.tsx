import { ArrowRight, Award, CircleDot, GraduationCap, LayoutGrid, MapPin } from "lucide-react";
import { Link, useParams } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CertificateListItem } from "@/components/shared/CertificateListItem";
import { CoverImage } from "@/components/shared/CoverImage";
import { EducationListItem } from "@/components/shared/EducationListItem";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { ProjectCard } from "@/components/shared/ProjectCard";
import { SkillChip } from "@/components/shared/SkillChip";
import { getPublicProfile } from "@/lib/api/endpoints/publicProfile";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function PublicProfilePage() {
  const { slug = "" } = useParams<{ slug: string }>();
  const { data, error, loading, refetch } = useAsync(() => getPublicProfile(slug), [slug]);

  if (loading) {
    return <LoadingState label="Loading profile..." />;
  }

  if (error || !data) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16">
        <ErrorState
          title="Profile not found"
          message="This profile doesn't exist or is no longer available."
          onRetry={refetch}
        />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6 lg:px-8">
      <Card>
        <CoverImage
          src={data.coverImageUrl}
          alt={`${data.fullName} cover`}
          className="-mx-(--card-spacing) -mt-(--card-spacing) rounded-t-xl"
        />
        <div className="flex flex-col items-center px-(--card-spacing) text-center sm:items-start sm:text-left">
          <Avatar className="-mt-10 size-20 border-4 border-card shadow-sm sm:size-24">
            {data.profileImageUrl ? <AvatarImage src={data.profileImageUrl} alt={data.fullName} /> : null}
            <AvatarFallback className="text-xl">{getInitials(data.fullName)}</AvatarFallback>
          </Avatar>
        </div>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
            <h1 className="text-xl font-bold text-foreground">{data.fullName}</h1>
            <Badge variant={data.availability ? "default" : "secondary"} className="gap-1">
              <CircleDot className="size-2.5" />
              {data.availability ? "Available" : "Not available"}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground">{data.headline}</p>
          {data.location ? (
            <p className="mt-1 inline-flex items-center justify-center gap-1 text-xs text-muted-foreground sm:justify-start">
              <MapPin className="size-3.5" /> {data.location}
            </p>
          ) : null}
        </CardHeader>
        {data.about || data.skills.length > 0 ? (
          <CardContent className="space-y-4">
            {data.about ? <p className="text-sm text-foreground">{data.about}</p> : null}
            {data.skills.length > 0 ? (
              <div className="flex flex-wrap justify-center gap-2 sm:justify-start">
                {data.skills.map((skill) => (
                  <SkillChip key={skill.id} skill={skill} />
                ))}
              </div>
            ) : null}
          </CardContent>
        ) : null}
      </Card>

      {data.education.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <GraduationCap className="size-4" /> Education
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="divide-y divide-border">
              {data.education.map((item, i) => (
                <EducationListItem key={i} item={item} />
              ))}
            </div>
          </CardContent>
        </Card>
      ) : null}

      {data.certificates.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Award className="size-4" /> Certificates
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="divide-y divide-border">
              {data.certificates.map((item, i) => (
                <CertificateListItem key={i} item={item} />
              ))}
            </div>
          </CardContent>
        </Card>
      ) : null}

      {data.showcase.length > 0 ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <LayoutGrid className="size-4" /> Showcase
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {data.showcase.map((project) => (
                <ProjectCard key={project.id} project={project} href={ROUTES.publicProject(slug, project.id)} />
              ))}
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div className="flex justify-center pt-2">
        <Button render={<Link to={ROUTES.home} />}>
          Explore StuDen <ArrowRight />
        </Button>
      </div>
    </div>
  );
}
