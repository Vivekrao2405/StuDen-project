import { Logo } from "@/components/layout/Logo";
import { BrandName } from "@/components/shared/BrandName";

export function MarketingFooter() {
  return (
    <footer className="border-t border-border">
      <div className="mx-auto flex max-w-7xl flex-col items-center gap-3 px-4 py-10 text-center sm:px-6 lg:px-8">
        <Logo size="sm" showIcon={false} />
        <p className="text-sm text-muted-foreground">
          The student marketplace — find a student who can do it, or get hired for what you do best.
        </p>
        <p className="text-xs text-muted-foreground">
          © {new Date().getFullYear()} <BrandName />. All rights reserved.
        </p>
      </div>
    </footer>
  );
}
