import { Search, ShieldAlert, Users as UsersIcon } from "lucide-react";
import { useEffect, useState } from "react";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { useAuth } from "@/features/auth/useAuth";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  deactivateAdminUser,
  deleteAdminUserPermanently,
  getAdminUser,
  listAdminUsers,
  restoreAdminUser,
} from "@/lib/api/endpoints/adminUsers";
import type { AdminUserStatus, AdminUserSummary } from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { MarketplacePagination } from "@/pages/marketplace/MarketplacePagination";

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 300;
const DELETE_CONFIRMATION_TEXT = "DELETE";

function statusBadgeVariant(status: AdminUserStatus): "default" | "secondary" | "destructive" {
  switch (status) {
    case "ACTIVE":
      return "default";
    case "DEACTIVATED":
      return "secondary";
    case "DELETED":
      return "destructive";
  }
}

function initials(fullName: string) {
  return fullName
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

export function UserManagementPage() {
  const { user: currentUser } = useAuth();
  const toast = useToast();

  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setDebouncedQuery(query.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [query]);

  const list = useAsync(
    () => listAdminUsers({ search: debouncedQuery || undefined, page, size: PAGE_SIZE }),
    [debouncedQuery, page]
  );

  function handleActionComplete() {
    setSelectedUserId(null);
    list.refetch();
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">User Management</h1>
        <p className="text-sm text-muted-foreground">Search, view, and manage student accounts.</p>
      </div>

      <div className="relative">
        <Search className="absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by name, email, or username..."
          className="h-11 rounded-full pl-10"
        />
      </div>

      {list.loading ? (
        <div className="space-y-2" aria-hidden="true">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-xl" />
          ))}
        </div>
      ) : list.error ? (
        <ErrorState message={list.error.message} onRetry={list.refetch} />
      ) : list.data && list.data.content.length > 0 ? (
        <>
          <div className="space-y-2">
            {list.data.content.map((u) => (
              <UserRow key={u.id} user={u} onView={() => setSelectedUserId(u.id)} />
            ))}
          </div>
          <MarketplacePagination page={list.data.page} totalPages={list.data.totalPages} onPageChange={setPage} />
        </>
      ) : (
        <EmptyState
          icon={UsersIcon}
          title="No users found"
          description="Try adjusting your search."
        />
      )}

      {selectedUserId ? (
        <UserDetailDialog
          userId={selectedUserId}
          currentUserId={currentUser?.id ?? null}
          onClose={() => setSelectedUserId(null)}
          onActionComplete={handleActionComplete}
          toast={toast}
        />
      ) : null}
    </div>
  );
}

function UserRow({ user, onView }: { user: AdminUserSummary; onView: () => void }) {
  return (
    <div className="flex flex-col gap-3 rounded-xl border border-border bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 items-center gap-3">
        <Avatar>
          <AvatarImage src={user.profileImageUrl ?? undefined} alt={user.fullName} />
          <AvatarFallback>{initials(user.fullName)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0">
          <p className="truncate text-sm font-medium text-foreground">{user.fullName}</p>
          <p className="truncate text-xs text-muted-foreground">{user.email}</p>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <Badge variant={user.role === "ADMIN" ? "outline" : "secondary"}>{user.role}</Badge>
        <Badge variant={statusBadgeVariant(user.status)}>{user.status}</Badge>
        <Button size="sm" variant="outline" onClick={onView}>
          View
        </Button>
      </div>
    </div>
  );
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
}

function UserDetailDialog({
  userId,
  currentUserId,
  onClose,
  onActionComplete,
  toast,
}: {
  userId: string;
  currentUserId: string | null;
  onClose: () => void;
  onActionComplete: () => void;
  toast: ToastApi;
}) {
  const detail = useAsync(() => getAdminUser(userId), [userId]);
  const [submitting, setSubmitting] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const isSelf = currentUserId !== null && currentUserId === userId;

  async function runAction(action: () => Promise<void>, successMessage: string) {
    setSubmitting(true);
    try {
      await action();
      toast.success(successMessage);
      onActionComplete();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <Dialog open onOpenChange={(open) => !open && onClose()}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>User Details</DialogTitle>
          </DialogHeader>

          {detail.loading ? (
            <div className="space-y-2">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-4 w-1/3" />
            </div>
          ) : detail.error ? (
            <ErrorState message={detail.error.message} onRetry={detail.refetch} />
          ) : detail.data ? (
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <Avatar size="lg">
                  <AvatarImage src={detail.data.profileImageUrl ?? undefined} alt={detail.data.fullName} />
                  <AvatarFallback>{initials(detail.data.fullName)}</AvatarFallback>
                </Avatar>
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-foreground">{detail.data.fullName}</p>
                  <p className="truncate text-xs text-muted-foreground">{detail.data.email}</p>
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <Badge variant={detail.data.role === "ADMIN" ? "outline" : "secondary"}>{detail.data.role}</Badge>
                <Badge variant={statusBadgeVariant(detail.data.status)}>{detail.data.status}</Badge>
                {detail.data.emailVerified ? <Badge variant="outline">Email verified</Badge> : null}
              </div>

              <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
                <dt className="text-muted-foreground">Phone</dt>
                <dd className="text-foreground">{detail.data.phone ?? "—"}</dd>
                <dt className="text-muted-foreground">University</dt>
                <dd className="text-foreground">{detail.data.university ?? "—"}</dd>
                <dt className="text-muted-foreground">Username</dt>
                <dd className="text-foreground">{detail.data.publicSlug ?? "—"}</dd>
                <dt className="text-muted-foreground">Joined</dt>
                <dd className="text-foreground">{new Date(detail.data.createdAt).toLocaleDateString()}</dd>
                {detail.data.deletedAt ? (
                  <>
                    <dt className="text-muted-foreground">Deleted</dt>
                    <dd className="text-foreground">{new Date(detail.data.deletedAt).toLocaleDateString()}</dd>
                  </>
                ) : null}
              </dl>

              {isSelf ? (
                <p className="text-xs text-muted-foreground">This is your own admin account — it can't be managed here.</p>
              ) : detail.data.role === "ADMIN" ? (
                <p className="text-xs text-muted-foreground">Admin accounts can't be managed from User Management.</p>
              ) : (
                <div className="space-y-3">
                  <div className="flex flex-wrap gap-2">
                    {detail.data.status === "ACTIVE" ? (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={submitting}
                        onClick={() =>
                          runAction(() => deactivateAdminUser(userId), "Account deactivated.")
                        }
                      >
                        Deactivate
                      </Button>
                    ) : null}
                    {detail.data.status === "DEACTIVATED" ? (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={submitting}
                        onClick={() => runAction(() => restoreAdminUser(userId), "Account restored.")}
                      >
                        Restore
                      </Button>
                    ) : null}
                  </div>

                  {detail.data.status !== "DELETED" ? (
                    <>
                      <Separator />
                      <div className="space-y-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3">
                        <div className="flex items-center gap-1.5 text-xs font-medium text-destructive">
                          <ShieldAlert className="size-3.5" /> Danger zone
                        </div>
                        <Button
                          size="sm"
                          variant="destructive"
                          disabled={submitting}
                          onClick={() => setDeleteDialogOpen(true)}
                        >
                          Delete Permanently
                        </Button>
                      </div>
                    </>
                  ) : null}
                </div>
              )}
            </div>
          ) : null}
        </DialogContent>
      </Dialog>

      {detail.data ? (
        <PermanentDeleteDialog
          open={deleteDialogOpen}
          onOpenChange={setDeleteDialogOpen}
          userName={detail.data.fullName}
          onConfirm={() =>
            runAction(() => deleteAdminUserPermanently(userId, DELETE_CONFIRMATION_TEXT), "Account permanently deleted.")
          }
        />
      ) : null}
    </>
  );
}

function PermanentDeleteDialog({
  open,
  onOpenChange,
  userName,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userName: string;
  onConfirm: () => Promise<void>;
}) {
  const [confirmText, setConfirmText] = useState("");
  const [deleting, setDeleting] = useState(false);
  const canConfirm = confirmText === DELETE_CONFIRMATION_TEXT;

  useEffect(() => {
    if (!open) setConfirmText("");
  }, [open]);

  async function handleConfirm() {
    if (!canConfirm) return;
    setDeleting(true);
    try {
      await onConfirm();
      onOpenChange(false);
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Delete this account permanently?</DialogTitle>
          <DialogDescription>
            This action cannot be undone. {userName}'s account and associated deletable data will be removed
            according to StuDen's data-retention rules. Conversations, orders, and assessment history involving
            other users are preserved.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-1.5">
          <label htmlFor="delete-confirmation" className="text-xs font-medium text-muted-foreground">
            Type <span className="font-mono font-semibold text-foreground">DELETE</span> to confirm.
          </label>
          <Input
            id="delete-confirmation"
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder="DELETE"
            autoComplete="off"
          />
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={deleting}>
            Cancel
          </Button>
          <Button variant="destructive" onClick={handleConfirm} disabled={!canConfirm || deleting}>
            {deleting ? "Deleting..." : "Delete Permanently"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
