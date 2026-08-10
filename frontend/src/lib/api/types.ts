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

export interface PortfolioRequest {
  headline: string;
  bio?: string;
  experienceSummary?: string;
  responseTime?: string;
  location?: string;
  available: boolean;
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
  skills: unknown[];
  education: PublicEducationItem[];
  certificates: PublicCertificateItem[];
  showcase: unknown[];
  services: unknown[];
}
