import { ArrowLeft, ExternalLink, MessageCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useFocusedResource } from "@/features/notifications/useFocusedResource";
import { ApiError } from "@/lib/api/ApiError";
import { completeOrder, getOrder } from "@/lib/api/endpoints/orders";
import { getOrCreateConversation } from "@/lib/api/endpoints/messaging";
import { getMyPortfolio } from "@/lib/api/endpoints/portfolio";
import { useAsync } from "@/lib/hooks/useAsync";
import { ROUTES } from "@/lib/routes";
import { CancelOrderDialog } from "@/pages/orders/CancelOrderDialog";
import { orderStatusLabel, orderStatusVariant } from "@/pages/orders/orderStatusBadge";
import { OrderTimeline } from "@/pages/orders/OrderTimeline";
import { SubmitWorkDialog } from "@/pages/orders/SubmitWorkDialog";

function getInitials(fullName: string) {
  return fullName
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");
}

function formatDate(value: string) {
  return new Date(value).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

export function OrderDetailPage() {
  const { orderId = "" } = useParams<{ orderId: string }>();
  const navigate = useNavigate();
  const { setFocusedResource } = useFocusedResource();
  const { data: order, error, loading, refetch } = useAsync(() => getOrder(orderId), [orderId]);

  useEffect(() => {
    if (!orderId) return;
    setFocusedResource({ type: "ORDER", resourceId: orderId });
    return () => setFocusedResource(null);
  }, [orderId, setFocusedResource]);
  const [submitDialogOpen, setSubmitDialogOpen] = useState(false);
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [openingConversation, setOpeningConversation] = useState(false);
  const [messageError, setMessageError] = useState<string | null>(null);

  // Same "compare against my own portfolio slug" technique as ServiceRequestDetailPage — the
  // response never exposes raw requester/provider ids, so this is how the page tells which side
  // of the order the viewer is on.
  const [ownProviderSlug, setOwnProviderSlug] = useState<string | null>(null);
  useEffect(() => {
    let cancelled = false;
    getMyPortfolio()
      .then((portfolio) => {
        if (!cancelled) setOwnProviderSlug(portfolio.publicSlug);
      })
      .catch(() => {
        if (!cancelled) setOwnProviderSlug(null);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function handleMessage() {
    if (!order) return;
    setMessageError(null);
    setOpeningConversation(true);
    try {
      const conversation = await getOrCreateConversation(order.serviceRequestId);
      navigate(ROUTES.conversationDetail(conversation.id));
    } catch (err) {
      setMessageError(err instanceof ApiError ? err.message : "Couldn't open the conversation. Please try again.");
      setOpeningConversation(false);
    }
  }

  async function handleComplete() {
    setActionError(null);
    setCompleting(true);
    try {
      await completeOrder(orderId);
      refetch();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Couldn't mark this order as completed.");
    } finally {
      setCompleting(false);
    }
  }

  if (loading) {
    return <LoadingState label="Loading order..." />;
  }

  if (error || !order) {
    return (
      <div className="mx-auto max-w-2xl px-4 py-16">
        <ErrorState
          title="Order not found"
          message="This order doesn't exist, or you don't have access to it."
          onRetry={refetch}
        />
      </div>
    );
  }

  const isProvider = ownProviderSlug != null && ownProviderSlug === order.providerSlug;
  const amount = order.proposedBudget ?? order.servicePriceAmount;

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-10 sm:px-6 lg:px-8">
      <Link
        to={ROUTES.orders}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="size-4" /> Back to Orders
      </Link>

      <Card>
        <CardContent className="space-y-5 pt-4">
          <div className="flex items-start justify-between gap-2">
            <div>
              <h1 className="text-lg font-semibold text-foreground">{order.serviceTitle}</h1>
              {amount != null ? <p className="text-sm text-muted-foreground">₹{amount.toLocaleString("en-IN")}</p> : null}
            </div>
            <Badge variant={orderStatusVariant(order.status)}>{orderStatusLabel(order.status)}</Badge>
          </div>

          {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="flex items-center gap-2 rounded-lg border border-border p-3">
              <Avatar className="size-8 shrink-0">
                {order.providerProfileImageUrl ? <AvatarImage src={order.providerProfileImageUrl} alt={order.providerName} /> : null}
                <AvatarFallback className="text-xs">{getInitials(order.providerName)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-muted-foreground">Provider</p>
                {order.providerSlug ? (
                  <Link
                    to={ROUTES.publicProfile(order.providerSlug)}
                    className="truncate text-sm font-medium text-foreground hover:text-primary"
                  >
                    {order.providerName}
                  </Link>
                ) : (
                  <p className="truncate text-sm font-medium text-foreground">{order.providerName}</p>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2 rounded-lg border border-border p-3">
              <Avatar className="size-8 shrink-0">
                {order.requesterProfileImageUrl ? (
                  <AvatarImage src={order.requesterProfileImageUrl} alt={order.requesterName} />
                ) : null}
                <AvatarFallback className="text-xs">{getInitials(order.requesterName)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <p className="text-xs text-muted-foreground">Requester</p>
                {order.requesterSlug ? (
                  <Link
                    to={ROUTES.publicProfile(order.requesterSlug)}
                    className="truncate text-sm font-medium text-foreground hover:text-primary"
                  >
                    {order.requesterName}
                  </Link>
                ) : (
                  <p className="truncate text-sm font-medium text-foreground">{order.requesterName}</p>
                )}
              </div>
            </div>
          </div>

          <div className="border-t border-border pt-4">
            {messageError ? <p className="mb-2 text-sm text-destructive">{messageError}</p> : null}
            <Button size="sm" variant="outline" onClick={handleMessage} disabled={openingConversation}>
              <MessageCircle className="size-4" />
              {openingConversation ? "Opening..." : isProvider ? "Message Requester" : "Message Provider"}
            </Button>
          </div>

          <div>
            <h2 className="text-sm font-semibold text-foreground">Requirements</h2>
            <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{order.requirements}</p>
          </div>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <h2 className="text-sm font-semibold text-foreground">Expected delivery</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {order.requestedDeliveryDate ? formatDate(order.requestedDeliveryDate) : "Not specified"}
              </p>
            </div>
            <div>
              <h2 className="text-sm font-semibold text-foreground">Started on</h2>
              <p className="mt-1 text-sm text-muted-foreground">{formatDate(order.createdAt)}</p>
            </div>
          </div>

          {order.submissionDescription ? (
            <div className="rounded-lg border border-border bg-muted/30 p-3">
              <h2 className="text-sm font-semibold text-foreground">Submitted work</h2>
              <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">{order.submissionDescription}</p>
              {order.submissionLink ? (
                <a
                  href={order.submissionLink}
                  target="_blank"
                  rel="noreferrer noopener"
                  className="mt-2 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                >
                  View link <ExternalLink className="size-3" />
                </a>
              ) : null}
            </div>
          ) : null}

          <div>
            <h2 className="mb-2 text-sm font-semibold text-foreground">Activity</h2>
            <OrderTimeline order={order} />
          </div>

          {order.status === "IN_PROGRESS" || order.status === "WORK_SUBMITTED" ? (
            <div className="flex flex-wrap gap-2 border-t border-border pt-4">
              {isProvider && order.status === "IN_PROGRESS" ? (
                <Button size="sm" onClick={() => setSubmitDialogOpen(true)}>
                  Submit Work
                </Button>
              ) : null}
              {!isProvider && order.status === "WORK_SUBMITTED" ? (
                <Button size="sm" onClick={handleComplete} disabled={completing}>
                  {completing ? "Marking..." : "Mark as Completed"}
                </Button>
              ) : null}
              <Button size="sm" variant="outline" onClick={() => setCancelDialogOpen(true)}>
                Cancel Order
              </Button>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <SubmitWorkDialog
        open={submitDialogOpen}
        onOpenChange={setSubmitDialogOpen}
        orderId={order.id}
        onSuccess={() => {
          setSubmitDialogOpen(false);
          refetch();
        }}
      />
      <CancelOrderDialog
        open={cancelDialogOpen}
        onOpenChange={setCancelDialogOpen}
        orderId={order.id}
        onSuccess={() => {
          setCancelDialogOpen(false);
          refetch();
        }}
      />
    </div>
  );
}
