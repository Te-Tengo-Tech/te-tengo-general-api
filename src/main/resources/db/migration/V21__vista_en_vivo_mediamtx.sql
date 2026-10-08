-- Live view through MediaMTX (ADR 0007). A session is now used for its whole length: token_hash is the
-- SHA-256 of the viewer token MediaMTX checks on every read authorization, ultima_actividad is the last
-- time the viewer was seen reading (authorization or HLS traffic), and expira_en is the maximum end.
alter table accesos_vista_en_vivo
    add column ultima_actividad timestamptz;

create index idx_accesos_vista_en_vivo_abiertas on accesos_vista_en_vivo (hogar_id, camara_id) where fin is null;

-- The current transmission of a camera, while at least one session is open: the SHA-256 of the publish
-- token handed to the household agent and the mode the agent draws (VIDEO, VIDEO_CON_POSTURA,
-- SOLO_POSTURA). One per camera.
create table transmisiones_en_vivo (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    camara_id       uuid        not null references camaras (id),
    clave_hash      varchar(64) not null,
    modo            varchar(20) not null,
    iniciada_en     timestamptz not null,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null,
    constraint uq_transmisiones_en_vivo_camara unique (camara_id)
);
