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
