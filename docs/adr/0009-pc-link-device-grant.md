# 0009. PC link with the OAuth 2.0 device grant, implemented in-house

**Status:** proposed (the change request «PCs, cámaras y datos de movimiento» of 2026-10-10 is pending the advisor's approval; built ahead of it at the project owner's request, additively, so installation credentials keep working).

## Context
- Today the project team installs the agent with a fixed configuration and an installation credential (`scripts/create-installation.sh`), one per webcam. US-28 asks for the opposite: the owner installs Te Tengo Captura and links the PC from the app by scanning a QR or typing an 8-letter code, with no secret typed or copied on the PC; US-30 allows several PCs per household; US-31 removes a PC from the app or from the PC itself.
- The change request (section 2.1) settles the mechanism: the OAuth 2.0 Device Authorization Grant (RFC 8628), a user code of 8 letters from `BCDFGHJKLMNPQRSTVWXZ` shown as `XXXX-XXXX`, single use and valid for 10 minutes, polling every 5 s with `authorization_pending`, `slow_down`, `access_denied` and `expired_token`, the approval screen against remote phishing (§5.4), 5 wrong codes → 15-minute block (CA-28.6, like CA-02.3), a short-lived access token with a rotating refresh token (RFC 9700 §2.2.2, §4.14.2) and a new link after 90 days without connecting (CA-28.7).
- The API already issues its own RS256 JWTs (`EmisorDeTokens`) and validates them as a resource server. Agent tokens carry `hogar_id`, `camara_id` and `rol = AGENTE`, and everything the agent does (heartbeat, events, clips, the live view control channel) reads the camera from its token.

## Options
1. **Spring Authorization Server** (1.x/2.x). It implements RFC 8628 (`DeviceAuthorizationEndpoint`, `DeviceVerificationEndpoint`), refresh token rotation for public clients and RFC 7009.
   - Its verification step is a browser flow: the user signs in to the authorization server (session cookie, login form) and approves on a consent page. Here the owner approves inside the mobile app with the API's own bearer token, for the household in that token, only as `TITULAR`; that means customizing the verification endpoint and the consent service and replacing the login with the existing JWT.
   - It needs its own JDBC schema (`oauth2_authorization`, `oauth2_registered_client`, `oauth2_authorization_consent`) that stores every token of every PC, a second security filter chain, a second token format or a custom `OAuth2TokenGenerator` to keep the API's claims, and a `RegisteredClientRepository`.
   - The household (the tenant) is decided by the approver, not the client; the PC must be a household entity (US-30 lists it with its cameras). That state would live in two places.
   - Brute-force limits per account (CA-28.6) and the 90-day rule (CA-28.7) are not built in either.
2. **A focused in-house implementation** of exactly the three endpoints the PC needs, following the RFCs to the letter, inside the `camaras` module, with the PC as a household aggregate.

## Decision
Option 2, in-house:
- **Endpoints**, form-encoded, snake_case JSON, RFC 6749 §5.2 errors, `Cache-Control: no-store`:
  - device authorization `POST /api/agente/vinculacion` (RFC 8628 §3.1–3.2);
  - token `POST /api/agente/token` with `urn:ietf:params:oauth:grant-type:device_code` (RFC 8628 §3.4–3.5) and `refresh_token` (RFC 6749 §6);
  - revocation `POST /api/agente/token/revocacion` (RFC 7009): revoking the refresh token unlinks the PC (CA-31.3).
  - The approval is part of the API, not of an authorization server: `POST /api/vinculaciones/consulta`, `/aprobacion` and `/rechazo`, owner only, the code in the body (never in a URL, so it stays out of access logs).
- **Codes.** `device_code`: 256 random bits (RFC 8628 asks for enough entropy; 128 bits is the floor). `user_code`: 8 letters of the 20 consonants, about 34.6 bits, unique among pending requests. Both are stored only as SHA-256. A request expires after 10 minutes; once approved it can still be redeemed for 2 minutes even if its 10 minutes run out meanwhile. A device code is redeemed once; the user code works once (`410 CODIGO_VENCIDO` afterwards).
- **Polling.** The server keeps the last poll and the interval of each request; a poll sooner than the interval answers `slow_down` and adds 5 s (RFC 8628 §3.5).
- **Brute force (RFC 8628 §5.1, CA-28.6).** Wrong codes are counted per account; 5 in a row lock code entry for 15 minutes (`423 VINCULACION_BLOQUEADA {bloqueadaHasta}`); a code that exists resets the count. With 34.6 bits, 5 tries per 15 minutes per account and 10-minute codes, guessing a live code is impractical.
- **Remote phishing (§5.4).** The app shows the PC's name, system and agent version before «Aprobar» (`consulta`), and only the household's `TITULAR` approves; the PC is linked to the approver's household.
- **Tokens.**
  - Access token: the API's own RS256 JWT, `sub` = PC, `hogar_id`, `pc_id`, `rol = AGENTE`, 1 hour [implementation choice]. The PC's cameras get per-camera tokens of the same lifetime with `camara_id` and `pc_id`, so every existing agent endpoint keeps reading its camera from the token.
  - Refresh token: 256 random bits, stored hashed, one row per token, each valid for 90 days from issue (CA-28.7: a PC that connects at least once every 90 days stays linked). Each use rotates it; a rotated token presented again revokes every token of the PC and unlinks it (RFC 9700 §4.14.2), and the family is notified. An expired one unlinks the PC too; a daily job unlinks PCs with no token for 90 days, so the app stops listing them.
- **Immediate revocation.** JWTs are stateless, so `FiltroHogarActual` checks every agent token against the database (`ComprobadorDeAgentes`, two indexed lookups): the token of an unlinked PC, or of a retired camera, answers `401 PC_DESVINCULADA` before anything else, also on the WebSocket handshake. Tokens of installation credentials are checked too: removing an installation's PC also invalidates its credential.
- **Control channel.** Besides one channel per camera, a linked PC opens one with its own token. Unlinking sends `{"desvincular":true}` on all of them and closes them (code 4001). An offline PC learns it at its next request.
- **Model.** `Pc` is a household aggregate (`pcs`); cameras get `pc_id` and `retirada_en`. Link requests, refresh tokens and wrong-code counters are global tables, because they are read before any household is known. Migration `V24` turns each existing installation with a camera into a PC of origin `INSTALACION`.

## Consequences
- About a dozen small classes instead of a framework, and no second token format: every agent endpoint, the live view control channel and the multi-tenancy rules stay as they are. The code follows the RFC sections it cites, and the security properties are covered by integration tests (expiry, single use, `slow_down`, brute force, owner only, rotation and reuse detection, revocation, revoked PC rejected, 90 days).
- If the system later needs standard OAuth for other clients (third parties, OpenID Connect), Spring Authorization Server should replace this, keeping the endpoints' wire format.
- One more query per agent request (heartbeats every 30 s per camera). Cheap, and it makes unlinking immediate.
- The device authorization endpoint is public, like sign-up: a client could create many pending requests. Requests are small and deleted a day after they expire; rate limiting by IP belongs to the reverse proxy (docs/BLOCKERS.md).
- The `verification_uri` (`https://app.tetengo.reqsai.tech/vincular`) must open the app's code screen; the domain is pending confirmation.

## References
- Denniss, W., Bradley, J., Jones, M., & Tschofenig, H. (2019). *OAuth 2.0 Device Authorization Grant* (RFC 8628). IETF. https://www.rfc-editor.org/rfc/rfc8628
- Hardt, D. (Ed.). (2012). *The OAuth 2.0 Authorization Framework* (RFC 6749). IETF. https://www.rfc-editor.org/rfc/rfc6749
- Lodderstedt, T., Bradley, J., Labunets, A., & Fett, D. (2025). *Best Current Practice for OAuth 2.0 Security* (RFC 9700). IETF. https://www.rfc-editor.org/rfc/rfc9700
- Lodderstedt, T. (Ed.), Dronia, S., & Scurtescu, M. (2013). *OAuth 2.0 Token Revocation* (RFC 7009). IETF. https://www.rfc-editor.org/rfc/rfc7009
