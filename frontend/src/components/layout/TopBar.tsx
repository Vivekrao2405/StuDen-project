import { Bell, MessageCircle, Search } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { UserMenu } from "@/components/layout/UserMenu";
import { ROUTES } from "@/lib/routes";

export function TopBar() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");

  function handleSearchSubmit(e: React.FormEvent) {
    e.preventDefault();
    // There's no search backend yet — the marketplace is the closest real destination for a
    // students/services/skills search, and it's an honest "coming soon" page rather than a dead
    // decorative box.
    navigate(ROUTES.marketplace);
  }

  return (
    <header className="sticky top-0 z-40 hidden h-16 items-center gap-4 border-b border-border bg-background/95 px-6 backdrop-blur supports-backdrop-filter:bg-background/80 lg:flex">
      <form onSubmit={handleSearchSubmit} className="max-w-md flex-1">
        <div className="relative">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Search students, services, skills..."
            className="h-10 rounded-full pr-10 pl-4"
          />
          <button
            type="submit"
            aria-label="Search"
            className="absolute top-1/2 right-3 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          >
            <Search className="size-4" />
          </button>
        </div>
      </form>

      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" aria-label="Notifications" onClick={() => navigate(ROUTES.notifications)}>
          <Bell />
        </Button>
        <Button variant="ghost" size="icon" aria-label="Messages" onClick={() => navigate(ROUTES.messages)}>
          <MessageCircle />
        </Button>
        <UserMenu variant="full" />
      </div>
    </header>
  );
}
