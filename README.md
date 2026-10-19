# OneHostel-Backend

Spring Boot 3.5.9 · Spring Cloud Gateway (Server MVC) · Java 21 · MongoDB · Spring Security + JWT · Gradle (Kotlin DSL).
REST API for the OneHostel Angular app.

## Architecture

```
Angular (:4200) ──▶ oh-auth-gtwy (:8080, the only public entry) ──▶ oh-access-core (:8082, loopback only)
                     gateway + authentication                        business: organizations, hostels, ...
                     database onehostel_auth                         database onehostel_core
```

| Module | What it is |
|---|---|
| `oh-common` | Shared library: request/response envelopes, `ErrorCode`, constants and enums, security helpers, search mirror |
| `oh-auth-gtwy` | Login, OTP, tokens and users (`/api/v1/auth/**`), plus the gateway that checks and routes every other request |
| `oh-access-core` | Business services. Accepts only requests signed by the gateway |

What the gateway does to every request, in order (the browser only ever talks to it, so the frontend needs no change):

1. **Correlation id:** reuses `X-Correlation-Id` or creates one, and passes it downstream.
2. **CORS**, and the per-IP **rate limit** on the public auth endpoints.
3. **Public endpoint?** If the path is on the permit-all list, it goes through without a token. The list is DB-driven (collection `C0011`): built-in entries are seeded at startup, and `POST /api/v1/gateway/public-endpoints/refresh` (super admin) reloads it without a restart.
4. **Token check:** otherwise the `Bearer` token must be valid and unexpired → else `401`.
5. **Account check:** the account must still be active and unlocked, read through a 60-second Caffeine cache → else `401`.
6. **Privilege check:** the endpoint must be in `EndpointPrivilege` (first match wins), and the caller's role must hold the permission it maps to → else `403`. Endpoints missing from the map are refused.
7. **Route:** `/api/v1/auth/**` and `/api/v1/gateway/**` are handled by the gateway itself. Everything else under `/api/v1` is forwarded to `oh-access-core`, with the caller as `X-User-Id`, `X-User-Email`, `X-User-Role`, `Organization-Id`, `X-Hostel-Id` and `X-Bed-Id`, plus an HMAC signature (`X-Gateway-Signature`, `X-Gateway-Timestamp`).

Before forwarding, the gateway strips any identity headers the client sent, plus `Authorization` and cookies, so the refresh token never leaves it. `oh-access-core` accepts a request only with a valid signature no older than 60 s, so a request that didn't come through the gateway gets `401`. If `oh-access-core` is down, the gateway answers `503 UPSTREAM_UNAVAILABLE` in the standard envelope.

## Prerequisites

- JDK 21
- A MongoDB cluster, from `MONGODB_URI` (or `mongodb://localhost:27017`). Each service uses its own database on it.

## Run

Start both services, each in its own terminal or IDE run configuration, with the same `MONGODB_URI`:

```bash
./gradlew :oh-access-core:bootRun   # Windows PowerShell: .\gradlew.bat :oh-access-core:bootRun
./gradlew :oh-auth-gtwy:bootRun
```

The `dev` profile is active by default. Point the Angular app and Postman at http://localhost:8080 only.

| URL | Purpose |
|---|---|
| http://localhost:8080/actuator/health | Gateway `{"status":"UP"}` once MongoDB is reachable |
| http://localhost:8080/swagger-ui.html | Gateway and auth API docs (dev only) |
| http://127.0.0.1:8082/swagger-ui.html | oh-access-core API docs (dev only, from this machine) |

## Test

```bash
./gradlew test            # all modules; e.g. ./gradlew :oh-auth-gtwy:test for one
```

## Environment variables

Secrets are never committed. Set them in your shell or IDE run configuration.

| Variable | Required | Dev default | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | no | `dev` | `dev` or `prod` (both services) |
| `MONGODB_URI` | prod | `mongodb://localhost:27017` | MongoDB cluster (both services). The database name comes from each service, not the URI |
| `AUTH_GTWY_DB` / `ACCESS_CORE_DB` | no | `onehostel_auth` / `onehostel_core` | Each service's database |
| `GATEWAY_SIGNING_SECRET` | prod | public dev-only value | Signs the caller identity the gateway forwards. **Same value in both services** |
| `ACCESS_CORE_URI` | no | `http://127.0.0.1:8082` | Where the gateway forwards business requests |
| `ACCESS_CORE_PORT` / `ACCESS_CORE_ADDRESS` | no | `8082` / `127.0.0.1` | oh-access-core listen port and address (loopback keeps it private) |
| `SEED_DEMO_ORGANIZATION` | no | `true` (dev only) | oh-access-core creates the demo organization `org-sunrise-hostels` |
| `CORS_ALLOWED_ORIGINS` | prod | `http://localhost:4200` | Comma-separated browser origins allowed to call the gateway |
| `SERVER_PORT` | no | `8080` | Gateway HTTP port |
| `JWT_SECRET` | prod | public dev-only key | Base64 of ≥ 32 random bytes (`openssl rand -base64 48`) |
| `JWT_EXPIRATION` | no | `15m` | Access token lifetime |
| `JWT_REFRESH_EXPIRATION` | no | `7d` | Refresh token (cookie) lifetime |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | no | — | First super admin account, created at startup if missing |
| `ADMIN_PASSCODE` | no | — | Optional 6-digit passcode for that super admin |
| `AUTH_RATE_LIMIT_PER_MINUTE` | no | `10` | Requests per minute per client IP on the public auth endpoints |
| `SEED_DEMO_USERS` | no | `true` (dev only) | Create the demo accounts below |

## Login and roles

All auth endpoints are under `/api/v1/auth` and take a `CommonRequestObject`:

| Endpoint | `requestObject` |
|---|---|
| `POST /login` | `identifier`, `secret`, `authMode`, `userType` (see below) |
| `POST /otp/send` | `identifier`, `channel` (`SMS` / `EMAIL`), `userType` (`HOSTEL_OWNER` / `HOSTEL_TENANT`) |
| `POST /refresh-token` | none; uses the refresh cookie |
| `POST /logout` | none; uses the refresh cookie |
| `GET /me` | none; needs the access token |

`/login` modes:

| `userType` | `identifier` | `authMode` → `secret` |
|---|---|---|
| `SUPER_ADMIN` | email or username | `PASSWORD` → password, `PASSCODE` → 6-digit passcode |
| `HOSTEL_OWNER` | email or 10-digit mobile | `PASSWORD` → password, `OTP` → code from `/otp/send` |
| `HOSTEL_TENANT` | email or 10-digit mobile | `PASSWORD` → password, `OTP` → code from `/otp/send` |

```json
{ "requestObject": { "identifier": "9848012345", "secret": "Owner@OneHostel2026", "authMode": "PASSWORD", "userType": "HOSTEL_OWNER" } }
```

Every login and refresh returns the same `responseObject`: `tokenType` (`Bearer`), `accessToken`, `expiresIn` (seconds), and `userProfile` (`userId`, `fullName`, `email`, `mobileNumber`, `role`, `status`, `organizationId`, `hostelId`, `roomNumber`, `bedId`, `permissions`).
Send the access token as `Authorization: Bearer <accessToken>`.

Account state is checked only after the credential is proven, so a wrong password never reveals it. Logging in requires `status` `ACTIVE` and not `locked`:

- Locked accounts get `423 ACCOUNT_LOCKED`.
- `PENDING_VERIFICATION` accounts get `403 ACCOUNT_NOT_VERIFIED`.
- `INACTIVE` and `BLOCKED` accounts get `403 ACCOUNT_DISABLED`.

Sessions:

- **Access token:** a JWT that lasts 15 minutes.
- **Refresh token:** set only as the `oh_refresh` cookie (HttpOnly, Secure, SameSite=Strict, path `/api/v1/auth`), so JavaScript never sees it. Angular must send `withCredentials: true` on `/refresh-token` and `/logout`.
- **Rotation:** each refresh replaces the cookie. Presenting an already-used refresh token ends the whole session.

Limits:

- **Rate limit:** the public auth endpoints allow 10 requests per minute per client IP, then answer `429` with a `Retry-After` header. The count is kept in memory, so it is per server instance. Behind a reverse proxy, set `server.forward-headers-strategy=native` so the client's IP is used.
- **OTP:** a code is valid for 5 minutes and allows 5 tries. Each identifier gets at most 3 codes per 15 minutes. `/otp/send` gives the same answer whether or not the account exists.
- **OTP delivery:** in `dev`, codes are written to the server log. Production must provide an `OtpSender` bean (SMS / email), or the app will not start.

| Role | Path prefix it alone can call |
|---|---|
| `SUPER_ADMIN` | `/api/v1/admin/**` |
| `HOSTEL_OWNER` | `/api/v1/owner/**` |
| `HOSTEL_TENANT` | `/api/v1/tenant/**` |

Every other `/api/v1/**` path accepts any signed-in role. Use `@PreAuthorize` on controller methods for finer rules.

In the `dev` profile, these accounts are created at startup if missing. The values are dev-only and publicly known:

| Name | Role | Email | Password | Also logs in with |
|---|---|---|---|---|
| OneHostel Super Admin | `SUPER_ADMIN` | `admin@onehostel.com` | `Admin@OneHostel2026` | username `superadmin`, passcode `246810` |
| Rajesh Kumar | `HOSTEL_OWNER` | `rajesh.kumar@onehostel.com` | `Owner@OneHostel2026` | mobile `9848012345` (password or OTP) |
| Priya Sharma | `HOSTEL_TENANT` | `priya.sharma@onehostel.com` | `Tenant@OneHostel2026` | mobile `9701234567` (password or OTP) |

The super admin comes from `app.bootstrap.admin-*` in `application-dev.yml`; override it with `ADMIN_NAME`, `ADMIN_EMAIL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` and `ADMIN_PASSCODE`.
The owner and tenant come from UserSeeder (`SEED_DEMO_USERS=false` skips them). They belong to organization `org-sunrise-hostels`, and the tenant is in room `101`, bed `ROOM-101-BED-A`.
At startup, UserMigration brings stored users up to date: old role names, the old `active` flag → `status`, and `hostelBranchId` / `unitId` → `hostelId` / `bedId`. It then rebuilds their search-mirror rows.

### Search mirror

`HostelCommonSearch` (collection `C0009`) holds one indexed row per user for fast list and lookup screens, kept in sync on every save and delete of a user:

| Slot | Holds |
|---|---|
| `text1` | user id |
| `text2` | role |
| `text3` | status |
| `text4` | locked |
| `text5` | email |
| `text6` | username |
| `text8` | country code |
| `text9` | mobile |
| `text10` | full name |
| `text11` | hostel id |
| `text12` | bed id |
| `text13` | room number |
| `list1` | permissions |

Password and passcode hashes are never mirrored.

## Feature modules and tenancy

Every entity exposes the same five actions, all `POST` with a `CommonRequestObject` body:

| Action | `requestObject` | Returns |
|---|---|---|
| `{base}/create` | the entity's DTO | 201, the created record |
| `{base}/update` | the DTO with `id` (and `version` to detect concurrent edits) | the updated record |
| `{base}/inq` | `{ "id": "..." }` | one record |
| `{base}/getall` | `{ "keyword": "...", "hostelBranchId": "..." }` or `{}`; paging in the envelope | a page: `content`, `page`, `size`, `totalElements`, `totalPages` |
| `{base}/delete` | `{ "id": "..." }` | no `responseObject` |

The first module is hostels, at `/api/v1/hostels`.

Tenancy comes only from the login token's `org` claim, never from headers or the body:

- `SUPER_ADMIN` sees every organization, and must send `organizationId` when creating.
- `HOSTEL_OWNER` reads and writes only their own organization.
- `HOSTEL_TENANT` only reads their own organization.

A record in another organization answers `404 ENTITY_NOT_FOUND`, the same as a missing one.

To add a module, extend `HostelAbstractDocument` (the document), `HostelAbstractService` (the mapping methods) and `HostelCoreController` (a constructor and a `@RequestMapping`).

## Package layout

```
oh-common        com.onehostel.common
├── constants    CollectionNames (one registry of C00xx codes), routes, defaults, enums (Role, Permission, ...)
├── dto          CommonRequestObject / CommonResponseObject envelopes, PageResponse, IdRequest, ListFilter
├── exception    ErrorCode, OneHostelException, GlobalExceptionHandler
├── security     AuthenticatedUser, GatewayIdentity (signed identity headers), 401/403 writers
├── search       HostelCommonSearch mirror (each service mirrors its own records)
├── web          CorrelationIdFilter
└── config       Mongo auditing, OpenAPI

oh-auth-gtwy     com.onehostel.AuthGatewayApplication, com.onehostel.authgtwy
├── gateway      GatewayRoutesConfig, IdentityForwarder, EndpointPrivilege, PublicEndpoint* (DB-driven
│                permit-all list), GatewayUserCacheService, GatewayExceptionHandler
├── security     SecurityConfig, GatewayAuthenticationFilter, JwtService, rate limiter, refresh cookie
├── controller   AuthController (/api/v1/auth/**)
├── service      AuthService, OTP, refresh tokens, user search mirror
├── model        User (C0001), OtpChallenge (C0007), RefreshToken (C0008)
└── config       AppProperties, CORS, UserSeeder, UserMigration

oh-access-core   com.onehostel.AccessCoreApplication, com.onehostel.accesscore
├── security     SecurityConfig, GatewayIdentityFilter (accepts only gateway-signed requests)
├── controller   OrganizationController, HostelController; helper/ HostelCoreController (shared base)
├── service      OrganizationService, HostelService; helper/ HostelAbstractInterface, HostelAbstractService
├── model        Organization (C0010), Hostel (C0002); helper/ HostelAbstractDocument (shared base)
└── config       CoreProperties, DemoOrganizationSeeder
```

A new business endpoint needs an `EndpointPrivilege` entry in the gateway, or the gateway refuses it with `403`.

## API conventions

Request body (`POST`/`PUT`/`PATCH`) is a `CommonRequestObject`. Only `requestObject` is required:

```json
{
  "requestObject": { },
  "requestId": "client-generated-id",
  "clientSource": "WEB",
  "appVersion": "1.4.0",
  "locale": "en-IN",
  "requestedAt": "2026-10-13T10:00:00Z",
  "page": 0, "size": 10, "sortField": "createdDateTime", "sortOrder": "DESC", "paginationRequired": true
}
```

Every response, success or error, is a `CommonResponseObject`. The HTTP status always equals `statusCode`.

```json
{
  "success": true,
  "message": "Login successful.",
  "responseObject": { },
  "statusCode": 200,
  "correlationId": "4edce2f011704bb4909db0aba6266758",
  "timestamp": "2026-10-13T10:00:00Z"
}
```

```json
{
  "success": false,
  "message": "Request validation failed.",
  "statusCode": 400,
  "errorCode": "VALIDATION_FAILED",
  "errors": [{ "field": "requestObject.email", "message": "must be a well-formed email address" }],
  "correlationId": "4edce2f011704bb4909db0aba6266758",
  "timestamp": "2026-10-13T10:00:00Z"
}
```

Every response carries an `X-Correlation-Id` header. Every log line for that request shows the same id.
