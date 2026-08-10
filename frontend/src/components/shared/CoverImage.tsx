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

  if (!src || failed) {
    return (
      <div
        role="img"
        aria-label={`${alt} (no cover image)`}
        className={cn("flex aspect-[3/1] w-full items-center justify-center bg-muted", className)}
      >
        <ImageIcon className="size-8 text-muted-foreground" />
      </div>
    );
  }

  return (
    <img
      src={src}
      alt={alt}
      className={cn("aspect-[3/1] w-full object-cover", className)}
      onError={() => setFailed(true)}
    />
  );
}
