import { Link } from "react-router-dom";

import { cn } from "@/lib/utils";
import logoMark from "@/assets/logo-mark.png";

const TEXT_SIZE_CLASSES = {
  sm: "text-lg",
  md: "text-xl",
  lg: "text-2xl",
};

const ICON_SIZE_CLASSES = {
  sm: "size-6",
  md: "size-7",
  lg: "size-9",
};

export function Logo({
  size = "md",
  showIcon = true,
  className,
}: {
  size?: keyof typeof TEXT_SIZE_CLASSES;
  showIcon?: boolean;
  className?: string;
}) {
  return (
    <Link to="/" className={cn("flex items-center gap-2", className)}>
      {showIcon ? (
        <img src={logoMark} alt="StuDen" className={cn("shrink-0 rounded-md", ICON_SIZE_CLASSES[size])} />
      ) : null}
      <span className={cn("font-heading font-bold tracking-tight text-foreground", TEXT_SIZE_CLASSES[size])}>
        Stu<span className="text-primary">Den</span>
      </span>
    </Link>
  );
}
