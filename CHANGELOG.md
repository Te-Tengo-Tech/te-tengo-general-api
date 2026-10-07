# Registro de cambios

Formato basado en [Keep a Changelog 1.1.0](https://keepachangelog.com/es-ES/1.1.0/).

## [0.1.0] - 2026-10-07
### Agregado
- **Base del monolito modular:** Spring Boot 4.1, Java 25 y Spring Modulith, con los módulos `cuentas`, `hogares`, `camaras`, `alertas`, `monitoreo`, `historial` y `shared`.
- **Multi-tenancy por hogar:** `@TenantId`, claim `hogar_id` del JWT y falla cerrada.
- **Errores** en RFC 9457 `ProblemDetail` y **versionado** por cabecera `Api-Version`.
- **Slice de referencia `camaras`:** listar y renombrar (US-06), con pruebas de dominio, de integración multi-tenancy y de arquitectura.
- **Herramientas:** Flyway, Testcontainers, Spotless con lefthook, JaCoCo y CI en GitHub Actions.
- **Preparación para Claude Code en la nube:** `AGENTS.md`, `CLAUDE.md` y el hook de entorno.
