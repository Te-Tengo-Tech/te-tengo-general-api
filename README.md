# te-tengo-general-api

**Backend API of Te Tengo**, a pose-estimation system that detects falls of older adults at home.

It receives the events detected by the household agent and serves the family member's mobile app: accounts, households, cameras, consent, alerts, escalation, live view and history.

| Stack | Version |
|---|---|
| Java | 25 (Temurin) |
| Spring Boot | 4.1 · Spring Modulith 2.1 |
| PostgreSQL | 18 · Flyway |
| Gradle | 9.7 (wrapper included) |

## Getting started
```bash
./scripts/generate-keys.sh   # local RS256 keys for JWTs (.claves/, not versioned)
./gradlew bootRun            # runs the API and starts PostgreSQL via compose.yaml
```
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Tests
```bash
./gradlew test               # unit + integration (Testcontainers, needs Docker) + architecture
./gradlew unitTest           # fast lane only
./gradlew spotlessApply      # format
```

## Documentation
| Document | Content |
|---|---|
| [AGENTS.md](AGENTS.md) | Repository rules for people and AI agents |
| [docs/WORK_PLAN.md](docs/WORK_PLAN.md) | Ordered task checklist and autonomous loop |
| [docs/API_CONTRACT.md](docs/API_CONTRACT.md) | HTTP API shared with the mobile app |
| [docs/AGENT_CONTRACT.md](docs/AGENT_CONTRACT.md) | API for the household agent |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules and their mapping to the system architecture |
| [docs/MULTITENANCY.md](docs/MULTITENANCY.md) | Per-household isolation with `@TenantId` |
| [docs/USE_CASE_GUIDE.md](docs/USE_CASE_GUIDE.md) | How to add a use case |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [docs/references/](docs/references/) | Product backlog and system architecture (Spanish source documents) |

## Claude Code in the cloud
The repository is ready for autonomous cloud sessions:
- `CLAUDE.md` imports `AGENTS.md`;
- a `SessionStart` hook installs JDK 25 and starts Docker;
- the `/work` command runs the work-plan loop.

Open a cloud session on this repository and type `/work`.

---

Thesis project, Software Engineering, Universidad Peruana de Ciencias Aplicadas (UPC). Authors: Jhosepmyr Gutierrez Soto and Elmer Riva Rodriguez.
