# StuDen — Student Opportunity Marketplace (V1.0)

StuDen is a student marketplace where a single user account can both offer and request
services: build a profile, publish services, discover other students, and send booking
requests. It also automatically generates a public, shareable student profile. See
`docs/StuDen_V1.0_Updated_SRS.pdf`, `docs/StuDen_API_Contract_V1.1_Updated.pdf`, and the
ER diagram in `docs/` for the full spec (V1.1) — those documents are the source of truth
for all backend work.

## Tech stack

| Layer    | Stack |
|----------|-------|
| Backend  | Java 21, Spring Boot 4.0.7, Spring Security, Spring Data JPA / Hibernate, PostgreSQL, Flyway, JWT (JJWT) |
| Frontend | React + TypeScript + Vite + Tailwind CSS + shadcn/ui |
| Database | PostgreSQL 18 |

## Repository structure

```
backend/    Spring Boot API (feature-based packages: auth, user, portfolio, education,
            certificate, share, security, config, common, ...)
frontend/   React/Vite app (scaffold only — no feature work yet)
docs/       SRS, API contract, ER diagram — source of truth for V1.0/V1.1
```

## Status

| Phase | Scope | Status |
|-------|-------|--------|
| Phase 1 | Project scaffolding, PostgreSQL connectivity, env-based config | ✅ Complete & verified |
| Phase 2 | User entity, registration, login, JWT auth, current-user profile | ✅ Complete & verified |
| Phase 3 | Student Portfolio, Education, Certificates, public shareable profile (ProfileShare/ProfileCard) | ✅ Complete & verified |
| Phase 4+ | Frontend foundation, Skills, Showcase, Services, Bookings, Messaging, Notifications, Reports, Admin | ⬜ Not started |
| Frontend | Any feature work | ⬜ Not started (Vite/Tailwind/shadcn scaffold only) |
| Payments / Reviews / Ratings / Wallet / Escrow / AI | Explicitly out of scope for V1.0 | ➖ Excluded by design |

### Verified working end-to-end

- `mvnw test` — **51/51 tests passing** (auth, JWT-protected `/users/me`, portfolio/education/certificate CRUD, public profile, share metadata, ownership isolation, validation, error handling)
- `mvnw clean package` — **builds successfully**
- Spring Boot starts and connects to PostgreSQL (`studen_db` via `studen_user`, not the superuser)
- Flyway migrations apply cleanly (`V1__create_users_table.sql`, `V2__create_portfolio_tables.sql`) — schema at version 2
- `/actuator/health` reports `UP` (including the `db` component)
- Manually verified via curl against a live server: register → create portfolio (auto-generates a unique `public_slug` + `ProfileShare`) → add education → fetch the public profile (no JWT) → fetch own share metadata (JWT)

Nothing is currently known to be broken. The backend has not been left running between sessions — start it locally with the steps below.

## What's implemented (Phase 1–3)

**User model** — a single `User` entity per the ER diagram (`id`, `fullName`, `email`, `phone`,
`passwordHash`, `role` [`STUDENT`/`ADMIN`], `profileImageUrl`, `university`, `isVerified`,
`isActive`, timestamps). No Buyer/Seller split, no role-switching accounts.

**Auth & user endpoints**

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/auth/register` | Public | `201`, returns user info + JWT |
| POST | `/api/v1/auth/login` | Public | `200`, returns user info + JWT |
| GET  | `/api/v1/users/me` | JWT required | Returns the caller's own profile |
| PUT  | `/api/v1/users/me` | JWT required | Updates `fullName` / `phone` / `profileImageUrl` only; target user always comes from the JWT, never from the request |

**Portfolio, education, certificates & shareable profile (Phase 3)**

A user becomes a provider by creating one `StudentPortfolio` (1:1 with `User`), which owns
many `Education` and `Certificate` records and auto-generates a unique, URL-safe
`public_slug` plus a `ProfileShare` row on creation. `ProfileCard` models the (not-yet
generated) downloadable profile card. Ownership for every mutation is always resolved from
the JWT principal — never from a path or body `userId`.

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/portfolio` | JWT required | Creates the caller's portfolio; auto-generates `public_slug` + `ProfileShare` |
| GET / PUT / DELETE | `/api/v1/portfolio/me` | JWT required | Read/update/delete the caller's own portfolio (cascades to education, certificates, share) |
| GET / POST | `/api/v1/users/me/education` | JWT required | List / add education entries |
| PUT / DELETE | `/api/v1/users/me/education/{id}` | JWT required | Update/delete own entry only (`404` if it belongs to another user) |
| GET / POST | `/api/v1/users/me/certificates` | JWT required | List / add certificates |
| PUT / DELETE | `/api/v1/users/me/certificates/{id}` | JWT required | Update/delete own entry only |
| GET | `/api/v1/public/profiles/{slug}` | **Public, no JWT** | Returns a dedicated `PublicProfileResponse` DTO — never the JPA entity, never security data |
| GET | `/api/v1/portfolio/me/share` | JWT required | Own public profile URL + card download URL (if a card has been generated) |

**Security** — BCrypt password hashing, stateless JWT sessions, a `JwtAuthenticationFilter`
that protects everything except `/api/v1/auth/**`, `/api/v1/public/**`, and
`/actuator/health`, `/actuator/info`, consistent JSON error responses (`401` for
missing/invalid/expired JWTs, `403` for unauthorized access, `400`/`409`/`404`/`500` via a
global exception handler), and CORS locked to a single configurable frontend origin (no
wildcard).

**Database** — schema is Flyway-managed (`backend/src/main/resources/db/migration`), Hibernate
runs in `validate` mode (it never auto-generates schema).

## Getting started (local development)

### Prerequisites

- PostgreSQL 18 running locally, with a `studen_db` database owned by a `studen_user` role
  (see the project's DB setup notes — the backend never connects as the `postgres` superuser)
- No global Maven required — use the wrapper (`backend/mvnw` / `mvnw.cmd`)

### Configure environment

```
cd backend
cp .env.example .env
```

Fill in `.env` with real values — **never commit it** (already gitignored):

- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` — `studen_user` credentials
- `JWT_SECRET` — a long random value (e.g. 64 random bytes, base64-encoded); never reuse the placeholder
- `JWT_EXPIRATION_MS` — token lifetime in milliseconds (default `86400000` = 24h)
- `CORS_ALLOWED_ORIGIN` — the frontend dev origin (default `http://localhost:5173`)

### Run the backend

```
cd backend
# load .env into your shell environment first, then:
./mvnw.cmd spring-boot:run
```

Flyway migrates the schema automatically on startup. Verify with:

```
curl http://localhost:8080/actuator/health
```

### Run the tests

```
cd backend
./mvnw.cmd test
```

Tests run against the real `studen_db` (no separate test database/Testcontainers yet) and are
wrapped in `@Transactional` so each test rolls back — no test data is left behind.

## Notes for future phases

- Spring Boot 4 removed `AntPathRequestMatcher` (use `PathPatternRequestMatcher`) and switched
  its own auto-configured Jackson bean to `tools.jackson.*` (Jackson 3) instead of classic
  `com.fasterxml.jackson.*` — keep this in mind if adding more JSON-handling beans.
- Flyway's Spring Boot autoconfiguration now lives in the separate `spring-boot-flyway`
  artifact (not bundled into `spring-boot-autoconfigure` anymore).
- Each phase is implemented strictly to scope — see `docs/StuDen_API_Contract_V1.1_Updated.pdf`
  before adding anything not already listed there.
- `public_slug` lives only on `StudentPortfolio` (not `User`) — the V1.1 ER diagram drew it on
  both, which was a duplicate-source-of-truth conflict resolved before Phase 3 was built.
- Phase 4 (frontend foundation + core UI) is next per the SRS's development sequence.
