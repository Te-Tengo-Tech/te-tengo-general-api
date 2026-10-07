# Architecture decision records

Michael Nygard's format: context, decision, status and consequences (Nygard, 2011). An accepted ADR is never edited; a new one supersedes it.

| No. | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith with Spring Modulith and hexagonal layers | Accepted |
| [0002](0002-per-household-multitenancy.md) | Multi-tenancy by `hogar_id` column with `@TenantId` | Accepted |
| [0003](0003-reqsai-conventions.md) | Conventions from `reqsai-api` (ProblemDetail, header versioning, UUID v7) | Accepted |
| [0004](0004-processing-on-household-pc.md) | The household agent processes video and sends events | Proposed (charter change request pending) |

Nygard, M. (2011, November 15). *Documenting architecture decisions*. Cognitect. https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
