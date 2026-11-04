# OneHostel backend

Spring Boot 3.5 · Java 21 · MongoDB · Keycloak · Gradle Kotlin DSL composite build.
Microservices behind one gateway, serving the OneHostel Angular app. House rules: [CLAUDE.md](CLAUDE.md).

## Builds

| Build | Kind | Port | What it is |
|---|---|---|---|
| `onehostel-gateway` | service | 9001 | Edge (Spring Cloud Gateway MVC). Verifies Keycloak tokens, strips and re-sets identity headers, CORS, rate limits, routing |
| `onehostel-access-core` | service | 9002 | Authentication (password, OTP, refresh, logout, change/reset password) and users. Later: roles, privileges, workflow |
| `onehostel-resident` | service | 9004 | Residents, admission, check-in/out, guardians (skeleton) |
| `onehostel-property` | service | 9005 | Hostel → block → floor → room → bed (skeleton) |
| `onehostel-billing` | service | 9006 | Fee plans, invoices, payments, refunds (skeleton) |
| `onehostel-common` | library | – | Error catalogue, envelopes, base document, generic CRUD, audit trail, correlation id, `CurrentUser`, async executors |
| `onehostel-tenancy` | library | – | `TenantContext` and the tenant boundary: every Mongo collection call is scoped to the caller's organization (ENFORCE) |
| `onehostel-scheduler-core` | library | – | `HostelJob`: cluster-safe jobs with DB-held cron, run history, failure records, lease lock |
| `build-logic` | build | – | Convention plugins (Java 21, Boot BOM, Lombok, JUnit, JaCoCo, Sonar, publishing) |

Versions: `gradle/libs.versions.toml` only.

## Routes (gateway → service)

| Path | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/access/**` | access-core |
| `/api/v1/resident/**` | resident |
| `/api/v1/property/**` | property |
| `/api/v1/billing/**` | billing |

Public (no token): `/api/v1/auth/login`, `otp/send`, `otp/verify`, `forgot-password/reset`, `refresh-token`, `logout`. Everything else needs a Keycloak access token.
Any signed-in user: `/api/v1/auth/change-password`, `GET /api/v1/access/privileges`, `GET/PUT /api/v1/access/me` (own profile). Every other `/api` call also needs a privilege (below).
`/internal/**` is service-to-service only: the gateway has no route to it.

## Access control (privileges)

- **Catalogue:** every privilege is declared once in `onehostel-access-core/.../helper/PrivilegeCatalog.java`, with:
  - its code;
  - the app pages it opens (`uiRoutes`);
  - the API calls it allows (`apis`, method + Ant path);
  - the roles it is granted by default.
- **Bits:**
  - At startup access-core syncs the catalogue into the `privileges` collection.
  - A new code gets the next free bit, and a removed code is retired.
  - Bits are never reused or edited by hand.
- **Role grants:** stored in `role_privileges`, one document per role.
  - Catalogue defaults are applied once, so a privilege an admin removes stays removed.
  - Change them with `GET /api/v1/access/roles` and `PUT /api/v1/access/roles/{role}/privileges` (privilege `ROLE_MANAGE`).
  - SUPER_ADMIN cannot lose `ROLE_MANAGE`.
- **privToken:** login, OTP verify and refresh return `priv_token`: one character per bit, `1` where the access token's roles hold it.
- **Gateway:** `PrivilegeFilter` fetches the matrix from `/internal/v1/privileges/matrix` and caches it for `PRIVILEGE_CACHE_TTL`.
  - It allows a call only when a privilege covering the call's method and path has its bit set in one of the caller's role tokens.
  - A call that no privilege covers gets 403, so a new endpoint needs its privilege first.
- **Angular:** these all test the same bit against the catalogue from `GET /api/v1/access/privileges`:
  - `AuthService.canAccess(route)`, used by the sidebar, global search and `adminGuard`;
  - the `*ohPrivilege="'ROOMS_MANAGE'"` directive.
- **When changes apply:**
  - at the gateway, within the cache time;
  - in the user's screens, at their next token refresh (at most 15 minutes).

## Run locally (Windows PowerShell, from this folder)

**Prerequisites:** JDK 21.

**1. Secrets (once):** `Copy-Item .env.example .env`, then fill in `KC_BOOTSTRAP_ADMIN_PASSWORD`, `KEYCLOAK_SERVICE_CLIENT_SECRET` (any long random string), `SUPERADMIN_PASSWORD` (8+ chars, upper + lower case, digit, special) and `GATEWAY_INTERNAL_SECRET` (any long random string).

**2. MongoDB (27017) and Keycloak (8080).** No Docker needed; binaries go to `%USERPROFILE%\onehostel-infra`:

```powershell
powershell -ExecutionPolicy Bypass -File infra\local\start-local.ps1                 # add -ResetKeycloak after editing the realm file
```

With Docker instead: `docker compose --env-file .env -f infra/local/docker-compose.yml up -d`, then run `infra\local\keycloak\configure-token-exchange.ps1` once.

**3. Services**, one terminal each (only the ones you need; the gateway and access-core for sign-in):

```powershell
.\gradlew.bat bootRun-onehostel-gateway
.\gradlew.bat bootRun-onehostel-access-core
.\gradlew.bat bootRun-onehostel-property      # resident, billing likewise
```

The Angular dev server proxies `/api` to the gateway on 9001. Sign in as `super.admin` with `SUPERADMIN_PASSWORD`.
Under the `local` profile (the default) OTP codes are logged and returned as `devOtp`, and any 6-digit code is accepted from this machine.
Health: `http://localhost:<port>/actuator/health`. API docs (local only): `http://localhost:<port>/swagger-ui.html`.

Stop MongoDB and Keycloak with `infra\local\stop-local.ps1`.

## Build and test

```powershell
.\gradlew.bat build              # every build, with tests
.\gradlew.bat test               # tests only
.\gradlew.bat publishLibraries   # tenancy, common, scheduler-core to mavenLocal (Nexus when NEXUS_URL is set)
.\gradlew.bat bootJar            # service jars; then: docker build --build-arg SERVICE=onehostel-gateway .
```

## Environment variables

| Variable | Used by | Default (local) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | all | `local` |
| `GATEWAY_INTERNAL_SECRET` | all | none: required outside `local` |
| `KEYCLOAK_SERVICE_CLIENT_SECRET` | access-core | none: required |
| `KEYCLOAK_SERVER_URL`, `KEYCLOAK_REALM` | access-core | `http://localhost:8080`, `onehostel` |
| `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI` | gateway | the local realm |
| `ACCESS_MONGODB_URI`, `RESIDENT_MONGODB_URI`, `PROPERTY_MONGODB_URI`, `BILLING_MONGODB_URI` | each service | `mongodb://localhost:27017/onehostel_<service>` |
| `ACCESS_CORE_URI`, `RESIDENT_URI`, `PROPERTY_URI`, `BILLING_URI` | gateway | `http://localhost:900x` |
| `CORS_ALLOWED_ORIGINS` | gateway | `http://localhost:4200` |
| `AUTH_RATE_LIMIT_PER_MINUTE` | gateway | `10` per client IP on login / OTP / refresh |
| `PRIVILEGE_CACHE_TTL` | gateway | `60s`: how long the privilege matrix is reused |
| `OTP_DEV_MODE`, `OTP_ACCEPT_ANY` | access-core | `true` under `local`; refused elsewhere |

## API conventions

Requests are a `CommonRequestObject`; controllers bind the inner `requestObject` directly:

```json
{ "requestObject": { }, "clientSource": "WEB", "page": 0, "size": 20, "sortField": "createdAt", "sortOrder": "DESC",
  "issuanceSearch": { "andOrType": "AND", "filterQueryVO": [ { "filterfield": "status", "filtervalue": "ACTIVE", "operatorType": "EQUAL" } ] } }
```

Success: `{ "success": true, "message", "responseObject", "statusCode", "correlationId", "timestamp" }`.
Error: `{ "success": false, "errorCode", "message", "statusCode", "errors": [{ "field", "message" }], "correlationId", "sourceService", "timestamp" }`.
The HTTP status always equals `statusCode`. Every response carries `X-Correlation-Id`, and every log line of that request shows it.

Generic CRUD (any feature on `AbstractCrudController`): `GET /inq/{id}`, `POST /getall`, `POST /create`, `PUT /update/{id}`, `PATCH /patch/{id}`, `DELETE /delete/{id}` (soft delete).
