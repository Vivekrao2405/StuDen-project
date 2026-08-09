import { LayoutDashboard, LogOut, Settings, Share2 } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) return null;

  function handleLogout() {
    // No explicit navigate: ProtectedRoute already redirects to /login once status
    // flips to guest (and an explicit navigate() call here loses that race anyway,
    // since react-router's navigate is lower-priority than this plain state update).
    // On a public page there's nothing to redirect away from.
    logout();
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger className="rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
        <Avatar>
          {user!.profileImageUrl ? <AvatarImage src={user!.profileImageUrl} alt={user!.fullName} /> : null}
          <AvatarFallback>{getInitials(user!.fullName)}</AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => navigate(ROUTES.profile)}>
          <LayoutDashboard /> My Portfolio
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => navigate(ROUTES.shareProfile)}>
          <Share2 /> Share Profile
        </DropdownMenuItem>
        <DropdownMenuItem onClick={() => navigate(ROUTES.settings)}>
          <Settings /> Settings
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem variant="destructive" onClick={handleLogout}>
          <LogOut /> Log out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
