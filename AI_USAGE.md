# AI Usage Log

## Tools and models used
- **Cursor IDE (v3.31.11)** with **Grok 4.7** (used in Plan mode for read-only codebase exploration and in Agent mode for drafting edits to `ExceptionControllerAdvice.java` and `V2RestControllersTests.java`).
- **Chat Assistant (Gemini)** (used as a second perspective to review diffs, verify Spring MVC / Spring Security exception-handling mechanics, and structure verification steps).

---

## Tasks delegated to AI
1. **Codebase mapping & risk discovery:** Asked Cursor (`Grok 4.7`) in read-only Plan mode to trace the recently added `/api/v2/owners` and `/api/v2/pets` endpoints across generated OpenAPI interfaces, controllers, `ClinicServiceImpl`, JDBC/JPA/Spring Data repositories, mappers, `ExceptionControllerAdvice`, and existing tests.
2. **Drafting exception handlers & test cases:** Delegated the initial implementation of `HandlerMethodValidationException`, `ConstraintViolationException`, `MethodArgumentTypeMismatchException`, and `AccessDeniedException` handlers in `ExceptionControllerAdvice.java` and expanded boundary tests in `V2RestControllersTests.java`.

---

## Prompt / session summary
- **Session 1 (Read-only exploration):** Instructed Cursor to inspect `#328`/`#329` pagination end-to-end without modifying files and rank 4–5 quality risks. This quickly surfaced the mismatch between `openapi.yml` (`minimum: 0`, `minimum: 1`, `maximum: 100`, `400 Bad Request`) and runtime behavior (`500 Internal Server Error` or unbounded `size`).
- **Session 2 (Targeted implementation):** Provided a constrained prompt restricting edits strictly to `ExceptionControllerAdvice.java` and `V2RestControllersTests.java` while forbidding changes to `pom.xml`, `openapi.yml`, or unrelated controllers.
- **Session 3 (Verification & correction):** Reviewed `git diff` and Maven test execution outputs, caught duplicate code blocks and unhandled `AuthorizationDeniedException` (`500` instead of `403`), and refined `ExceptionControllerAdvice.java`.

---

## Independent verification
Every AI claim and generated code block was verified directly against the codebase and Maven execution:
1. **Manual source inspection:** Opened `OwnerRestControllerV2.java`, `PetRestControllerV2.java`, `ExceptionControllerAdvice.java`, `V2RestControllersTests.java`, and the generated `target/generated-sources/openapi/.../OwnerV2Api.java` to verify actual annotations (`@Validated`, `@Min(0)`, `@Min(1) @Max(100)`) and Spring MVC exception types.
2. **Test execution (`./mvnw test -Dtest=V2RestControllersTests -DargLine="-XX:-UseContainerSupport"`):** Executed the tests directly in the terminal and inspected both passing assertions and full stack traces for failures.
3. **Full regression suite (`./mvnw clean verify -DargLine="-XX:-UseContainerSupport"`):** Verified all 253 tests across all modules and persistence profiles compile and pass from a clean build.
4. **Line-by-line `git diff` review:** Audited every added import, method signature, Javadoc comment, and assertion before accepting the changes.

---

## Rejected or changed suggestions
1. **Rejected implementing `ETag` / `304 Not Modified` caching and altering `FetchType.EAGER` entity mappings:** The initial AI risk report flagged missing `ETag`/`304` responses and eager collection fetching on `Owner`/`Pet`. I rejected modifying entity fetch strategies or adding HTTP caching because changing core JPA fetch plans risks subtle `LazyInitializationException` regressions (`spring.jpa.open-in-view=false`) and exceeds a safe, focused PR scope.
2. **Rejected Cursor running `mvn -q -Dtest=V2RestControllersTests test` directly without `-DargLine`:** Skipped Cursor's proposed shell execution because it omitted `-DargLine="-XX:-UseContainerSupport"`, which would have triggered the host OpenJDK cgroups v2 `NullPointerException`.
3. **Modified `ExceptionControllerAdvice.java` after initial AI draft:** Added `@ExceptionHandler(AccessDeniedException.class)` returning `403 Forbidden` after test execution proved that `@PreAuthorize` failures were being swallowed by `@ExceptionHandler(Exception.class)` as `500 Internal Server Error`.

---

## AI mistakes / misunderstandings
1. **Incorrect root-cause explanation during initial analysis:** In its initial exploration report, `Grok 4.7` stated that query parameters were unvalidated because controller overrides lacked `@Min`/`@Max` and that `PageRequest.of` threw `IllegalArgumentException` mapped to `500`. Inspecting generated `OwnerV2Api.java` and running `webAppContextSetup` tests proved that Spring MVC *did* enforce `@Min`/`@Max` from the generated interface, throwing `HandlerMethodValidationException`—which `ExceptionControllerAdvice` failed to handle, causing the `500` response.
2. **Duplicate method insertion in `ExceptionControllerAdvice.java`:** During the first edit pass, Cursor duplicated `handleMethodArgumentTypeMismatchException` and a partial `toValidationMessages` block in `ExceptionControllerAdvice.java`. Catching this during `git diff` review prevented a compilation failure.
3. **Overlooked `AccessDeniedException` interaction with `@ControllerAdvice`:** Switching `V2RestControllersTests` to `webAppContextSetup` exposed that `ExceptionControllerAdvice`'s catch-all `Exception.class` handler intercepted `AuthorizationDeniedException` and returned `500` instead of `403`, requiring an explicit `AccessDeniedException` handler.

---

## What you would not merge without more verification
- **Full security filter-chain integration (`petclinic.security.enable=true`):** While `@PreAuthorize` method security is now verified to return `403 Forbidden` via `ProblemDetail`, I would not merge changes affecting security responses without also running an integration test with `petclinic.security.enable=true` to confirm `401 Unauthorized` HTTP Basic challenges and `AccessDeniedHandler` behavior remain consistent across both Spring Security filters and `@ControllerAdvice`.
- **Database-backed pagination tests under `jpa` and `jdbc` profiles:** Before relying on v2 pagination in production with large datasets, I would verify `findPets(Pageable)` and `findOwners(lastName, Pageable)` against real SQL queries rather than mocked `ClinicService` calls.
