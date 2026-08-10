import { cn } from "@/lib/utils";

export function BrandName({ className }: { className?: string }) {
  return (
    <span className={cn("font-semibold", className)}>
      Stu<span className="text-primary">Den</span>
    </span>
  );
}
