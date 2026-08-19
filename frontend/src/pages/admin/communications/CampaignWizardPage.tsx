import { ArrowLeft, ArrowRight, Loader2, Mail, MessageSquare, Send, Smartphone } from "lucide-react";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import { ErrorState } from "@/components/shared/ErrorState";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import {
  createCampaign,
  getCampaign,
  listTemplates,
  previewAudience,
  scheduleCampaign,
  sendCampaignNow,
  updateCampaign,
} from "@/lib/api/endpoints/communications";
import type { AudienceCondition, CampaignRequest, CommunicationCategory, TemplateResponse } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";
import {
  AudienceBuilderStep,
  parseAudienceFilter,
  serializeAudienceFilter,
} from "@/pages/admin/communications/AudienceBuilderStep";
import { CATEGORY_LABEL, CATEGORY_OPTIONS } from "@/pages/admin/communications/communicationsDisplay";
import { MessageComposerStep, type MessageFields } from "@/pages/admin/communications/MessageComposerStep";
import { QB_SELECT_CLASS } from "@/pages/admin/questionBankSelectClass";

const STEPS = ["Audience", "Message", "Channels", "Preview", "Send"] as const;
const EMPTY_MESSAGE: MessageFields = {
  emailSubject: "",
  emailBodyHtml: "",
  pushTitle: "",
  pushBody: "",
  inappTitle: "",
  inappBody: "",
  ctaText: "",
  ctaUrl: "",
};
const SAMPLE_TOKEN_VALUES: Record<string, string> = {
  firstName: "Alex",
  lastName: "Rivera",
  skillName: "React",
  assessmentName: "React Fundamentals",
  score: "88",
  rank: "3",
  challengeName: "Summer Build Challenge",
  roadmapName: "Frontend Roadmap",
};

function withSampleValues(text: string): string {
  return text.replace(/\{\{(\w+)\}\}/g, (match, token: string) => SAMPLE_TOKEN_VALUES[token] ?? match);
}

export function CampaignWizardPage() {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const isEditing = Boolean(id);

  const [step, setStep] = useState(0);
  const [loading, setLoading] = useState(isEditing);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [templates, setTemplates] = useState<TemplateResponse[]>([]);
  const [campaignId, setCampaignId] = useState<string | null>(id ?? null);

  const [name, setName] = useState("");
  const [category, setCategory] = useState<CommunicationCategory>("CUSTOM");
  const [marketing, setMarketing] = useState(false);
  const [conditions, setConditions] = useState<AudienceCondition[]>([]);
  const [templateId, setTemplateId] = useState<string>("");
  const [sendEmail, setSendEmail] = useState(true);
  const [sendPush, setSendPush] = useState(true);
  const [sendInapp, setSendInapp] = useState(true);
  const [message, setMessage] = useState<MessageFields>(EMPTY_MESSAGE);

  const [finalPreview, setFinalPreview] = useState<{ count: number } | null>(null);
  const [scheduleAt, setScheduleAt] = useState("");

  useEffect(() => {
    listTemplates(false)
      .then(setTemplates)
      .catch(() => setTemplates([]));
  }, []);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    getCampaign(id)
      .then((c) => {
        if (c.status !== "DRAFT") {
          // Only DRAFT campaigns can be edited (backend rejects a PATCH otherwise) — send
          // everyone straight to the read-only analytics view instead.
          navigate(ROUTES.adminCommunicationsCampaignAnalytics(id), { replace: true });
          return;
        }
        setCampaignId(c.id);
        setName(c.name);
        setCategory(c.category);
        setMarketing(c.marketing);
        setConditions(parseAudienceFilter(c.filterJson));
        setSendEmail(c.sendEmail);
        setSendPush(c.sendPush);
        setSendInapp(c.sendInapp);
        setMessage({
          emailSubject: c.emailSubject ?? "",
          emailBodyHtml: c.emailBodyHtml ?? "",
          pushTitle: c.pushTitle ?? "",
          pushBody: c.pushBody ?? "",
          inappTitle: c.inappTitle ?? "",
          inappBody: c.inappBody ?? "",
          ctaText: c.ctaText ?? "",
          ctaUrl: c.ctaUrl ?? "",
        });
      })
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : "Failed to load campaign."))
      .finally(() => setLoading(false));
  }, [id, navigate]);

  useEffect(() => {
    if (step !== 3) return;
    previewAudience(serializeAudienceFilter(conditions))
      .then((r) => setFinalPreview({ count: r.count }))
      .catch(() => setFinalPreview(null));
  }, [step, conditions]);

  function applyTemplate(templateId: string) {
    const t = templates.find((tpl) => tpl.id === templateId);
    if (!t) return;
    setMessage({
      emailSubject: t.emailSubject ?? "",
      emailBodyHtml: t.emailBodyHtml ?? "",
      pushTitle: t.pushTitle ?? "",
      pushBody: t.pushBody ?? "",
      inappTitle: t.inappTitle ?? "",
      inappBody: t.inappBody ?? "",
      ctaText: t.ctaText ?? "",
      ctaUrl: t.ctaUrl ?? "",
    });
  }

  function buildRequest(): CampaignRequest {
    return {
      name,
      category,
      marketing,
      filterJson: serializeAudienceFilter(conditions),
      templateId: templateId || null,
      segmentId: null,
      sendEmail,
      sendPush,
      sendInapp,
      emailSubject: message.emailSubject || null,
      emailBodyHtml: message.emailBodyHtml || null,
      pushTitle: message.pushTitle || null,
      pushBody: message.pushBody || null,
      inappTitle: message.inappTitle || null,
      inappBody: message.inappBody || null,
      ctaText: message.ctaText || null,
      ctaUrl: message.ctaUrl || null,
    };
  }

  async function persistDraft(): Promise<string> {
    const request = buildRequest();
    if (campaignId) {
      const updated = await updateCampaign(campaignId, request);
      return updated.id;
    }
    const created = await createCampaign(request);
    setCampaignId(created.id);
    return created.id;
  }

  async function handleSaveDraft() {
    setSubmitting(true);
    try {
      await persistDraft();
      toast.success("Campaign saved as draft.");
      navigate(ROUTES.adminCommunications);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to save draft.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSendNow() {
    setSubmitting(true);
    try {
      const savedId = await persistDraft();
      await sendCampaignNow(savedId);
      toast.success("Campaign is sending now.");
      navigate(ROUTES.adminCommunications);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to send campaign.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleSchedule() {
    if (!scheduleAt) {
      toast.error("Pick a date and time to schedule this campaign.");
      return;
    }
    setSubmitting(true);
    try {
      const savedId = await persistDraft();
      await scheduleCampaign(savedId, new Date(scheduleAt).toISOString());
      toast.success("Campaign scheduled.");
      navigate(ROUTES.adminCommunications);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Failed to schedule campaign.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-64 w-full rounded-xl" />
      </div>
    );
  }

  if (loadError) {
    return <ErrorState message={loadError} />;
  }

  const canGoNextFromStep0 = name.trim().length > 0;
  const canGoNextFromStep2 = sendEmail || sendPush || sendInapp;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">{isEditing ? "Edit Campaign" : "New Campaign"}</h1>
        <p className="text-sm text-muted-foreground">Build an audience, compose a message, and send or schedule it.</p>
      </div>

      <div className="flex items-center gap-1 overflow-x-auto">
        {STEPS.map((label, i) => (
          <div key={label} className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => i < step && setStep(i)}
              disabled={i >= step}
              className={`flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium whitespace-nowrap ${
                i === step
                  ? "bg-primary text-primary-foreground"
                  : i < step
                    ? "bg-muted text-foreground hover:bg-muted/70"
                    : "bg-muted/40 text-muted-foreground"
              }`}
            >
              {i + 1}. {label}
            </button>
            {i < STEPS.length - 1 ? <div className="h-px w-4 bg-border" /> : null}
          </div>
        ))}
      </div>

      {step === 0 ? (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Campaign name</label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Spring assessment reminder" />
            </div>
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Category</label>
              <select value={category} onChange={(e) => setCategory(e.target.value as CommunicationCategory)} className={QB_SELECT_CLASS}>
                {CATEGORY_OPTIONS.map((c) => (
                  <option key={c} value={c}>
                    {CATEGORY_LABEL[c]}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="flex items-center justify-between rounded-xl border border-border bg-card px-4 py-3">
            <div>
              <p className="text-sm font-medium text-foreground">Marketing campaign</p>
              <p className="text-xs text-muted-foreground">Students who opted out of marketing messages are excluded automatically.</p>
            </div>
            <Switch checked={marketing} onCheckedChange={setMarketing} />
          </div>
          <AudienceBuilderStep conditions={conditions} onChange={setConditions} />
        </div>
      ) : null}

      {step === 1 ? (
        <div className="space-y-4">
          {templates.length > 0 ? (
            <div className="space-y-1">
              <label className="text-xs font-medium text-muted-foreground">Load from template (optional)</label>
              <select
                value={templateId}
                onChange={(e) => {
                  setTemplateId(e.target.value);
                  if (e.target.value) applyTemplate(e.target.value);
                }}
                className={QB_SELECT_CLASS}
              >
                <option value="">None — write from scratch</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>
          ) : null}
          <MessageComposerStep value={message} onChange={setMessage} sendEmail={sendEmail} sendPush={sendPush} sendInapp={sendInapp} />
        </div>
      ) : null}

      {step === 2 ? (
        <div className="space-y-3">
          <ChannelToggle icon={Mail} label="Email" description="Sent via Resend to the student's account email." checked={sendEmail} onCheckedChange={setSendEmail} />
          <ChannelToggle icon={Smartphone} label="Push Notification" description="Sent to any subscribed device." checked={sendPush} onCheckedChange={setSendPush} />
          <ChannelToggle icon={MessageSquare} label="In-App Notification" description="Appears in the student's notification center." checked={sendInapp} onCheckedChange={setSendInapp} />
          {!canGoNextFromStep2 ? <p className="text-sm text-destructive">Select at least one channel.</p> : null}
        </div>
      ) : null}

      {step === 3 ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-border bg-card px-4 py-3 text-sm text-foreground">
            <span className="font-semibold">{finalPreview ? finalPreview.count.toLocaleString() : "…"}</span> students will
            receive this campaign.
          </div>
          {sendEmail ? (
            <PreviewCard title="Email" primary={withSampleValues(message.emailSubject)} body={withSampleValues(message.emailBodyHtml)} />
          ) : null}
          {sendPush ? (
            <PreviewCard title="Push Notification" primary={withSampleValues(message.pushTitle)} body={withSampleValues(message.pushBody)} />
          ) : null}
          {sendInapp ? (
            <PreviewCard title="In-App Notification" primary={withSampleValues(message.inappTitle)} body={withSampleValues(message.inappBody)} />
          ) : null}
          {message.ctaText ? (
            <p className="text-sm text-muted-foreground">
              Call to action: <span className="font-medium text-foreground">{message.ctaText}</span> → {message.ctaUrl || "(no URL set)"}
            </p>
          ) : null}
        </div>
      ) : null}

      {step === 4 ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-border bg-card px-4 py-4 text-sm text-foreground">
            You are about to send <span className="font-semibold">{name || "this campaign"}</span> to{" "}
            <span className="font-semibold">{finalPreview ? finalPreview.count.toLocaleString() : "an estimated number of"}</span>{" "}
            students.
          </div>
          <div className="space-y-1">
            <label className="text-xs font-medium text-muted-foreground">Schedule for later (optional)</label>
            <Input type="datetime-local" value={scheduleAt} onChange={(e) => setScheduleAt(e.target.value)} className="h-10 w-fit" />
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" disabled={submitting} onClick={handleSaveDraft}>
              Save as Draft
            </Button>
            {scheduleAt ? (
              <Button disabled={submitting} onClick={handleSchedule}>
                {submitting ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />} Schedule
              </Button>
            ) : (
              <Button disabled={submitting} onClick={handleSendNow}>
                {submitting ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />} Send Now
              </Button>
            )}
          </div>
        </div>
      ) : null}

      <div className="flex items-center justify-between border-t border-border pt-4">
        <Button variant="outline" onClick={() => setStep((s) => Math.max(0, s - 1))} disabled={step === 0}>
          <ArrowLeft className="size-4" /> Back
        </Button>
        {step < STEPS.length - 1 ? (
          <Button
            onClick={() => setStep((s) => Math.min(STEPS.length - 1, s + 1))}
            disabled={(step === 0 && !canGoNextFromStep0) || (step === 2 && !canGoNextFromStep2)}
          >
            Next <ArrowRight className="size-4" />
          </Button>
        ) : null}
      </div>
    </div>
  );
}

function ChannelToggle({
  icon: Icon,
  label,
  description,
  checked,
  onCheckedChange,
}: {
  icon: typeof Mail;
  label: string;
  description: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between rounded-xl border border-border bg-card px-4 py-3">
      <div className="flex items-center gap-3">
        <Icon className="size-5 shrink-0 text-muted-foreground" />
        <div>
          <p className="text-sm font-medium text-foreground">{label}</p>
          <p className="text-xs text-muted-foreground">{description}</p>
        </div>
      </div>
      <Switch checked={checked} onCheckedChange={onCheckedChange} />
    </div>
  );
}

function PreviewCard({ title, primary, body }: { title: string; primary: string; body: string }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4">
      <p className="mb-2 text-xs font-medium tracking-wide text-muted-foreground uppercase">{title}</p>
      <p className="text-sm font-semibold text-foreground">{primary || "(no subject/title)"}</p>
      <p className="mt-1 text-sm whitespace-pre-wrap text-muted-foreground">{body || "(no body)"}</p>
    </div>
  );
}
