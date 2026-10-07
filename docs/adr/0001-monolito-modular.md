# 0001. Monolito modular con Spring Modulith y capas hexagonales

**Estado:** aceptada (2026-10-07)

## Contexto
Un equipo de dos personas, un piloto de una vivienda y una EC2 t3.small. Los microservicios agregarían despliegue y comunicación sin beneficio en esta etapa.

## Decisión
Un solo servicio Spring Boot, con un módulo por épica del backlog. Spring Modulith y ArchUnit verifican sus límites en las pruebas, y cada módulo tiene capas hexagonales (`domain`, `application`, `infrastructure`, `interfaces`), igual que `reqsai-api`.

## Consecuencias
- Un solo despliegue.
- Los límites internos se comprueban en cada build.
- Un módulo podría extraerse después si la escala lo exige.
