import Editor from "@monaco-editor/react";
import { useEffect, useMemo, useState } from "react";

import { useDebouncedCallback } from "@/lib/hooks/useDebouncedCallback";
import { cn } from "@/lib/utils";
import type { WorkspaceProps } from "@/pages/practical/workspaces/types";

interface WebSource {
  html: string;
  css: string;
  js: string;
}

const DEFAULT_SOURCE: WebSource = { html: "<div>\n  Hello!\n</div>", css: "div {\n  font-family: sans-serif;\n}", js: "" };

function parseSource(raw: string | null | undefined): WebSource {
  if (!raw) return DEFAULT_SOURCE;
  try {
    const parsed = JSON.parse(raw) as Partial<WebSource>;
    return { html: parsed.html ?? "", css: parsed.css ?? "", js: parsed.js ?? "" };
  } catch {
    return DEFAULT_SOURCE;
  }
}

function buildPreviewDocument(source: WebSource): string {
  return `<!doctype html><html><head><style>${source.css}</style></head><body>${source.html}<script>${source.js}</script></body></html>`;
}

const TABS: { key: keyof WebSource; label: string; language: string }[] = [
  { key: "html", label: "HTML", language: "html" },
  { key: "css", label: "CSS", language: "css" },
  { key: "js", label: "JavaScript", language: "javascript" },
];

/**
 * Live HTML/CSS/JS preview — the one workspace in this phase with genuine, safe live execution.
 * The iframe's `sandbox` attribute deliberately omits `allow-same-origin`, so the preview
 * document's origin is forced to `null`: it structurally cannot read parent cookies/localStorage,
 * call StuDen APIs, or reach the parent window/DOM, regardless of what the student's JS does
 * (spec §15/§38).
 */
export function WebEditorWorkspace({ assessment, attempt, mode, onSave, saving }: WorkspaceProps) {
  const isPreview = mode === "preview";
  const [source, setSource] = useState<WebSource>(() => parseSource(attempt?.submissionContent));
  const [activeTab, setActiveTab] = useState<keyof WebSource>("html");
  const [previewDoc, setPreviewDoc] = useState(() => buildPreviewDocument(source));

  const debouncedUpdate = useDebouncedCallback((next: WebSource) => {
    setPreviewDoc(buildPreviewDocument(next));
    if (!isPreview) {
      onSave?.({ submissionContent: JSON.stringify(next) });
    }
  }, 500);

  useEffect(() => {
    debouncedUpdate(source);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [source]);

  const activeTabConfig = useMemo(() => TABS.find((t) => t.key === activeTab)!, [activeTab]);

  function updateActive(value: string | undefined) {
    setSource((prev) => ({ ...prev, [activeTab]: value ?? "" }));
  }

  return (
    <div className="space-y-4">
      <div className="rounded-xl border border-border bg-card p-4">
        <h2 className="font-semibold text-foreground">Task</h2>
        <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">{assessment.instructions}</p>
        {assessment.requirements ? (
          <p className="mt-2 whitespace-pre-wrap text-sm text-muted-foreground">
            <span className="font-medium text-foreground">Requirements: </span>
            {assessment.requirements}
          </p>
        ) : null}
      </div>

      <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
        <div className="overflow-hidden rounded-xl border border-border bg-card">
          <div className="flex border-b border-border">
            {TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                onClick={() => setActiveTab(tab.key)}
                className={cn(
                  "flex-1 px-3 py-2 text-xs font-medium transition-colors",
                  activeTab === tab.key ? "bg-muted text-foreground" : "text-muted-foreground hover:text-foreground"
                )}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <Editor
            height="380px"
            language={activeTabConfig.language}
            value={source[activeTab]}
            onChange={updateActive}
            options={{ readOnly: isPreview, minimap: { enabled: false }, fontSize: 13 }}
            theme="vs-dark"
          />
          {saving ? <p className="px-3 py-1 text-xs text-muted-foreground">Saving...</p> : null}
        </div>

        <div className="overflow-hidden rounded-xl border border-border bg-card">
          <div className="border-b border-border px-3 py-2 text-xs font-medium text-muted-foreground">Live Preview</div>
          <iframe
            title="Live preview"
            sandbox="allow-scripts"
            srcDoc={previewDoc}
            className="h-[416px] w-full bg-white"
          />
        </div>
      </div>
    </div>
  );
}
