@AGENTS.md

## Notas para Claude Code
- En la nube, el hook `SessionStart` (`scripts/cloud/preparar-entorno.sh`) instala el JDK 25 y arranca Docker. Si `./gradlew test` falla por Java o Docker, revisa primero ese script.
- Respeta las reglas del usuario: mensajes en español, Conventional Commits **sin** línea de coautor, y no inventar datos: todo valor de negocio sale del backlog (`docs/referencias/PRODUCT_BACKLOG.md`).
