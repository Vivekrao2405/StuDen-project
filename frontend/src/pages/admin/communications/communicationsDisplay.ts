import type { AudienceFilterField, CampaignStatus, CommunicationCategory, RecipientStatus } from "@/lib/api/types";

export const CATEGORY_LABEL: Record<CommunicationCategory, string> = {
  CHALLENGE: "Challenge",
  CHALLENGE_WINNER: "Challenge Winner",
  ASSESSMENT: "Assessment",
  ASSESSMENT_REMINDER: "Assessment Reminder",
  ASSESSMENT_RESULT: "Assessment Result",
  LEARNING: "Learning",
  MY_LEARNING: "My Learning",
  OPPORTUNITY: "Opportunity",
  MARKETPLACE: "Marketplace",
  SYSTEM_ANNOUNCEMENT: "System Announcement",
  PRODUCT_UPDATE: "Product Update",
  MARKETING: "Marketing",
  CUSTOM: "Custom",
};

export const CATEGORY_OPTIONS = Object.keys(CATEGORY_LABEL) as CommunicationCategory[];

export const CAMPAIGN_STATUS_LABEL: Record<CampaignStatus, string> = {
  DRAFT: "Draft",
  SCHEDULED: "Scheduled",
  PROCESSING: "Sending",
  SENT: "Sent",
  PARTIALLY_SENT: "Partially Sent",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
};

export function campaignStatusBadgeVariant(status: CampaignStatus): "default" | "secondary" | "destructive" | "outline" {
  switch (status) {
    case "SENT":
      return "default";
    case "DRAFT":
      return "secondary";
    case "SCHEDULED":
    case "PROCESSING":
      return "outline";
    case "PARTIALLY_SENT":
    case "FAILED":
      return "destructive";
    case "CANCELLED":
      return "secondary";
  }
}

export const RECIPIENT_STATUS_LABEL: Record<RecipientStatus, string> = {
  QUEUED: "Queued",
  SENT: "Sent",
  DELIVERED: "Delivered",
  FAILED: "Failed",
  BOUNCED: "Bounced",
  COMPLAINED: "Complained",
  SKIPPED: "Skipped",
};

export const ASSESSMENT_LEVEL_OPTIONS = [
  { value: "BEGINNER", label: "Beginner" },
  { value: "DEVELOPING", label: "Developing" },
  { value: "INTERMEDIATE", label: "Intermediate" },
  { value: "ADVANCED", label: "Advanced" },
  { value: "EXPERT", label: "Expert" },
] as const;

// What param inputs the AudienceBuilderStep must render for each field — mirrors exactly what
// com.studen.communication.audience.AudienceSpecificationBuilder reads for that field, no more,
// no less. A bad/missing param is a 400 server-side, so every "required" input here has a
// matching server-side `requireX` call.
export type AudienceFieldInputKind =
  | "none"
  | "days"
  | "singleSkill"
  | "multiSkill"
  | "optionalSkill"
  | "optionalSkillAndMinScore"
  | "optionalSkillAndScoreRange"
  | "requiredSkillAndLevel"
  | "requiredSkill"
  | "dateRange"
  | "userIds";

export interface AudienceFieldMeta {
  field: AudienceFilterField;
  label: string;
  inputKind: AudienceFieldInputKind;
}

export interface AudienceFieldGroup {
  label: string;
  fields: AudienceFieldMeta[];
}

export const AUDIENCE_FIELD_GROUPS: AudienceFieldGroup[] = [
  {
    label: "Portfolio",
    fields: [
      { field: "PORTFOLIO_EXISTS", label: "Has a portfolio", inputKind: "none" },
      { field: "PORTFOLIO_NOT_EXISTS", label: "Has no portfolio", inputKind: "none" },
      { field: "PORTFOLIO_UPDATED_WITHIN_DAYS", label: "Portfolio updated within the last N days", inputKind: "days" },
      { field: "PORTFOLIO_STALE_SINCE_DAYS", label: "Portfolio not updated in the last N days", inputKind: "days" },
    ],
  },
  {
    label: "Skill",
    fields: [
      { field: "SKILL_HAS", label: "Has a specific skill", inputKind: "singleSkill" },
      { field: "SKILL_LACKS", label: "Does not have a specific skill", inputKind: "singleSkill" },
      { field: "SKILL_HAS_ANY", label: "Has any of these skills", inputKind: "multiSkill" },
      { field: "SKILL_HAS_ALL", label: "Has all of these skills", inputKind: "multiSkill" },
    ],
  },
  {
    label: "Knowledge Assessment",
    fields: [
      { field: "ASSESSMENT_COMPLETED", label: "Completed an assessment", inputKind: "optionalSkill" },
      { field: "ASSESSMENT_NOT_COMPLETED", label: "Has not completed an assessment", inputKind: "optionalSkill" },
      { field: "ASSESSMENT_NO_ATTEMPT", label: "Never attempted an assessment", inputKind: "optionalSkill" },
      { field: "ASSESSMENT_SCORE_GTE", label: "Scored at least N%", inputKind: "optionalSkillAndMinScore" },
      { field: "ASSESSMENT_SCORE_RANGE", label: "Scored within a range", inputKind: "optionalSkillAndScoreRange" },
    ],
  },
  {
    label: "Skill Verification",
    fields: [
      { field: "SKILL_VERIFICATION_LEVEL", label: "Verified at a specific level", inputKind: "requiredSkillAndLevel" },
      { field: "SKILL_VERIFICATION_UNVERIFIED", label: "Not yet verified in a skill", inputKind: "requiredSkill" },
    ],
  },
  {
    label: "Activity",
    fields: [
      { field: "ACTIVITY_LAST_ACTIVE_BEFORE_DAYS", label: "Inactive for at least N days", inputKind: "days" },
      { field: "ACTIVITY_REGISTERED_BETWEEN", label: "Registered between two dates", inputKind: "dateRange" },
    ],
  },
  {
    label: "User",
    fields: [
      { field: "USER_VERIFIED", label: "Email verified", inputKind: "none" },
      { field: "USER_UNVERIFIED", label: "Email not verified", inputKind: "none" },
      { field: "USER_ACTIVE", label: "Account active", inputKind: "none" },
      { field: "USER_INACTIVE", label: "Account inactive", inputKind: "none" },
      { field: "USER_SPECIFIC_IDS", label: "Specific user IDs", inputKind: "userIds" },
    ],
  },
];

export const AUDIENCE_FIELD_META: Record<AudienceFilterField, AudienceFieldMeta> = Object.fromEntries(
  AUDIENCE_FIELD_GROUPS.flatMap((g) => g.fields).map((f) => [f.field, f])
) as Record<AudienceFilterField, AudienceFieldMeta>;

// Strict allow-list of {{token}} personalization variables — matches
// com.studen.communication.MessageTemplateRenderer exactly.
export const PERSONALIZATION_TOKENS = [
  "firstName",
  "lastName",
  "skillName",
  "assessmentName",
  "score",
  "rank",
  "challengeName",
  "roadmapName",
] as const;
