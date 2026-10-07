# How to add a use case

Example: **US-06 / CA-06.2**, renaming a camera's room. It is the `camaras` slice.

1. **Read the story and its criteria** in `docs/references/PRODUCT_BACKLOG.md`, and its endpoint in `docs/API_CONTRACT.md`. Each Given/When/Then criterion becomes at least one test.
2. **Domain:** the rule goes in the aggregate (`Camara.renombrar`), which throws `ErrorDeNegocio` with a code from the module's `enum` (`CamaraError.NOMBRE_VACIO`).
   - Test: `CamaraTest`, no Spring.
3. **Migration**, if there are new tables: `V<n>__<snake_case>.sql`, with `hogar_id` for household data.
4. **Port and adapter:**
   - the port goes in `application/port` (`CamaraRepository`);
   - the adapter goes in `infrastructure/persistence`, with a package-private `JpaRepository`.
5. **Use case:** one class per action in `application` (`RenombrarCamara`), with `@Transactional`. Its Javadoc cites the story and criterion.
6. **REST:**
   - the controller goes in `interfaces/rest`, with `version = ApiVersioning.V1`;
   - DTOs are `record`s;
   - the mapper is static.
   - Paths, fields and error codes must match the API contract exactly.
7. **Integration test:** extend `AbstractIntegrationTest`. Create members with `ApiDePrueba.titularConHogar` (through the API) or `DatosDePrueba.hogar` plus `JwtDePrueba.token(usuario, hogar, rol)`: a token only opens a household its user belongs to. It must cover:
   - the happy path;
   - each error as a `ProblemDetail` with its `codigo`;
   - **isolation between two households**.
8. **Finish:**
   ```bash
   ./gradlew spotlessApply test
   ```
   Everything must pass, including the architecture tests. Then make one Conventional Commit and check the task off in `docs/WORK_PLAN.md`.
