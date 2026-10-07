# 0004. The household agent processes video and sends events

**Status:** proposed. The current project charter says processing runs in the cloud; a change request approved by the advisor is required.

## Context
- **Bandwidth (team measurement):** streaming 480p at 8 fps as JPEG needs about 2.8 Mbit/s of constant upload, about 31 GB per day per camera.
- **CPU (team measurement):** MediaPipe *lite* runs on CPU at 6.5 ms per frame on a MacBook M5 Pro.
- **Evidence:** in the systematic review, the only direct edge-versus-cloud comparison (Mundody & Guddeti, 2026) reports lower latency under congestion and better privacy at the edge.

## Decision
The household agent estimates pose, classifies and sends only **events and clips**. The backend:
- receives events;
- issues pre-signed URLs to upload clips;
- publishes the current classification configuration.

See [AGENT_CONTRACT.md](../AGENT_CONTRACT.md).

## Consequences
- Video never leaves the home, except the event clip and the live view when a family member requests it.
- The EC2 instance runs no detection.
- The backend must distribute thresholds and versions to each agent.

Mundody, S., & Guddeti, R. M. R. (2026). Pose-based fall detection with robust feature analysis and privacy-aware edge-fog-cloud deployment. *IEEE Access, 14*, 114183–114208. https://doi.org/10.1109/ACCESS.2026.3716718
