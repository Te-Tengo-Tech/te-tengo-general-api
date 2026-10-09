# Architecture decision records

Michael Nygard's format: context, decision, status and consequences (Nygard, 2011). An accepted ADR is never edited; a new one supersedes it.

| No. | Decision | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith with Spring Modulith and hexagonal layers | Accepted |
| [0002](0002-per-household-multitenancy.md) | Multi-tenancy by `hogar_id` column with `@TenantId` | Accepted |
| [0003](0003-reqsai-conventions.md) | Conventions from `reqsai-api` (ProblemDetail, header versioning, UUID v7) | Accepted |
| [0004](0004-processing-on-household-pc.md) | The household agent processes video and sends events | Proposed (charter change request pending) |
| [0005](0005-floci-local-aws-emulator.md) | Floci as the local AWS emulator (S3, SES, SNS) | Accepted |
| [0006](0006-push-provider-switch.md) | Push provider chosen by configuration (registro, FCM, SNS, iOS simulator) | Accepted |
| [0007](0007-live-view-through-mediamtx.md) | Live view through MediaMTX (LL-HLS, API-authorized tokens, agent control channel) | Accepted |
| [0008](0008-live-view-webrtc-whep.md) | Live view over WebRTC (WHEP) with LL-HLS as the fallback, and `preparar` | Accepted |

Nygard, M. (2011, November 15). *Documenting architecture decisions*. Cognitect. https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions
