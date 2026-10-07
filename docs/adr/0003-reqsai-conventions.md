# 0003. Conventions from `reqsai-api`

**Status:** accepted (2026-10-07)

## Context
The team already works with `reqsai-api` (Java 25, Spring Boot 4, Spring Modulith), which has proven conventions. Reusing them reduces decisions and the learning curve.

## Decision
The project adopts:
- RFC 9457 `ProblemDetail` errors with `codigo`;
- native `Api-Version` header versioning;
- UUID v7 generated in constructors;
- ports in `application/port` and package-private `JpaRepository` interfaces;
- `record` DTOs and static mappers;
- Testcontainers with PostgreSQL;
- `unitTest`, `integrationTest` and `architectureTest` lanes;
- Spotless with lefthook.

**Difference:** column-based multi-tenancy instead of a schema per tenant (ADR 0002).

## Consequences
The code follows known patterns, and an agent can copy the `camaras` slice as a reference.
