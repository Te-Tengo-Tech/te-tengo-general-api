# 0001. Modular monolith with Spring Modulith and hexagonal layers

**Status:** accepted (2026-10-07)

## Context
A two-person team, a one-household pilot and a single EC2 t3.small. Microservices would add deployment and communication overhead with no benefit at this stage.

## Decision
One Spring Boot service, with one module per backlog epic. Spring Modulith and ArchUnit verify the module boundaries in tests, and each module has hexagonal layers (`domain`, `application`, `infrastructure`, `interfaces`), as in `reqsai-api`.

## Consequences
- A single deployment.
- Internal boundaries are checked on every build.
- A module can be extracted later if scale requires it.
