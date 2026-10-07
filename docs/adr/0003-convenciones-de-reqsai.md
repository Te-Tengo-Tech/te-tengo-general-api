# 0003. Convenciones de `reqsai-api`

**Estado:** aceptada (2026-10-07)

## Contexto
El equipo ya trabaja con `reqsai-api` (Java 25, Spring Boot 4, Spring Modulith), que tiene convenciones probadas. Reutilizarlas reduce decisiones y curva de aprendizaje.

## Decisión
Se adoptan:
- errores RFC 9457 `ProblemDetail` con `codigo`;
- versionado nativo por cabecera `Api-Version`;
- UUID v7 generados en el constructor;
- puertos en `application/port` y `JpaRepository` internos;
- DTO como `record` y mappers estáticos;
- Testcontainers con PostgreSQL;
- carriles de pruebas `unitTest`, `integrationTest` y `architectureTest`;
- Spotless con lefthook.

**Diferencia:** multi-tenancy por columna en lugar de un esquema por tenant (ADR 0002).

## Consecuencias
El código sigue patrones conocidos, y un agente puede copiar el slice `camaras` como referencia.
