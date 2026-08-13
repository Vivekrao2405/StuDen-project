import { Clock, ExternalLink, FolderKanban, MapPin } from "lucide-react";
import { Link } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { SkillChip } from "@/components/shared/SkillChip";
import type { MarketplaceCategory, ProjectMediaType, ServiceCurrency, SkillResponse } from "@/lib/api/types";
import { cloudinaryThumb } from "@/lib/cloudinary";
import { MARKETPLACE_CATEGORY_OPTIONS } from "@/lib/marketplaceOptions";
import { ROUTES } from "@/lib/routes";

export interface ServiceDetailViewMedia {
  id: string;
  url: string;
  thumbnailUrl: string | null;
  mediaType: ProjectMediaType;
  cover: boolean;
}

export interface ServiceDetailViewLinkedProject {
  id: string;
  title: string;
  coverImageUrl: string | null;
}

export interface ServiceDetailViewData {
  title: string;
  description: string | null;
  category: MarketplaceCategory | "";
  location: string | null;
  priceAmount: number | null;
  currency: ServiceCurrency;
  deliveryDays: number | null;
  available: boolean;
  skills: SkillResponse[];
  whatYoullReceive: string[];
  media: ServiceDetailViewMedia[];
  links: { label: string; url: string }[];
  linkedProjects: ServiceDetailViewLinkedProject[];
}

export interface ServiceDetailViewProvider {
  name: string;
  headline?: string | null;
  profileImageUrl: string | null;
  /** Present only when the current viewer can navigate to the provider's public profile — e.g.
   * absent in the ServiceForm preview, where the "View Profile" link isn't the point. */
  slug?: string | null;
}

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function categoryLabel(category: MarketplaceCategory | "") {
  return MARKETPLACE_CATEGORY_OPTIONS.find((c) => c.value === category)?.label ?? category;
}

/** The actual service card/detail layout, rendered once and shared between the ServiceForm
 * preview step and the real public ServiceDetailPage — guarantees they visually match by
 * construction rather than by two hand-kept-in-sync implementations. */
export function ServiceDetailView({ data, provider }: { data: ServiceDetailViewData; provider: ServiceDetailViewProvider }) {
  const cover = data.media.find((m) => m.cover) ?? data.media[0];
  const coverUrl = cover ? (cover.mediaType === "IMAGE" ? cover.url : cover.thumbnailUrl) : null;
  const gallery = data.media.filter((m) => m.id !== cover?.id);

  return (
    <div className="space-y-4">
      <div className="relative aspect-[16/9] w-full overflow-hidden rounded-xl border border-border bg-muted">
        {coverUrl ? (
          <img src={cloudinaryThumb(coverUrl, 900) ?? coverUrl} alt="" className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full w-full items-center justify-center">
            <FolderKanban className="size-10 text-muted-foreground" />
          </div>
        )}
        {!data.available ? (
          <Badge variant="secondary" className="absolute top-3 left-3">
            Currently unavailable
          </Badge>
        ) : null}
      </div>

      <div className="space-y-2">
        <span className="w-fit rounded-full bg-accent px-2.5 py-0.5 text-xs font-medium text-primary">
          {categoryLabel(data.category)}
        </span>
        <h1 className="text-xl font-bold text-foreground">{data.title || "Untitled service"}</h1>

        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          {data.priceAmount != null ? (
            <span className="text-lg font-semibold text-foreground">
              ₹{data.priceAmount.toLocaleString("en-IN")}
            </span>
          ) : null}
          {data.deliveryDays != null ? (
            <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
              <Clock className="size-4" /> Delivery: {data.deliveryDays} day{data.deliveryDays === 1 ? "" : "s"}
            </span>
          ) : null}
          <span className="inline-flex items-center gap-1.5 text-sm">
            <span className={data.available ? "size-2 rounded-full bg-emerald-500" : "size-2 rounded-full bg-muted-foreground"} />
            {data.available ? "Available" : "Currently unavailable"}
          </span>
          {serviceLocationLabel(data.location)}
        </div>
      </div>

      {provider.slug ? (
        <Link
          to={ROUTES.publicProfile(provider.slug)}
          className="inline-flex items-center gap-2 rounded-lg border border-border px-2.5 py-1.5 hover:bg-muted/50"
        >
          <Avatar className="size-9">
            {provider.profileImageUrl ? <AvatarImage src={provider.profileImageUrl} alt={provider.name} /> : null}
            <AvatarFallback className="text-[10px]">{getInitials(provider.name)}</AvatarFallback>
          </Avatar>
          <span className="min-w-0">
            <span className="block text-sm font-medium text-foreground">{provider.name}</span>
            {provider.headline ? (
              <span className="block truncate text-xs text-muted-foreground">{provider.headline}</span>
            ) : null}
          </span>
        </Link>
      ) : (
        <div className="inline-flex items-center gap-2 rounded-lg border border-border px-2.5 py-1.5">
          <Avatar className="size-9">
            {provider.profileImageUrl ? <AvatarImage src={provider.profileImageUrl} alt={provider.name} /> : null}
            <AvatarFallback className="text-[10px]">{getInitials(provider.name)}</AvatarFallback>
          </Avatar>
          <span className="min-w-0">
            <span className="block text-sm font-medium text-foreground">{provider.name}</span>
            {provider.headline ? (
              <span className="block truncate text-xs text-muted-foreground">{provider.headline}</span>
            ) : null}
          </span>
        </div>
      )}

      {data.skills.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {data.skills.map((skill) => (
            <SkillChip key={skill.id} skill={skill} variant="compact" />
          ))}
        </div>
      ) : null}

      {data.description ? (
        <div className="space-y-1">
          <h2 className="text-sm font-semibold text-foreground">What I offer</h2>
          <p className="whitespace-pre-wrap text-sm text-muted-foreground">{data.description}</p>
        </div>
      ) : null}

      {data.whatYoullReceive.length > 0 ? (
        <div className="space-y-1">
          <h2 className="text-sm font-semibold text-foreground">What you&apos;ll receive</h2>
          <ul className="list-inside list-disc space-y-0.5 text-sm text-muted-foreground">
            {data.whatYoullReceive.map((item, i) => (
              <li key={i}>{item}</li>
            ))}
          </ul>
        </div>
      ) : null}

      {data.linkedProjects.length > 0 ? (
        <div className="space-y-2">
          <h2 className="text-sm font-semibold text-foreground">Previous work</h2>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {data.linkedProjects.map((project) => {
              const thumb = (
                <div className="aspect-square w-full bg-muted">
                  {project.coverImageUrl ? (
                    <img
                      src={cloudinaryThumb(project.coverImageUrl, 300) ?? project.coverImageUrl}
                      alt=""
                      loading="lazy"
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-full w-full items-center justify-center">
                      <FolderKanban className="size-6 text-muted-foreground" />
                    </div>
                  )}
                </div>
              );
              const title = <p className="truncate p-1.5 text-xs font-medium text-foreground">{project.title}</p>;

              return provider.slug ? (
                <Link
                  key={project.id}
                  to={ROUTES.publicProject(provider.slug, project.id)}
                  className="overflow-hidden rounded-lg border border-border bg-muted hover:border-primary/50"
                >
                  {thumb}
                  {title}
                </Link>
              ) : (
                <div key={project.id} className="overflow-hidden rounded-lg border border-border bg-muted">
                  {thumb}
                  {title}
                </div>
              );
            })}
          </div>
        </div>
      ) : null}

      {gallery.length > 0 ? (
        <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
          {gallery.map((m) => (
            <div key={m.id} className="aspect-square overflow-hidden rounded-lg border border-border bg-muted">
              <img
                src={cloudinaryThumb(m.mediaType === "IMAGE" ? m.url : m.thumbnailUrl, 300) ?? m.url}
                alt=""
                loading="lazy"
                className="h-full w-full object-cover"
              />
            </div>
          ))}
        </div>
      ) : null}

      {data.links.length > 0 ? (
        <div className="flex flex-wrap gap-2">
          {data.links.map((link) => (
            <a
              key={link.url}
              href={link.url}
              target="_blank"
              rel="noreferrer noopener"
              className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-muted/50"
            >
              {link.label} <ExternalLink className="size-3.5" />
            </a>
          ))}
        </div>
      ) : null}

    </div>
  );
}

export function serviceLocationLabel(location: string | null) {
  return location ? (
    <span className="inline-flex items-center gap-1 text-sm text-muted-foreground">
      <MapPin className="size-4" /> {location}
    </span>
  ) : null;
}
