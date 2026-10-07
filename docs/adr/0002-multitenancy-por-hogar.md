# 0002. Multi-tenancy por columna `hogar_id` con `@TenantId`

**Estado:** aceptada (2026-10-07)

## Contexto
Los datos de un hogar (cámaras, alertas, clips, consentimientos, accesos) **nunca** deben verse desde otro hogar (Ley N.° 29733). Habrá muchos hogares con pocos datos cada uno.

## Decisión
Particionar por discriminador: cada tabla del hogar tiene `hogar_id`, y `@TenantId` de Hibernate lo rellena y lo filtra automáticamente según el claim `hogar_id` del JWT. Si no hay hogar, la consulta falla cerrada. Detalle en [MULTITENANCY.md](../MULTITENANCY.md).

## Consecuencias
- Un solo esquema y una sola cadena de migraciones.
- El aislamiento no depende de que cada consulta recuerde el filtro.
- Cada funcionalidad necesita una prueba con dos hogares.
- Las consultas nativas requieren cuidado adicional; para ellas se recomienda *Row Level Security*.
