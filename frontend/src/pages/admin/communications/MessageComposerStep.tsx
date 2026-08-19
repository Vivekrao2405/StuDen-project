import { useRef } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { PERSONALIZATION_TOKENS } from "@/pages/admin/communications/communicationsDisplay";

export interface MessageFields {
  emailSubject: string;
  emailBodyHtml: string;
  pushTitle: string;
  pushBody: string;
  inappTitle: string;
  inappBody: string;
  ctaText: string;
  ctaUrl: string;
}

interface MessageComposerStepProps {
  value: MessageFields;
  onChange: (value: MessageFields) => void;
  sendEmail: boolean;
  sendPush: boolean;
  sendInapp: boolean;
}

type TextFieldElement = HTMLInputElement | HTMLTextAreaElement;

export function MessageComposerStep({ value, onChange, sendEmail, sendPush, sendInapp }: MessageComposerStepProps) {
  const activeFieldRef = useRef<{ key: keyof MessageFields; el: TextFieldElement } | null>(null);

  function set(key: keyof MessageFields, next: string) {
    onChange({ ...value, [key]: next });
  }

  function trackFocus(key: keyof MessageFields) {
    return (e: React.FocusEvent<TextFieldElement>) => {
      activeFieldRef.current = { key, el: e.currentTarget };
    };
  }

  function insertToken(token: string) {
    const active = activeFieldRef.current;
    const text = `{{${token}}}`;
    if (!active) {
      return;
    }
    const { key, el } = active;
    const start = el.selectionStart ?? value[key].length;
    const end = el.selectionEnd ?? value[key].length;
    const current = value[key];
    const next = current.slice(0, start) + text + current.slice(end);
    set(key, next);
    window.setTimeout(() => {
      el.focus();
      el.setSelectionRange(start + text.length, start + text.length);
    }, 0);
  }

  const noChannelsSelected = !sendEmail && !sendPush && !sendInapp;

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border bg-muted/30 p-3">
        <p className="mb-2 text-xs font-medium text-muted-foreground">
          Click a field, then click a token to insert it at the cursor.
        </p>
        <div className="flex flex-wrap gap-1.5">
          {PERSONALIZATION_TOKENS.map((token) => (
            <button
              key={token}
              type="button"
              onClick={() => insertToken(token)}
              className="rounded-full border border-border bg-card px-2.5 py-1 font-mono text-xs text-foreground hover:bg-accent hover:text-accent-foreground"
            >
              {`{{${token}}}`}
            </button>
          ))}
        </div>
      </div>

      {noChannelsSelected ? (
        <p className="rounded-lg border border-dashed border-border px-4 py-6 text-center text-sm text-muted-foreground">
          Select at least one channel to compose its message.
        </p>
      ) : null}

      {sendEmail ? (
        <Card>
          <CardHeader>
            <CardTitle>Email</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Field label="Subject">
              <Input
                value={value.emailSubject}
                onChange={(e) => set("emailSubject", e.target.value)}
                onFocus={trackFocus("emailSubject")}
                placeholder="You have a new update"
              />
            </Field>
            <Field label="Body (HTML)">
              <Textarea
                value={value.emailBodyHtml}
                onChange={(e) => set("emailBodyHtml", e.target.value)}
                onFocus={trackFocus("emailBodyHtml")}
                placeholder="<p>Hi {{firstName}}, ...</p>"
                className="min-h-32 font-mono text-xs"
              />
            </Field>
          </CardContent>
        </Card>
      ) : null}

      {sendPush ? (
        <Card>
          <CardHeader>
            <CardTitle>Push Notification</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Field label="Title">
              <Input
                value={value.pushTitle}
                onChange={(e) => set("pushTitle", e.target.value)}
                onFocus={trackFocus("pushTitle")}
                placeholder="New update"
              />
            </Field>
            <Field label="Body">
              <Textarea
                value={value.pushBody}
                onChange={(e) => set("pushBody", e.target.value)}
                onFocus={trackFocus("pushBody")}
                placeholder="Hi {{firstName}}, ..."
                className="min-h-20"
              />
            </Field>
          </CardContent>
        </Card>
      ) : null}

      {sendInapp ? (
        <Card>
          <CardHeader>
            <CardTitle>In-App Notification</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <Field label="Title">
              <Input
                value={value.inappTitle}
                onChange={(e) => set("inappTitle", e.target.value)}
                onFocus={trackFocus("inappTitle")}
                placeholder="New update"
              />
            </Field>
            <Field label="Body">
              <Textarea
                value={value.inappBody}
                onChange={(e) => set("inappBody", e.target.value)}
                onFocus={trackFocus("inappBody")}
                placeholder="Hi {{firstName}}, ..."
                className="min-h-20"
              />
            </Field>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Call to Action (optional, shared across channels)</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label="Button text">
            <Input value={value.ctaText} onChange={(e) => set("ctaText", e.target.value)} placeholder="View details" />
          </Field>
          <Field label="URL">
            <Input value={value.ctaUrl} onChange={(e) => set("ctaUrl", e.target.value)} placeholder="https://..." />
          </Field>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <label className="text-xs font-medium text-muted-foreground">{label}</label>
      {children}
    </div>
  );
}
