import { Award, ExternalLink } from "lucide-react";

import { formatIssueDate } from "@/lib/format";

export interface CertificateItemData {
  title: string;
  issuedBy: string | null;
  issueDate: string | null;
  certificateUrl: string | null;
}

export function CertificateListItem({ item }: { item: CertificateItemData }) {
  const formattedDate = formatIssueDate(item.issueDate);
  return (
    <div className="flex gap-3 py-3">
      <div className="mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full bg-accent">
        <Award className="size-4 text-accent-foreground" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-medium text-foreground">{item.title}</p>
        <p className="text-sm text-muted-foreground">
          {[item.issuedBy, formattedDate].filter(Boolean).join(" · ")}
        </p>
        {item.certificateUrl ? (
          <a
            href={item.certificateUrl}
            target="_blank"
            rel="noreferrer"
            className="mt-0.5 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
          >
            View certificate <ExternalLink className="size-3" />
          </a>
        ) : null}
      </div>
    </div>
  );
}
