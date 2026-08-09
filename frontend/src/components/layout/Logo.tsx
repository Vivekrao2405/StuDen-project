import { Link } from "react-router-dom";

import { cn } from "@/lib/utils";

const SIZE_CLASSES = {
  sm: "text-lg",
  md: "text-xl",
  lg: "text-2xl",
};

export function Logo({ size = "md", className }: { size?: keyof typeof SIZE_CLASSES; className?: string }) {
  return (
    <Link
      to="/"
      className={cn("font-heading font-bold tracking-tight text-foreground", SIZE_CLASSES[size], className)}
    >
      Stu<span className="text-primary">Den</span>
    </Link>
  );
}
