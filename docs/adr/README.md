# Decisiones de arquitectura (ADR)

Formato de Michael Nygard: contexto, decisión, estado y consecuencias (Nygard, 2011). Un ADR aceptado no se edita: si la decisión cambia, se escribe otro que lo reemplace.

| N.° | Decisión | Estado |
|---|---|---|
| [0001](0001-monolito-modular.md) | Monolito modular con Spring Modulith y capas hexagonales | Aceptada |
| [0002](0002-multitenancy-por-hogar.md) | Multi-tenancy por columna `hogar_id` con `@TenantId` | Aceptada |
| [0003](0003-convenciones-de-reqsai.md) | Convenciones de `reqsai-api` (ProblemDetail, versión por cabecera, UUID v7) | Aceptada |
| [0004](0004-procesamiento-en-la-vivienda.md) | El agente procesa el video y envía eventos | Propuesta (solicitud de cambio pendiente) |

Nygard, M. (2011, 15 de noviembre). *Documenting architecture decisions*. Cognitect. https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
