# StuDen — Student Opportunity Marketplace (V1.0)

StuDen is a student marketplace where a single user account can both offer and request
services: build a profile, publish services, discover other students, and send booking
requests. See `docs/StuDen_SRS_V1.0.pdf`, `docs/API_Contract_V1.0.md`, and the ER diagram
in `docs/` for the full spec — those documents are the source of truth for all backend work.

## Tech stack

| Layer    | Stack |
|----------|-------|
| Backend  | Java 21, Spring Boot 4.0.7, Spring Security, Spring Data JPA / Hibernate, PostgreSQL, Flyway, JWT (JJWT) |
| Frontend | React + TypeScript + Vite + Tailwind CSS + shadcn/ui |
| Database | PostgreSQL 18 |

## Repository structure

```
backend/    Spring Boot API (feature-based packages: auth, user, security, config, common, ...)
frontend/   React/Vite app (scaffold only — no feature work yet)
docs/       SRS, API contract, ER diagram — frozen source of truth for V1.0
```

## Status

| Phase | Scope | Status |
|-------|-------|--------|
| Phase 1 | Project scaffolding, PostgreSQL connectivity, env-based config | ✅ Complete & verified |
| Phase 2 | User entity, registration, login, JWT auth, current-user profile | ✅ Complete & verified |
| Phase 3+ | Student Portfolio, Skills, Education, Certificates, Showcase, Services, Bookings, Messaging, Notifications, Reports, Admin | ⬜ Not started |
| Frontend | Any feature work | ⬜ Not started (Vite/Tailwind/shadcn scaffold only) |
| Payments / Reviews / Ratings / Wallet / Escrow / AI | Explicitly out of scope for V1.0 | ➖ Excluded by design |

### Verified working end-to-end

- `mvnw test` — **12/12 tests passing** (registration, login, JWT-protected `/users/me`, validation, error handling, cross-user isolation)
- `mvnw clean package` — **builds successfully**
- Spring Boot starts and connects to PostgreSQL (`studen_db` via `studen_user`, not the superuser)
- Flyway migration applies cleanly (`V1__create_users_table.sql`)
- `/actuator/health` reports `UP` (including the `db` component)
- Manually verified via curl: register (`201`), login (`200`), `/users/me` with a valid JWT (`200`), without a JWT (`401`), with an invalid JWT (`401`), profile update (`200`), wrong password (`401`), duplicate email (`409`), invalid request body (`400`), CORS preflight scoped to the frontend origin only

Nothing is currently known to be broken. The backend has not been left running between sessions — start it locally with the steps below.

## What's implemented (Phase 1 + 2)

**User model** — a single `User` entity per the ER diagram (`id`, `fullName`, `email`, `phone`,
`passwordHash`, `role` [`STUDENT`/`ADMIN`], `profileImageUrl`, `university`, `isVerified`,
`isActive`, timestamps). No Buyer/Seller split, no role-switching accounts.

**Endpoints**

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/auth/register` | Public | `201`, returns user info + JWT |
| POST | `/api/v1/auth/login` | Public | `200`, returns user info + JWT |
| GET  | `/api/v1/users/me` | JWT required | Returns the caller's own profile |
| PUT  | `/api/v1/users/me` | JWT required | Updates `fullName` / `phone` / `profileImageUrl` only; target user always comes from the JWT, never from the request |

**Security** — BCrypt password hashing, stateless JWT sessions, a `JwtAuthenticationFilter`
that protects everything except `/api/v1/auth/**` and `/actuator/health`, `/actuator/info`,
consistent JSON error responses (`401` for missing/invalid/expired JWTs, `403` for
unauthorized access, `400`/`409`/`404`/`500` via a global exception handler), and CORS locked
to a single configurable frontend origin (no wildcard).

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
- Each phase is implemented strictly to scope — see `docs/API_Contract_V1.0.md` (frozen for
  V1.0) before adding anything not already listed there.
