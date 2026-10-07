# te-tengo-general-api

[![CI](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/ci.yml)
[![OSV-Scanner](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/osv-scanner.yml/badge.svg)](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/osv-scanner.yml)
[![OWASP Dependency-Check](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/owasp.yml/badge.svg)](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/owasp.yml)
[![End-to-end](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/e2e.yml/badge.svg)](https://github.com/Te-Tengo-Tech/te-tengo-general-api/actions/workflows/e2e.yml)

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
./gradlew bootRun            # runs the API (profile `local`) and starts PostgreSQL and Floci via compose.yaml
```
- AWS services: with the `local` profile they run on [Floci](https://github.com/floci-io/floci), a local AWS emulator, at http://localhost:4566 ([ADR 0005](docs/adr/0005-floci-local-aws-emulator.md)). Clips go to its S3 (bucket `te-tengo-clips`, created at startup); the pre-signed URLs use that host, so the household agent on this machine and the iOS simulator can upload and play clips.
- E-mail and push: sent e-mails are listed at http://localhost:4566/_aws/ses and captured pushes at http://localhost:4566/_aws/sns/push-notifications. To see pushes on the iOS simulator or real phones, see [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md).
To try the three apps together, seed the prototype's demo household (account, household, consent and one agent installation) into the running API. With a path, the script also writes the desktop agent's configuration:
```bash
./scripts/seed-demo.sh ../te-tengo-desktop-pywebview/config.local.toml
```

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
| `TT_CLIPS_BUCKET` | S3 bucket of the clips; unset uses an in-memory fake (the `local` profile sets `te-tengo-clips` on Floci) | — |
| `TT_CLIPS_REGION` | AWS region of the bucket | SDK default chain |
| `TT_CLIPS_ENDPOINT` | Endpoint of an S3-compatible store; also the host of the pre-signed URLs | AWS |
| `TT_CLIPS_PATH_STYLE` | Path-style URLs (`endpoint/bucket/key`) | `false` |
| `TT_CLIPS_ACCESS_KEY` / `TT_CLIPS_SECRET_KEY` | Static credentials | SDK default chain |
| `TT_ENLACE_INVITACION` | Invitation link sent by e-mail; `{token}` is replaced | `tetengo://app/invitacion/{token}` |
| `TT_CORREO_PROVEEDOR` | E-mail adapter: `registro` (logs) or `ses` (Amazon SES) | `registro` (`ses` on Floci with the `local` profile) |
| `TT_SES_REMITENTE` | Sender of the e-mails, a verified SES identity; required with `ses` | — |
| `TT_SES_REGION` / `TT_SES_ENDPOINT` | SES region and endpoint override (credentials: SDK default chain) | SDK default chain / AWS |
| `TT_PUSH_PROVEEDOR` | Push adapter: `registro` (logs), `fcm`, `sns` or `simulador` ([NOTIFICATIONS.md](docs/NOTIFICATIONS.md)) | `registro` (`sns` on Floci with the `local` profile) |
| `TT_FCM_CREDENCIALES` | Path of the Firebase service-account JSON key; required with `fcm`; never commit it | — |
| `TT_SNS_ARN_ANDROID` / `TT_SNS_ARN_IOS` | SNS platform application ARNs; required with `sns` (both may be the same FCM application) | — |
| `TT_SNS_REGION` / `TT_SNS_ENDPOINT` | SNS region and endpoint override (credentials: SDK default chain) | SDK default chain / AWS |
| `TT_SIMULADOR_BUNDLE_ID` | Bundle id the `simulador` provider pushes to | `tech.tetengo.teTengo` |
| `TT_FLOCI_PUERTO` | Host port of Floci in `compose.yaml`, and the endpoint port of the `local` profile | `4566` |
| `TT_POSTGRES_PUERTO` | Host port of PostgreSQL in `compose.yaml` (`0`: a free port, found by Spring Boot) | `0` |

## Tests
```bash
./gradlew test               # unit + integration (Testcontainers, needs Docker) + architecture
./gradlew unitTest           # fast lane only
./gradlew spotlessApply      # format
```

### End-to-end smoke test (agent → API → alert)
`scripts/e2e.sh` checks the contract between the household agent and this API with the real programs. It:
- starts an isolated stack (compose project `tt-e2e`; PostgreSQL on 15432, Floci on 14566, the API on 18080), so it runs while your own stack is up;
- seeds the demo household (`seed-demo.sh`) and registers a push device for the family;
- runs the desktop agent without its UI (`--sin-interfaz`) on a URFD fall clip, cropped to its RGB half, with the last frame held 45 s;
- asserts, as the family, that a `CAIDA` alert appears and is pushed (provider `registro`), becomes `confirmada`, and that its clip becomes `DISPONIBLE` and downloads from Floci's S3;
- tears everything down and prints a PASS/FAIL summary with the detection → push latency.

```bash
# Needs Docker, JDK 25, jq, uv, python3 and te-tengo-desktop-pywebview next to this repository.
./scripts/e2e.sh
TT_E2E_ESCRITORIO=/path/to/te-tengo-desktop-pywebview TT_E2E_SIN_BUILD=1 ./scripts/e2e.sh
```

It takes about 1.5 minutes plus the API build. The fall clip is taken from the agent's `datos/urfd/` or downloaded. The ports, the work directory (logs are kept there) and the timeouts are set with the `TT_E2E_*` variables described at the top of the script.

In CI, the [End-to-end](.github/workflows/e2e.yml) workflow runs it on demand (any branch of the agent), weekly, and on pull requests that change the agent endpoints or the contract. The agent repository is private: add the `E2E_REPO_TOKEN` repository secret, a fine-grained personal access token with read-only *Contents* access to `Te-Tengo-Tech/te-tengo-desktop-pywebview`. Without the secret, the job is skipped with a notice.

## Documentation
| Document | Content |
|---|---|
| [AGENTS.md](AGENTS.md) | Repository rules for people and AI agents |
| [docs/WORK_PLAN.md](docs/WORK_PLAN.md) | Ordered task checklist and autonomous loop |
| [docs/API_CONTRACT.md](docs/API_CONTRACT.md) | HTTP API shared with the mobile app |
| [docs/AGENT_CONTRACT.md](docs/AGENT_CONTRACT.md) | API for the household agent |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules and their mapping to the system architecture |
| [docs/MULTITENANCY.md](docs/MULTITENANCY.md) | Per-household isolation with `@TenantId` |
| [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md) | E-mail and push providers, switching Firebase to SNS, local testing |
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
