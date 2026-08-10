import { ImageIcon } from "lucide-react";
import { useEffect, useState } from "react";

import { cn } from "@/lib/utils";

interface CoverImageProps {
  src: string | null | undefined;
  alt: string;
  className?: string;
}

export function CoverImage({ src, alt, className }: CoverImageProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  // A wide banner ratio crops heavily once the container narrows (mobile), cutting off
  // faces/heads in portrait photos. Scaling from a taller ratio on small screens down to
  // the wide "professional banner" look on desktop keeps more of the subject in frame,
  // and object-top biases any remaining crop toward where a face usually sits.
  const aspectClasses = "aspect-[4/3] sm:aspect-[2/1] lg:aspect-[3/1]";

  if (!src || failed) {
    return (
      <div
        role="img"
        aria-label={`${alt} (no cover image)`}
        className={cn("flex w-full items-center justify-center bg-muted", aspectClasses, className)}
      >
        <ImageIcon className="size-8 text-muted-foreground" />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      className={cn("w-full object-cover object-top", aspectClasses, className)}
      onError={() => setFailed(true)}
    />
  );
}
