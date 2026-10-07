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
./gradlew bootRun            # runs the API (profile `local`) and starts PostgreSQL and SeaweedFS via compose.yaml
```
- Clips: with the `local` profile they go to SeaweedFS, an S3-compatible store, at http://localhost:8333 (bucket `te-tengo-clips`, created at startup). The pre-signed URLs use that host, so the household agent on this machine and the iOS simulator can upload and play clips.
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Configuration
| Variable | Purpose | Default |
|---|---|---|
| `TT_JWT_CLAVE_PUBLICA` | RS256 public key (X.509 PEM) that validates every token | `file:.claves/publica.pem` |
| `TT_JWT_CLAVE_PRIVADA` | RS256 private key (PKCS#8 PEM) that signs the tokens the API issues | `file:.claves/privada.pem` |
| `TT_ENLACE_RECUPERACION` | Password-reset link sent by e-mail; `{token}` is replaced | `tetengo://app/nueva-contrasena?token={token}` |
| `TT_URL_TRANSMISION` | Base of the live view stream URL (`wss://` in production) | `ws://localhost:8080` |
| `TT_RETENCION_CLIPS` | How long clips are kept, e.g. `30d` (unset: kept; pending, see BLOCKERS) | — |
| `TT_AGENTE_VERSION_PUBLICADA` | Agent release published by `GET /api/agente/configuracion` (thresholds: `tetengo.agente.umbrales`, empty by default) | `0.2.0` |
| `TT_CLIPS_BUCKET` | S3 bucket of the clips; unset uses an in-memory fake (the `local` profile sets `te-tengo-clips` on SeaweedFS) | — |
| `TT_CLIPS_REGION` | AWS region of the bucket | SDK default chain |
| `TT_CLIPS_ENDPOINT` | Endpoint of an S3-compatible store; also the host of the pre-signed URLs | AWS |
| `TT_CLIPS_PATH_STYLE` | Path-style URLs (`endpoint/bucket/key`) | `false` |
| `TT_CLIPS_ACCESS_KEY` / `TT_CLIPS_SECRET_KEY` | Static credentials | SDK default chain |
| `TT_ENLACE_INVITACION` | Invitation link sent by e-mail; `{token}` is replaced | `tetengo://app/invitacion/{token}` |

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
