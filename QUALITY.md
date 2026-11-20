# Quality Assessment: V2 Pagination Contract Validation & Authorization Error Handling

## Scope and system understanding
I focused on the recently introduced version 2 pagination endpoints (`GET /api/v2/owners` and `GET /api/v2/pets`, added in `#328` and `#329`) and their contract, validation, and authorization error-handling boundaries.

End-to-end flow:
1. **OpenAPI contract & generated interfaces:** `src/main/resources/openapi.yml` defines `/v2/owners` (`listOwnersPage`, lines 139–205) and `/v2/pets` (`listPetsPage`, lines 872–924). During build, `openapi-generator-maven-plugin` (`pom.xml` lines 347–388, with `interfaceOnly` and `performBeanValidation`) generates `@Validated` interfaces `OwnerV2Api` and `PetV2Api` with `@Min(0)` on `page` (default `0`), `@Min(1) @Max(100)` on `size` (default `20`), and `400 Bad Request` (`ProblemDetail`) responses.
2. **Controllers & service/repository layers:** `OwnerRestControllerV2` and `PetRestControllerV2` implement those interfaces under `/api`, enforce `@PreAuthorize("hasRole(@roles.OWNER_ADMIN)")`, construct `PageRequest.of(pageNumber, pageSize, Sort.by("id"))`, and delegate to `ClinicServiceImpl` across three persistence profiles (`jdbc`, `jpa`, `spring-data-jpa`).
3. **Global exception mapping:** `ExceptionControllerAdvice` maps exceptions to RFC 7807 `ProblemDetail` payloads (`application/problem+json`) with sanitized messages and `schemaValidationErrors`.

### Baseline environment setup note (before counted time)
- **Starting revision:** `4cd8e1b0cd42578e882247d8801f6be5d402f118` on branch `qa-assessment`.
- **Host JDK cgroups v2 issue:** Running `./mvnw clean install` on Ubuntu with OpenJDK 17/18 initially failed during Spring `ApplicationContext` startup (`NullPointerException` in `jdk.internal.platform.cgroupv2.CgroupV2Subsystem.getInstance` via Micrometer `ProcessorMetrics`). Passing `-DargLine="-XX:-UseContainerSupport"` to Surefire resolved the host JVM container-detection bug without altering `pom.xml`, allowing the baseline suite (237 tests) to pass cleanly.

---

## Risks considered
1. **[High — Selected] OpenAPI contract & validation drift on pagination query parameters (`500` instead of `400`, plus missing `size` upper-bound enforcement):**
   - `ExceptionControllerAdvice` handled `MethodArgumentNotValidException` (request-body `@Valid` failures) as `400 Bad Request`, but lacked handlers for Spring Framework 6.1+/7 `HandlerMethodValidationException` (query/path `@Min`/`@Max` failures), `ConstraintViolationException`, and `MethodArgumentTypeMismatchException` (e.g., `?page=abc`). Any invalid query parameter fell into `@ExceptionHandler(Exception.class)` and returned `500 Internal Server Error`, while `V2RestControllersTests` used `MockMvcBuilders.standaloneSetup(...)` which bypassed method validation completely.
2. **[High — Selected as part of the advice boundary] Method-security denials (`AccessDeniedException` / `AuthorizationDeniedException`) converted to `500 Internal Server Error`:**
   - Because `ExceptionControllerAdvice` defines a catch-all `@ExceptionHandler(Exception.class)`, `@PreAuthorize("hasRole(@roles.OWNER_ADMIN)")` denials inside `DispatcherServlet` were caught as unexpected exceptions (`500`) instead of `403 Forbidden`. Existing controller tests only tested happy-path roles (`@WithMockUser(roles = "OWNER_ADMIN")`) on standalone `MockMvc` instances, so unauthorized role access was never verified.
3. **[Medium] Persistence profile parity & eager collection pagination (`FetchType.EAGER`):**
   - `Owner.pets` and `Pet.visits` use `FetchType.EAGER`. Under JPA (`JpaOwnerRepositoryImpl` / `JpaPetRepositoryImpl`), `setFirstResult`/`setMaxResults` with eager collections risks in-memory pagination (`HHH000104`), whereas JDBC applies SQL `LIMIT`/`OFFSET`. Furthermore, `AbstractClinicServiceTests` tests `findOwners(lastName, pageable)` across profiles but lacks an equivalent `findPets(pageable)` test.
4. **[Low-Medium] `ETag` and `304 Not Modified` contract-only headers:**
   - `openapi.yml` documents `ETag` headers on `200` responses (and `304` with header spelled `Etag` on `/v2/owners`), whereas neither v2 controller emits `ETag` or handles conditional `If-None-Match` requests.
5. **[Low] Prefix matching semantics on `lastName` filter:**
   - `lastName` performs a prefix match (`LIKE CONCAT(:lastName, '%')`), and an empty string `?lastName=` takes the filtered branch matching all owners, which is undocumented in `openapi.yml`.

---

## Changes and rationale
I kept the diff small, surgical, and isolated to two files:

1. **`src/main/java/org/springframework/samples/petclinic/rest/advice/ExceptionControllerAdvice.java`:**
   - Added `@ExceptionHandler(HandlerMethodValidationException.class)` and `@ExceptionHandler(ConstraintViolationException.class)` to map query/method parameter constraint failures (`@Min(0)`, `@Min(1)`, `@Max(100)`) to `400 Bad Request` `ProblemDetail` responses with structured `schemaValidationErrors` (`ValidationMessageDto`), matching the existing `MethodArgumentNotValidException` format without leaking stack traces.
   - Added `@ExceptionHandler(MethodArgumentTypeMismatchException.class)` so non-integer query parameters (e.g., `?page=abc`) return `400 Bad Request` `ProblemDetail` instead of `500`.
   - Added `@ExceptionHandler(AccessDeniedException.class)` (covering `AuthorizationDeniedException`) to return `403 Forbidden` `ProblemDetail` with a sanitized `"Access is denied"` message instead of falling through to the `500` general exception handler.
2. **`src/test/java/org/springframework/samples/petclinic/rest/controller/V2RestControllersTests.java`:**
   - Switched `MockMvc` setup from `standaloneSetup` to `MockMvcBuilders.webAppContextSetup(webApplicationContext).build()` so Spring MVC's parameter validation, `ExceptionControllerAdvice`, and `@PreAuthorize` AOP proxies execute end-to-end.
   - Added 16 new test cases (18 total in `V2RestControllersTests`) covering omitted defaults (`page=0, size=20`), valid boundaries (`size=1`, `size=100`), negative `page=-1`, `size=0`, oversized `size=101`, non-numeric `page=abc`, and unauthorized role (`VET_ADMIN` -> `403 Forbidden`) for both `/api/v2/owners` and `/api/v2/pets`.

This fits the 3-hour timebox cleanly while closing two real contract/security defects across both v2 endpoints and existing controllers without touching generated sources or unrelated business logic.

---

## Evidence
### Main verification command (from a clean checkout)
```bash
./mvnw clean verify -DargLine="-XX:-UseContainerSupport"
```
*(Note: On Linux/macOS/Windows environments without the OpenJDK 17/18 cgroups v2 `CgroupV2Subsystem` host issue, `./mvnw clean verify` also runs directly.)*

### Targeted v2 controller test command
```bash
./mvnw test -Dtest=V2RestControllersTests -DargLine="-XX:-UseContainerSupport"
```

### Observed results
- **Before fix (with `webAppContextSetup` and full Spring validation active):** `page=-1`, `size=0`, `size=101`, `page=abc`, and `VET_ADMIN` role requests all returned `500 Internal Server Error` (`handleGeneralException`).
- **After fix:**
  - `V2RestControllersTests`: `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0` (`400 Bad Request` with `ProblemDetail` and `schemaValidationErrors` on all invalid/out-of-bounds parameters; `403 Forbidden` on `VET_ADMIN`; `200 OK` on defaults and boundaries).
  - Full suite (`./mvnw clean verify -DargLine="-XX:-UseContainerSupport"`): `Tests run: 253, Failures: 0, Errors: 0, Skipped: 0` (`BUILD SUCCESS`).

---

## Confidence limits
- **What is proven:** Full Spring MVC request binding, Jakarta Bean Validation (`@Min`/`@Max`) on generated OpenAPI interfaces, `ProblemDetail` error formatting in `ExceptionControllerAdvice`, and `@PreAuthorize` method-level role enforcement for `/api/v2/owners` and `/api/v2/pets` work end-to-end without regressions across all 253 unit/integration tests.
- **What remains uncertain:**
  - `V2RestControllersTests` mocks `ClinicService`, so it does not verify real SQL/JPQL pagination behavior or `FetchType.EAGER` collection slicing across `jdbc`, `jpa`, and `spring-data-jpa` profiles.
  - Full HTTP filter-chain authentication (`petclinic.security.enable=true` with anonymous `401 Unauthorized` responses via `BasicAuthenticationConfig`) is disabled in the default test profile and was not exercised end-to-end over HTTP.

---

## Work left out
- **Full HTTP Basic authentication filter tests (`petclinic.security.enable=true`):** Left out to avoid duplicating Spring Security context configurations across test suites within the timebox.
- **Cross-profile repository integration tests for `findPets(Pageable)` and `lastName=""` edge cases:** Left out to keep the PR focused on the HTTP/validation/advice boundary.
- **`ETag` / `304 Not Modified` implementation in v2 controllers:** Left out because adding caching semantics changes API behavior beyond bug-fix scope and requires cache-invalidation design.

---

## Next step
With one more day, I would:
1. Add parameterized repository/service pagination tests in `AbstractClinicServiceTests` covering `findPets(Pageable)`, multi-page boundary offsets, and `lastName=""` across all three persistence profiles (`jdbc`, `jpa`, `spring-data-jpa`), asserting identical page ordering and zero `HHH000104` in-memory pagination warnings.
2. Add an end-to-end `@SpringBootTest` security profile test (`petclinic.security.enable=true`) verifying `401 Unauthorized` for unauthenticated requests and `403 Forbidden` for cross-role requests across v1 and v2 endpoints.

---

## Time spent
Approximately **1 hour 30 minutes** (counted work after initial environment setup and OpenJDK cgroups diagnosis).
