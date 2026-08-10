import { Award, CircleDot, GraduationCap, MapPin } from "lucide-react";
import { useParams } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CertificateListItem } from "@/components/shared/CertificateListItem";
import { CoverImage } from "@/components/shared/CoverImage";
import { EducationListItem } from "@/components/shared/EducationListItem";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { getPublicProfile } from "@/lib/api/endpoints/publicProfile";
import { useAsync } from "@/lib/hooks/useAsync";

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
          className="-mx-(--card-spacing) -mt-(--card-spacing) mb-2 rounded-t-xl"
        />
        <CardHeader className="flex-row items-start gap-4">
          <Avatar size="lg">
            {data.profileImageUrl ? <AvatarImage src={data.profileImageUrl} alt={data.fullName} /> : null}
            <AvatarFallback>{getInitials(data.fullName)}</AvatarFallback>
          </Avatar>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-bold text-foreground">{data.fullName}</h1>
              <Badge variant={data.availability ? "default" : "secondary"} className="gap-1">
                <CircleDot className="size-2.5" />
                {data.availability ? "Available" : "Not available"}
              </Badge>
            </div>
            <p className="text-sm text-muted-foreground">{data.headline}</p>
            {data.location ? (
              <p className="mt-1 inline-flex items-center gap-1 text-xs text-muted-foreground">
                <MapPin className="size-3.5" /> {data.location}
              </p>
            ) : null}
          </div>
        </CardHeader>
        {data.about ? (
          <CardContent>
            <p className="text-sm text-foreground">{data.about}</p>
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
    </div>
  );
}
