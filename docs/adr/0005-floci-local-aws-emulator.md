# 0005. Floci as the local AWS emulator

**Status:** accepted.

## Context
- The API uses three AWS services in production: **S3** (clips, pre-signed URLs), **SES** (e-mail) and **SNS** (mobile push). Local runs and adapter tests must not call AWS (AGENTS.md).
- Until now `compose.yaml` ran **SeaweedFS**, which only covers S3. SES and SNS had logging fakes only, so their adapters could not be exercised end to end before having an AWS account.
- **Floci** (https://github.com/floci-io/floci, MIT) is a LocalStack-compatible emulator on port 4566 that covers S3, SES (v1 and v2) and SNS, including mobile push platform applications and endpoints.

## Evaluation (Floci 2.2.0, `floci/floci:2.2.0`, 2026-10-07)
| Need | Result |
|---|---|
| S3 pre-signed PUT and GET from the AWS SDK v2, path-style | Work. With `FLOCI_AUTH_VALIDATE_SIGNATURES=true` a tampered URL answers `403`, as Amazon S3 does (`AlmacenamientoDeClipsEnS3IntegrationTest`) |
| SES v2 `SendEmail` | Works; sent messages are listed at `GET /_aws/ses` |
| SNS `CreatePlatformApplication` (`GCM`, `FCM`, `APNS`, `APNS_SANDBOX`), `CreatePlatformEndpoint`, `Publish` with `MessageStructure=json` | Work; nothing reaches FCM or APNs, every push is captured at `GET /_aws/sns/push-notifications`. A disabled endpoint answers `EndpointDisabled`, as in AWS |
| Startup | About 1 s with `FLOCI_SERVICES_ECS_RECONCILE_CONTAINERS_ON_STARTUP=false` (without a Docker socket the ECS clean-up retries for about 15 s) |

## Decision
- **Floci replaces SeaweedFS** in `compose.yaml` (host port **4566**, image pinned to `floci/floci:2.2.0`, `hybrid` storage on a named volume so clips survive restarts, signature verification on).
- The `local` profile (`application-local.yml`) points S3, SES and SNS to `http://localhost:4566` with Floci's built-in `test`/`test` keys (the only keys it accepts while it verifies signatures).
- Adapter integration tests start Floci with Testcontainers (`GenericContainer`, `tech.tetengo.api.support.Floci`), so they test the same emulator developers run.

## Consequences
- One container emulates every AWS service the API uses; the production adapters run locally without an AWS account.
- Floci is not AWS: SES does not require a verified sender and SNS does not reach FCM or APNs. Production behavior still needs the checks listed in [BLOCKERS.md](../BLOCKERS.md).
- Developers who ran the old stack get a new `floci` service on port 4566; the `seaweedfs` container and its data are no longer used (`docker compose down` removes the orphan).
