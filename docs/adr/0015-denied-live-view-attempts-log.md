# 0015. Denied live view attempts, recorded in an independent transaction

**Status:** accepted (2026-11-15).

## Context
- A security measurement of the thesis must report how many unauthorized attempts to reach a camera's live view were **rejected and logged**. Until now only granted sessions were recorded (`accesos_vista_en_vivo`, `GET /api/accesos-vista-en-vivo`, US-24). A refusal left no trace: the REST use cases throw `ErrorDeNegocio` (`404 CAMARA_NO_ENCONTRADA`, `409 SIN_CONSENTIMIENTO`, `404 SESION_NO_ENCONTRADA`), and MediaMTX's authorization hook (`AutorizarMediaMtx`, ADR 0007) only wrote an application log line («MediaMTX: … denegado»).
- **The refusals happen inside transactions.** `AbrirVistaEnVivo`, `PrepararVistaEnVivo`, `AbrirVistaPrevia`, `CambiarModoDeVistaEnVivo` and `CerrarVistaEnVivo` are `@Transactional` and deny by throwing; Spring rolls the transaction back on that runtime exception, so an insert made in the same transaction would disappear with it. The hook reads and writes through `EjecutorEnHogar`, one transaction per household.
- **The refused request is often not of the camera's household.** A member of household B asking for a camera of household A is exactly the case to report, so the denial belongs to A (the household that must see it), while the tenant in context is B. A camera id may also exist nowhere, and the hook has no household at all (no JWT).
- Tokens, publish keys and the hook's secret must never be stored (ADR 0007 stores only token hashes).

## Options considered
- **Insert in the use case's transaction:** lost on rollback. Rejected.
- **Catch the exception in the controller or a `@ControllerAdvice` and record there:** runs after the rollback, but loses the camera and the reason for the hook (which does not throw), and couples recording to HTTP error mapping. Rejected.
- **Domain event with `@TransactionalEventListener(phase = AFTER_ROLLBACK)`:** works for the REST cases, but events registered on aggregates are published on save, which a refusal never reaches, and the hook has no surrounding transaction. More moving parts for the same result.
- **A small recorder that writes in its own transaction (`REQUIRES_NEW`), called at the point of refusal (chosen).**

## Decision
- **`RegistroDeAccesosDenegados`** (`monitoreo.application`) records each denial through a `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW`: the caller's transaction is suspended, the denial is committed on its own connection, and the caller then throws and rolls back as before. A failure to record is logged (`warn`) and swallowed: the request is refused either way, and recording never turns a denial into a `500`.
- **Table `accesos_denegados`** (migration V34): `id`, `ocurrido_en`, `canal` (`API` | `MEDIAMTX`), `motivo`, `accion` and `protocolo` (MediaMTX's, cut to 20 characters), `camara_id` (no foreign key, so unknown ids are kept), `hogar_id` (the **camera's** household, nullable), `usuario_id` and `hogar_del_usuario` (who asked and from which household, when known: the member of the request, or the viewer of the session whose token MediaMTX was given). No token, key, secret or path is stored.
- **Multi-tenancy:** rows are written with a native insert, because the household in context (if any) is the requester's, not the camera's; `hogar_id` comes from `CamarasDelHogar.hogarDeCamara`, a native lookup across households used only here. Reading is ordinary: `AccesoDenegado` extends `EntidadDelHogar`, so `@TenantId` filters it to the household in context. Rows with a null `hogar_id` (a camera that exists nowhere) count for the measurement but no household lists them. This is the one household table whose `hogar_id` may be null; `docs/MULTITENANCY.md`'s rule of a non-null `hogar_id` does not apply to it.
- **What is a denial:** only attempts to get around a rule. `CAMARA_NO_ENCONTRADA` (a camera of another household or of none; a member's own camera that is not in use is not recorded), `SIN_CONSENTIMIENTO` and `SESION_AJENA` (changing or closing another member's or another household's session) from the API; from the hook, every `401` with its reason (`ORIGEN_EXTERNO`, `SECRETO_INVALIDO`, `ACCION_NO_PERMITIDA`, `PROTOCOLO_NO_PERMITIDO`, `RUTA_INVALIDA`, `CLAVE_DE_PUBLICACION_INVALIDA`, `PUBLICACION_SIN_VISTA_PREVIA`, `SIN_TOKEN`, `TOKEN_INVALIDO`, `TOKEN_DE_OTRA_CAMARA`, `SESION_TERMINADA`, `LECTURA_SIN_VISTA_PREVIA`, `CAPTURA_NO_PERMITIDA`). Not denials: a paused or disconnected camera (`CAMARA_EN_PAUSA`, `CAMARA_DESCONECTADA`), validation errors, a member's own ended session, and an RTSP publish's first request without credentials, which every RTSP client sends before MediaMTX answers `401` (as already decided for the hook's log).
- **Answers do not change:** the same status codes and bodies as before; closing someone else's session still answers `204`.
- **`GET /api/accesos-vista-en-vivo/denegados`** (any member, like the access log) lists the household's denials, newest first, with the camera's room and `miembroDelHogar`. The requester is named only when they are a member of this household asking from it; a user of another household is never identified to this one.

## Consequences
- The measurement can count rows of `accesos_denegados` (by `canal`, `motivo`, period) and show each household its own list; a test proves a row exists after the request answered `409`, `404`, `204` or `401`.
- Each denial costs one extra short transaction and connection. Refusals are rare in normal use; the hook already answers within MediaMTX's 10 s timeout and logs answers slower than 1 s, so a slow insert would show there.
- Nothing limits the number of rows: a client hammering the API with refused requests grows the table. The hook only answers the private networks, and API requests need a valid JWT; a retention or rate limit can be added if that changes.
- A rare denial may be lost if its own transaction fails (logged as a warning); the request is still refused.

## References
- Spring Framework. (n.d.). *Transaction propagation*. Spring Framework reference documentation. https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- Hibernate. (n.d.). *Hibernate ORM User Guide*, ch. 23 "Multitenancy". https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html
