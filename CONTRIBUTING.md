# Contributing

1. **Branch from `develop`:** `feat/<module>-<topic>`, `fix/...` or `docs/...`. `main` only receives releases merged from `develop`.
2. **Hooks:** run `lefthook install` once. It formats code before each commit and checks the message format.
3. **Before pushing:** `./gradlew spotlessApply test` must pass.
4. **Commits:** [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) in English, **no co-author line**. Example: `feat(alertas): receive events from the household agent`.
5. **Pull request** to `develop`; CI runs the full test suite.

To add a feature, follow [docs/USE_CASE_GUIDE.md](docs/USE_CASE_GUIDE.md).
