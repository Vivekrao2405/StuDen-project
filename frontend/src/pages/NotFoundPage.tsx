import { SearchX } from "lucide-react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { ROUTES } from "@/lib/routes";

export function NotFoundPage() {
  return (
    <div className="flex min-h-[70svh] flex-col items-center justify-center px-4 text-center">
      <div className="flex size-16 items-center justify-center rounded-full bg-accent">
        <SearchX className="size-7 text-accent-foreground" />
      </div>
      <p className="mt-6 text-6xl font-bold text-foreground">404</p>
      <h1 className="mt-2 text-lg font-semibold text-foreground">Page not found</h1>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">
        The page you're looking for doesn't exist or has been moved.
      </p>
      <Button className="mt-6" render={<Link to={ROUTES.home} />}>
        Go Home
      </Button>
    </div>
  );
}
