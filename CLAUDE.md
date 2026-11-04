# OneHostel backend: rules for every change

Java 21 · Spring Boot 3.5 · Gradle Kotlin DSL composite build · MongoDB · Keycloak. Built to the same
patterns as the RWA backend; see README.md for the layout and how to run it.

## Ground rules

1. **Each service and library is its own Gradle build**, included by the root `settings.gradle.kts`. Versions live only in `gradle/libs.versions.toml`; shared build config only in `build-logic` convention plugins. CVE fixes go through Boot BOM properties (`extra["tomcat.version"] = "..."`), never forced versions.
2. **Package by layer** inside every service: `com.onehostel.<service>.<layer>`. Cross-cutting code belongs in `onehostel-common` / `onehostel-tenancy`.

   | Folder | Holds |
   |---|---|
   | `config` | `@Configuration` classes and `@ConfigurationProperties` records |
   | `constants` | `CollectionNames` and the one `<Service>Enums` file that holds **every enum** of the module (`AccessEnums`, `CommonEnums`, `PropertyEnums`, ...) |
   | `controller` | REST controllers only |
   | `dto` | API request / response records |
   | `vo` | Value objects embedded in documents or passed between layers |
   | `model` | MongoDB documents |
   | `repository` | Spring Data repositories |
   | `service` | Business logic |
   | `helper` | Stateless helpers, mappers, validators |
   | `exception` | The service's `ErrorCode` catalogue |
   | `client` | Calls to outside systems (Keycloak, SMS, payment gateway) |
   | `filter` | Servlet filters (gateway, common) |

   Exception: `onehostel-tenancy/mongo` stays one package, because its collection proxy is package-private so callers cannot bypass the tenant filter.

   **Enums:** every enum of a module is nested in its `constants/<Service>Enums` holder; callers import the nested type (`import com.onehostel.access.constants.AccessEnums.Role;`). The one exception is the `ErrorCode` catalogue (`exception/ErrorCode`, `CommonErrorCode`): it raises errors and its codes are an API contract, so it keeps its own file.

3. **Constructor injection only**: `@RequiredArgsConstructor` with `private final` fields. No `@Autowired` fields (ArchitectureTest fails the build).
4. **Errors come only from an ErrorCode catalogue**: `throw ErrorCode.BED_ALREADY_OCCUPIED.raise(bedNo);`. Shared codes are in `CommonErrorCode`, domain codes in the service's `exception/ErrorCode`. Never `new RuntimeException(...)`, never message strings in a constants class, never rename a code (the frontend matches on them).
5. **Every tenant-owned document extends `BaseDocument`** and so carries `organizationId`. Tenancy runs in **ENFORCE**: every Mongo operation is scoped to the caller's tenant at the BSON level. Platform-wide collections say so with `@GlobalCollection(reason = "...")`. Jobs and consumers set the tenant with `TenantContext.callAs(...)`.
6. **IDs are ULIDs** (assigned by `UlidIdCallback`). No `UUID`, no `ObjectId`.
7. **No secrets in the repo.** Every credential is an env var, read from the environment or the git-ignored `.env`.
8. **Readable Mongo field names**, one database per service, collection names in the service's `CollectionNames`. No opaque codes (`C0035`, `RAD0005`), no shared `CommonSearch` mirror: query the entity collection with compound indexes (`{organizationId:1, deleted:1, createdAt:-1}` plus the feature's filter fields).
9. **No god classes**: keep a service class under ~600 lines. A new workflow flow is one handler class plus a seed row, not another `switch`.
10. **Identity comes from `@AuthenticatedUser CurrentUser` and `@CurrentTenant`**, never from headers (ArchitectureTest ratchet at zero).
11. **Scheduled work is a `HostelJob`** (onehostel-scheduler-core). Raw `@Scheduled` is not allowed.

## Exception propagation contract

| Layer | Rule |
|---|---|
| Repository / integration edge | Translate technical failures into a domain code (e.g. `DuplicateKeyException` → `DUPLICATE_RECORD`, Keycloak 5xx → `IDENTITY_PROVIDER_UNAVAILABLE`). Never swallow. |
| Service layer | **Throw, don't catch.** Catch only to *recover* with a genuinely correct result, and write a comment saying why partial success is acceptable. |
| Best-effort side effects (notification push, search reindex, temp-file cleanup) | May be absorbed. Say so in a comment and log with the stack trace. |
| Money or persistent state | Never absorb silently. If it cannot propagate, **record a recovery task** (a failure document plus an admin dashboard). `log.warn` is not recovery. |
| Controller layer | **Do not catch.** Let `GlobalExceptionHandler` map it. Never `throw new RuntimeException(e.getMessage())`. |
| `@Async`, scheduled, Kafka listener | No caller and no advice. Use the explicit sinks: the async uncaught-exception handler (logs the correlation id), scheduler-core failure records, Kafka `DefaultErrorHandler` plus a DLT. |
| Servlet filters | Run before `DispatcherServlet`, so they write their own `ApiErrorResponse` body (`ErrorResponseWriter`). |

## Caching

- Declare every cache in the service's `CacheSpec` enum (implements `CacheSpecification`), named from `CacheNames` constants; build the manager with `CaffeineCaches.manager(CacheSpec.values())`.
- **Every cache key includes `organizationId`.**
- **Evict programmatically** (`AbstractCrudService.cacheNames()` / `evictCache()`, or an injected `CacheManager`), not `@CacheEvict` on a method that may be called from the same class.
- The TTL is a safety net, never the invalidation mechanism. Caffeine is per pod: past one replica, add cross-pod eviction or move to Redis.

## Adding a feature

1. `model/`: the document, `extends BaseDocument`, `@Document(CollectionNames.X)`, a compound index starting with `organizationId`. Use wrapper types (`Integer`, not `int`) for fields a PATCH may omit. Its enums are nested in `constants/<Service>Enums` (no separate enum files), embedded objects in `vo/`.
2. `EntityType` entry in `constants/<Service>Enums`, `exception/ErrorCode` entries for its domain rules.
3. `repository/`: `XRepository` if it needs derived queries beyond the generic CRUD.
4. `service/`: `extends AbstractCrudService<X>`, `getEntityType()`, hooks (`beforeCreate`, `beforeUpdate`, ...) for domain rules.
5. `controller/`: `extends AbstractCrudController<X>` with `@RequestMapping("/api/v1/<service>/<feature>")`; add only feature-specific endpoints, with their request / response records in `dto/`.
6. Privilege: the gateway refuses any `/api` call that no privilege covers.
   - A hostel module with an `apiPrefix` in `helper/PrivilegeCatalog` already covers generic CRUD under it.
   - Otherwise add the API to the right privilege, or append a new privilege.
   - Never rename a code, and never set a bit by hand.
7. Tests for the domain rules. `./gradlew build` must stay green.
