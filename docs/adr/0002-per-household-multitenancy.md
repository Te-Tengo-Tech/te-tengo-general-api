# 0002. Multi-tenancy by `hogar_id` column with `@TenantId`

**Status:** accepted (2026-10-07)

## Context
A household's data (cameras, alerts, clips, consents, access logs) must **never** be visible from another household (Peruvian Law No. 29733). There will be many households with little data each.

## Decision
Partition by discriminator: every household table has `hogar_id`, and Hibernate's `@TenantId` fills and filters it automatically from the `hogar_id` JWT claim. With no household, queries fail closed. Details in [MULTITENANCY.md](../MULTITENANCY.md).

## Consequences
- One schema and one migration chain.
- Isolation does not depend on every query remembering the filter.
- Every feature needs a two-household test.
- Native queries need extra care; row-level security is recommended for them.

## Follow-up (2026-10-07, T24): row-level security not enabled yet
Row-level security was evaluated as optional defense in depth and left out for now:
- The only native queries are the scheduled jobs' candidate searches across households, plus the stream-token and installation lookups. They return ids only, and the work then happens through `EjecutorEnHogar` with the `@TenantId` filter.
- The application connects as the table owner, which PostgreSQL exempts from policies unless `FORCE ROW LEVEL SECURITY` is set. Enabling it needs a separate, non-owner application role, `set_config('app.hogar_id', ..., true)` at the start of every transaction, and a bypass role for those cross-household job queries.
- Every feature already has a two-household integration test.

Enable it together with the separate database role when the production database is set up.
