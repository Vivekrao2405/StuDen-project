import { Code2 } from "lucide-react";
import { useEffect, useState } from "react";

import { cn } from "@/lib/utils";

interface SkillIconProps {
  iconSlug: string | null;
  className?: string;
}

/**
 * Renders a skill's brand icon from simple-icons' CDN (stable per-slug URLs, no bundled/base64
 * assets to maintain). Falls back to a generic icon whenever a skill has no iconSlug, or the CDN
 * request fails — a skill can never render a broken image.
 */
export function SkillIcon({ iconSlug, className }: SkillIconProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [iconSlug]);

  if (!iconSlug || failed) {
    return (
      <span className={cn("flex items-center justify-center text-muted-foreground", className)}>
        <Code2 className="size-full" strokeWidth={1.75} />
      </span>
    );
  }

  return (
    <img
      src={`https://cdn.simpleicons.org/${iconSlug}`}
      alt=""
      aria-hidden="true"
      className={cn("object-contain", className)}
      onError={() => setFailed(true)}
    />
  );
}
