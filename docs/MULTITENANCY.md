# Per-household multi-tenancy

## The tenant
A **household** (`hogar`) groups:
- the home where the camera is installed;
- its older adult (one per account, CA-04.2);
- the linked family members (US-08).

One person can belong to several households, for example someone caring for both parents in two homes. That is why the household–user membership is a global table.

## Why a column instead of a schema per tenant
`reqsai-api` uses one schema per organization: few organizations, lots of data each. Te Tengo will have **many households with little data each**. A schema per household would multiply migrations and connections for no benefit, so this project uses Hibernate's **partitioned (discriminator) data** strategy (*Hibernate ORM User Guide*, ch. 23 "Multitenancy", sections 23.2.3 and 23.3.1).

## How it works
1. **The token carries the household:** the family member or the agent sends a JWT with the `hogar_id` claim.
2. **The filter binds it:** `FiltroHogarActual`, which runs after JWT validation, stores it in `HogarActual` (a ThreadLocal) and **clears it in `finally`**.
3. **Hibernate reads it:** `ResolvedorDeHogar` (`CurrentTenantIdentifierResolver<UUID>`) hands that household to every session.
4. **Filtering is automatic:** in entities extending `EntidadDelHogar`, the `hogarId` field carries `@TenantId`. Hibernate:
   - fills `hogar_id` on insert;
   - adds `hogar_id = ?` to **every** query, including `findById` and `findAll`.
5. **Fail closed:** with no household in context, a non-existent UUID (`ResolvedorDeHogar.SIN_HOGAR`) is used, so queries return nothing.

## Rules
- **Every household table has** `hogar_id uuid not null references hogares(id)` and an index starting with `hogar_id`.
- **Never accept the household** as a parameter (path or body), and never filter by hand.
- **Global tables** (`hogares`, accounts, memberships) do not extend `EntidadDelHogar`.
- **Mandatory test per feature:** two households, proving neither sees nor changes the other's data (`CamarasMultitenancyIntegrationTest`).
- **Optional defense in depth:** PostgreSQL row-level security with `set_config('app.hogar_id', ...)`, so even a native query that forgets the filter returns no rows from another household.

## Work outside a request: listeners and scheduled jobs
There is no JWT outside a web request, so the household must be bound explicitly:
- **Event listeners** (`@Async @TransactionalEventListener`) receive the `hogarId` in the event and run their work through `EjecutorEnHogar`, which binds the household and **then** opens a new transaction. Hibernate fixes a session's tenant when the session opens, so `@ApplicationModuleListener` (whose transaction starts before the method body) is not used for household data.
- **Scheduled jobs** first find their candidates across households with a native query that returns `(id, hogar_id)` pairs (native queries are not filtered), then process each one through `EjecutorEnHogar`, one transaction per household.

## The household agent
The agent receives a **per-camera token** when it registers. That token carries `hogar_id` and `camara_id`, so its events can only write to its own household. See [AGENT_CONTRACT.md](AGENT_CONTRACT.md).

## Reference
Hibernate. (n.d.). *Hibernate ORM User Guide*, ch. 23 "Multitenancy". https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html
