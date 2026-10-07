# Cómo contribuir

1. **Rama desde `main`:** `feat/<modulo>-<tema>`, `fix/...` o `docs/...`.
2. **Hooks:** `lefthook install`, una vez. Formatea el código antes de cada commit.
3. **Antes de subir:** `./gradlew spotlessApply test` debe pasar completo.
4. **Commits** en [Conventional Commits 1.0.0](https://www.conventionalcommits.org/es/v1.0.0/) y en español, **sin línea de coautor**. Por ejemplo: `feat(alertas): recibir eventos del agente de la vivienda`.
5. **Pull request** hacia `main`; la CI repite las pruebas.

Para agregar funcionalidad, sigue [docs/GUIA_CASOS_DE_USO.md](docs/GUIA_CASOS_DE_USO.md).
