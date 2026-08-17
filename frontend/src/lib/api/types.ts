export type UserRole = "STUDENT" | "ADMIN";

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  id: string;
  fullName: string;
  email: string;
  emailVerified: boolean;
  accessToken: string;
}

export interface UserResponse {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  profileImageUrl: string | null;
  university: string | null;
  role: UserRole;
  emailVerified: boolean;
  active: boolean;
  createdAt: string;
}

export interface UpdateUserRequest {
  fullName: string;
  phone?: string;
}

export type AdminUserStatus = "ACTIVE" | "DEACTIVATED" | "DELETED";

export interface AdminUserSummary {
  id: string;
  fullName: string;
  email: string;
  profileImageUrl: string | null;
  role: UserRole;
  status: AdminUserStatus;
  createdAt: string;
}

export interface AdminUserDetail {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  profileImageUrl: string | null;
  university: string | null;
  role: UserRole;
  emailVerified: boolean;
  status: AdminUserStatus;
  createdAt: string;
  deletedAt: string | null;
  publicSlug: string | null;
  portfolioAvailable: boolean | null;
}

export interface AdminUserListParams {
  search?: string;
  page?: number;
  size?: number;
}

export type AvailabilityOption =
  | "FREELANCE_PROJECTS"
  | "COLLABORATIONS"
  | "HACKATHONS"
  | "STUDENT_PROJECTS"
  | "INTERNSHIPS"
  | "OPEN_SOURCE"
  | "PART_TIME";

export type SkillIconType = "BRAND" | "LUCIDE";

export interface SkillResponse {
  id: string;
  name: string;
  category: string;
  iconSlug: string | null;
  iconType: SkillIconType;
}

export type SkillLevel = "BEGINNER" | "INTERMEDIATE" | "EXPERT";

export interface PortfolioSkillResponse extends SkillResponse {
  level: SkillLevel;
}

export interface PortfolioRequest {
  headline: string;
  bio?: string;
  experienceSummary?: string;
  responseTime?: string;
  location?: string;
  available: boolean;
  skillIds?: string[];
  availableFor?: AvailabilityOption[];
}

export interface PortfolioResponse {
  id: string;
  headline: string;
  bio: string | null;
  experienceSummary: string | null;
  responseTime: string | null;
  location: string | null;
  available: boolean;
  publicSlug: string;
  profileUrl: string;
  coverImageUrl: string | null;
  skills: PortfolioSkillResponse[];
  availableFor: AvailabilityOption[];
  createdAt: string;
  updatedAt: string;
}

export interface EducationRequest {
  degree: string;
  fieldOfStudy?: string;
  institution: string;
  startYear: number;
  endYear?: number | null;
  current: boolean;
}

export interface EducationResponse {
  id: string;
  degree: string;
  fieldOfStudy: string | null;
  institution: string;
  startYear: number;
  endYear: number | null;
  current: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CertificateRequest {
  title: string;
  issuedBy?: string;
  issueDate?: string | null;
  certificateUrl?: string;
}

export interface CertificateResponse {
  id: string;
  title: string;
  issuedBy: string | null;
  issueDate: string | null;
  certificateUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ShareMetadataResponse {
  slug: string;
  profileUrl: string;
  cardDownloadUrl: string | null;
}

export interface PublicEducationItem {
  degree: string;
  fieldOfStudy: string | null;
  institution: string;
  startYear: number;
  endYear: number | null;
  current: boolean;
}

export interface PublicCertificateItem {
  title: string;
  issuedBy: string | null;
  issueDate: string | null;
  certificateUrl: string | null;
}

export interface PublicProfileResponse {
  slug: string;
  profileUrl: string;
  fullName: string;
  profileImageUrl: string | null;
  coverImageUrl: string | null;
  headline: string;
  about: string | null;
  location: string | null;
  availability: boolean;
  skills: SkillResponse[];
  education: PublicEducationItem[];
  certificates: PublicCertificateItem[];
  showcase: PublicProjectSummary[];
  services: unknown[];
}

export type ProjectVisibility = "PUBLIC" | "PRIVATE";

export type ProjectMediaType = "IMAGE" | "VIDEO";

export interface ProjectLinkRequest {
  label: string;
  url: string;
}

export interface ProjectLinkResponse {
  label: string;
  url: string;
}

export interface ProjectMediaResponse {
  id: string;
  mediaType: ProjectMediaType;
  url: string;
  thumbnailUrl: string | null;
  displayOrder: number;
  cover: boolean;
}

export interface ProjectRequest {
  title: string;
  shortDescription?: string;
  description?: string;
  visibility?: ProjectVisibility;
  skillIds?: string[];
  links?: ProjectLinkRequest[];
}

export interface ProjectResponse {
  id: string;
  title: string;
  shortDescription: string | null;
  description: string | null;
  visibility: ProjectVisibility;
  skills: SkillResponse[];
  media: ProjectMediaResponse[];
  links: ProjectLinkResponse[];
  coverImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PublicProjectSummary {
  id: string;
  title: string;
  shortDescription: string | null;
  coverImageUrl: string | null;
  skills: SkillResponse[];
}

export interface PublicProjectDetail {
  id: string;
  title: string;
  shortDescription: string | null;
  description: string | null;
  skills: SkillResponse[];
  media: ProjectMediaResponse[];
  links: ProjectLinkResponse[];
  studentName: string;
  studentProfileImageUrl: string | null;
  studentSlug: string;
}

export type MarketplaceCategory =
  | "TECHNOLOGY"
  | "DESIGN_CREATIVE"
  | "BUSINESS_FINANCE"
  | "MARKETING"
  | "WRITING_CONTENT"
  | "EDUCATION_TUTORING"
  | "VIDEO_MEDIA"
  | "DATA_ANALYTICS"
  | "ENGINEERING"
  | "ARTS_PERFORMANCE"
  | "SPORTS_FITNESS"
  | "SCIENCE_RESEARCH"
  | "LANGUAGES"
  | "PRACTICAL_TECHNICAL"
  | "OTHER";

export type MarketplaceAvailability = "AVAILABLE" | "NOT_AVAILABLE";

export type MarketplaceSort = "recommended" | "newest" | "relevant";

export interface StudentResultResponse {
  type: "STUDENT";
  publicSlug: string;
  fullName: string;
  profileImageUrl: string | null;
  headline: string;
  location: string | null;
  available: boolean;
  skills: SkillResponse[];
  bio: string | null;
}

export interface ServiceResultResponse {
  type: "SERVICE";
  id: string;
  title: string;
  description: string | null;
  category: MarketplaceCategory;
  location: string | null;
  providerName: string;
  providerHeadline: string | null;
  providerSlug: string;
  providerProfileImageUrl: string | null;
  skills: SkillResponse[];
  priceAmount: number | null;
  currency: ServiceCurrency;
  deliveryDays: number | null;
  available: boolean;
  coverImageUrl: string | null;
}

export type MarketplaceResultResponse = StudentResultResponse | ServiceResultResponse;

export interface MarketplaceSearchResponse {
  content: MarketplaceResultResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type MarketplaceResultType = "STUDENT" | "SERVICE";

export interface MarketplaceSearchParams {
  q?: string;
  category?: MarketplaceCategory;
  location?: string;
  availability?: MarketplaceAvailability;
  skill?: string;
  type?: MarketplaceResultType;
  minPrice?: number;
  maxPrice?: number;
  maxDeliveryDays?: number;
  sort?: MarketplaceSort;
  page?: number;
  size?: number;
}

// --- Marketplace Services (Phase 6.3) -------------------------------------------------------

export type ServiceStatus = "DRAFT" | "ACTIVE" | "INACTIVE";

export type ServiceCurrency = "INR";

export interface ServiceLinkRequest {
  label: string;
  url: string;
}

export interface ServiceLinkResponse {
  label: string;
  url: string;
}

export interface ServiceMediaResponse {
  id: string;
  mediaType: ProjectMediaType;
  url: string;
  thumbnailUrl: string | null;
  displayOrder: number;
  cover: boolean;
}

export interface ServiceProjectSummary {
  id: string;
  title: string;
  coverImageUrl: string | null;
}

export interface ServiceRequest {
  title: string;
  description?: string;
  category: MarketplaceCategory;
  location?: string;
  skillIds?: string[];
  whatYoullReceive?: string[];
  priceAmount?: number;
  currency?: ServiceCurrency;
  deliveryDays?: number;
  available?: boolean;
  linkedProjectIds?: string[];
  links?: ServiceLinkRequest[];
}

export interface ServiceResponse {
  id: string;
  title: string;
  description: string | null;
  category: MarketplaceCategory;
  location: string | null;
  status: ServiceStatus;
  available: boolean;
  priceAmount: number | null;
  currency: ServiceCurrency;
  deliveryDays: number | null;
  skills: SkillResponse[];
  whatYoullReceive: string[];
  media: ServiceMediaResponse[];
  links: ServiceLinkResponse[];
  linkedProjects: ServiceProjectSummary[];
  coverImageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PublicServiceDetail {
  id: string;
  title: string;
  description: string | null;
  category: MarketplaceCategory;
  location: string | null;
  available: boolean;
  priceAmount: number | null;
  currency: ServiceCurrency;
  deliveryDays: number | null;
  skills: SkillResponse[];
  whatYoullReceive: string[];
  media: ServiceMediaResponse[];
  links: ServiceLinkResponse[];
  linkedProjects: ServiceProjectSummary[];
  coverImageUrl: string | null;
  providerName: string;
  providerHeadline: string | null;
  providerProfileImageUrl: string | null;
  providerSlug: string;
}

// Phase 6.5: a student requesting another student's service. Named distinctly from
// ServiceRequest/ServiceResponse above (that pair is the create/update payload+response for a
// service *listing*, an unrelated concept) to avoid the export-name collision.
// ACCEPTED/REJECTED are terminal — the post-acceptance work lifecycle lives on its own
// OrderStatus/OrderResponse below (Phase 6.8), not on this type.
export type ServiceRequestStatus = "PENDING" | "ACCEPTED" | "REJECTED";

export interface ServiceRequestLinkPayload {
  label: string;
  url: string;
}

export interface CreateServiceRequestPayload {
  serviceId: string;
  description: string;
  requestedDeliveryDate?: string;
  proposedBudget?: number;
  links?: ServiceRequestLinkPayload[];
}

export interface ServiceRequestRecord {
  id: string;
  serviceId: string | null;
  serviceTitle: string;
  servicePriceAmount: number | null;
  serviceCurrency: ServiceCurrency | null;
  status: ServiceRequestStatus;
  description: string;
  requestedDeliveryDate: string | null;
  proposedBudget: number | null;
  links: ServiceRequestLinkPayload[];
  acceptedAt: string | null;
  rejectedAt: string | null;
  rejectionReason: string | null;
  requesterName: string;
  requesterProfileImageUrl: string | null;
  requesterSlug: string | null;
  providerName: string;
  providerHeadline: string | null;
  providerProfileImageUrl: string | null;
  providerSlug: string;
  createdAt: string;
  updatedAt: string;
}

// Phase 6.7: request-linked messaging, only ever reachable for an ACCEPTED ServiceRequest.
export interface ConversationResponse {
  id: string;
  serviceRequestId: string;
  serviceTitle: string;
  servicePriceAmount: number | null;
  serviceCurrency: ServiceCurrency | null;
  otherParticipantName: string;
  otherParticipantProfileImageUrl: string | null;
  otherParticipantSlug: string | null;
  otherParticipantHeadline: string | null;
  createdAt: string;
}

export interface ConversationSummaryResponse extends ConversationResponse {
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
}

export interface MessageResponse {
  id: string;
  content: string;
  mine: boolean;
  createdAt: string;
  readAt: string | null;
}

export interface CreateMessagePayload {
  content: string;
}

// Phase 6.8: the post-acceptance work lifecycle for an ACCEPTED ServiceRequest.
export type OrderStatus = "IN_PROGRESS" | "WORK_SUBMITTED" | "COMPLETED" | "CANCELLED";

export interface OrderResponse {
  id: string;
  serviceRequestId: string;
  serviceTitle: string;
  servicePriceAmount: number | null;
  serviceCurrency: ServiceCurrency | null;
  requirements: string;
  proposedBudget: number | null;
  requestedDeliveryDate: string | null;
  status: OrderStatus;
  requestAcceptedAt: string | null;
  createdAt: string;
  submittedAt: string | null;
  submissionDescription: string | null;
  submissionLink: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  requesterName: string;
  requesterProfileImageUrl: string | null;
  requesterSlug: string | null;
  providerName: string;
  providerHeadline: string | null;
  providerProfileImageUrl: string | null;
  providerSlug: string | null;
}

export interface SubmitWorkPayload {
  description: string;
  link?: string;
}

export interface CancelOrderPayload {
  reason?: string;
}

// --- Push / in-app notifications -------------------------------------------------------------

export type NotificationType =
  | "NEW_SERVICE_REQUEST"
  | "REQUEST_ACCEPTED"
  | "REQUEST_REJECTED"
  | "NEW_MESSAGE"
  | "WORK_SUBMITTED"
  | "ORDER_COMPLETED"
  | "ORDER_CANCELLED";

export interface NotificationResponse {
  id: string;
  type: NotificationType;
  message: string;
  resourceId: string;
  url: string;
  read: boolean;
  createdAt: string;
}

export interface UnreadCountResponse {
  count: number;
}

export interface NotificationPreferenceResponse {
  type: NotificationType;
  pushEnabled: boolean;
  inAppEnabled: boolean;
}

export interface UpdateNotificationPreferencePayload {
  pushEnabled: boolean;
  inAppEnabled: boolean;
}

export interface PushSubscriptionResponse {
  id: string;
}

export interface VapidPublicKeyResponse {
  publicKey: string;
}

export interface RegisterPushSubscriptionPayload {
  endpoint: string;
  p256dh: string;
  auth: string;
}

// --- Question Bank (Phase 7.1) -----------------------------------------------------------------

export type QuestionType = "MCQ_SINGLE" | "MCQ_MULTIPLE" | "TRUE_FALSE";

export type Difficulty = "EASY" | "MEDIUM" | "HARD";

export type QuestionStatus = "DRAFT" | "REVIEW" | "PUBLISHED" | "ARCHIVED";

export interface TopicResponse {
  id: string;
  skillId: string;
  name: string;
  displayOrder: number;
}

export interface QuestionOptionRequest {
  optionText: string;
  displayOrder: number;
  isCorrect: boolean;
}

export interface QuestionOptionResponse {
  id: string;
  optionText: string;
  displayOrder: number;
  isCorrect: boolean;
}

export interface QuestionRequest {
  skillId: string;
  topicId?: string | null;
  questionText: string;
  questionType: QuestionType;
  difficulty: Difficulty;
  explanation?: string;
  estimatedTimeSeconds?: number;
  tags?: string[];
  options: QuestionOptionRequest[];
}

export interface DuplicateWarning {
  existingQuestionId: string;
  existingQuestionText: string;
}

// Admin/content-management view — includes isCorrect on every option. Never reuse this shape for
// anything a plain student-facing page renders.
export interface QuestionResponse {
  id: string;
  skillId: string;
  skillName: string;
  topicId: string | null;
  topicName: string | null;
  questionText: string;
  questionType: QuestionType;
  difficulty: Difficulty;
  explanation: string | null;
  status: QuestionStatus;
  estimatedTimeSeconds: number | null;
  tags: string[];
  options: QuestionOptionResponse[];
  createdById: string;
  createdByName: string;
  reviewedById: string | null;
  reviewedByName: string | null;
  version: number;
  previousVersionId: string | null;
  createdAt: string;
  updatedAt: string;
  duplicateWarning: DuplicateWarning | null;
}

export interface QuestionSummaryResponse {
  id: string;
  skillId: string;
  skillName: string;
  topicId: string | null;
  topicName: string | null;
  questionTextPreview: string;
  questionType: QuestionType;
  difficulty: Difficulty;
  status: QuestionStatus;
  version: number;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface QuestionBankStats {
  total: number;
  draft: number;
  review: number;
  published: number;
  archived: number;
}

export interface QuestionListParams {
  skillId?: string;
  topicId?: string;
  difficulty?: Difficulty;
  type?: QuestionType;
  status?: QuestionStatus;
  search?: string;
  page?: number;
  size?: number;
}

// --- Assessment Engine (Phase 7.2) --------------------------------------------------------------

export type AssessmentType = "KNOWLEDGE";

export type AssessmentStatus = "IN_PROGRESS" | "SUBMITTED" | "EXPIRED";

export interface AssessableSkillResponse {
  skillId: string;
  name: string;
  category: string;
  iconSlug: string | null;
  iconType: SkillIconType;
  publishedQuestionCount: number;
  requiredQuestionCount: number;
  assessable: boolean;
}

// In-progress view — structurally cannot carry isCorrect/explanation/correctOptionIds (mirrors
// LearnerOptionResponse's no-leak guarantee from Phase 7.1). AssessmentResultOptionView is the
// only shape allowed to carry `correct`.
export interface AssessmentOptionView {
  id: string;
  optionText: string;
  displayOrder: number;
}

export interface AssessmentQuestionView {
  id: string;
  questionText: string;
  questionType: QuestionType;
  difficulty: Difficulty;
  displayOrder: number;
  points: number;
  options: AssessmentOptionView[];
  // The student's own previously-saved answer for this question, if any — lets resume/refresh
  // restore in-progress selections without ever exposing correctness.
  selectedOptionIds: string[];
}

export interface AssessmentDetailResponse {
  id: string;
  skillId: string;
  skillName: string;
  assessmentType: AssessmentType;
  status: AssessmentStatus;
  totalQuestions: number;
  startedAt: string;
  timeLimitSeconds: number | null;
  // Backend-computed remaining time as of this response — the frontend must treat this as the
  // authoritative reference point for its countdown display, never a purely client-side timer.
  remainingSeconds: number | null;
  questions: AssessmentQuestionView[];
}

export interface AssessmentResultOptionView {
  id: string;
  optionText: string;
  displayOrder: number;
  correct: boolean;
}

export interface AssessmentResultQuestionView {
  id: string;
  questionText: string;
  questionType: QuestionType;
  difficulty: Difficulty;
  displayOrder: number;
  points: number;
  options: AssessmentResultOptionView[];
  selectedOptionIds: string[];
  correctOptionIds: string[];
  correct: boolean;
  explanation: string | null;
}

// Only ever returned once an assessment is SUBMITTED/EXPIRED — correct answers and explanations
// are safe here because the assessment is permanently locked (spec §31/§32).
export interface AssessmentResultResponse {
  id: string;
  skillId: string;
  skillName: string;
  assessmentType: AssessmentType;
  status: AssessmentStatus;
  totalQuestions: number;
  correctCount: number | null;
  scorePercentage: number | null;
  startedAt: string;
  submittedAt: string | null;
  questions: AssessmentResultQuestionView[];
}

// --- Skill Scoring / Level Mapping (Phase 7.3) ---------------------------------------------------

// Deliberately NOT the same type as the portfolio's self-declared SkillLevel
// (BEGINNER/INTERMEDIATE/EXPERT) — this is the wider 5-tier scale an assessment score maps to.
// Render as "Assessment Level: X", never "Verified Skill: X" (spec §20).
export type AssessmentLevel = "BEGINNER" | "DEVELOPING" | "INTERMEDIATE" | "ADVANCED" | "EXPERT";

export type TopicPerformanceTier = "NEEDS_IMPROVEMENT" | "DEVELOPING" | "STRONG";

export interface TopicPerformanceView {
  topicId: string | null;
  topicName: string;
  correctCount: number;
  totalQuestions: number;
  percentage: number;
  tier: TopicPerformanceTier;
}

export interface PerformanceSummaryView {
  overallPercentage: number;
  level: AssessmentLevel;
  strongTopics: string[];
  needsImprovementTopics: string[];
}

// GET /assessments/{id}/result — only ever returned for a terminal (SUBMITTED/EXPIRED) assessment;
// requesting it while IN_PROGRESS 409s server-side.
export interface AssessmentResultSummaryResponse {
  assessmentId: string;
  skillId: string;
  skillName: string;
  status: AssessmentStatus;
  totalQuestions: number;
  correctCount: number;
  incorrectCount: number;
  scorePercentage: number;
  level: AssessmentLevel;
  topicPerformance: TopicPerformanceView[];
  summary: PerformanceSummaryView;
  startedAt: string;
  submittedAt: string | null;
}

export interface AnswerResponse {
  assessmentQuestionId: string;
  selectedOptionIds: string[];
  answeredAt: string;
}
