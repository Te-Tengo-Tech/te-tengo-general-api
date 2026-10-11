-- EP08, movement data (US-35 to US-39; ADR 0010) and US-40's report. PostgreSQL keeps only the
-- consent, the settings, an index of the files and counts; the landmarks themselves are Parquet files
-- in object storage (Cloudflare R2), uploaded by the PC. Additive only.

-- US-35: the separate, optional consent to store movements (CA-35.2: who gave it, who registered it,
-- when, and the text version).
create table consentimientos_de_movimientos (
    id                 uuid primary key,
    hogar_id           uuid         not null references hogares (id),
    otorgado_en        timestamptz  not null,
    otorgado_por       varchar(120) not null,
    representante      boolean      not null,
    registrado_por     uuid         not null,
    version_texto      varchar(20)  not null,
    revocado_en        timestamptz,
    revocado_por       uuid,
    -- DESDE_LA_APP or CONSENTIMIENTO_DE_MONITOREO_REVOCADO
    motivo_revocacion  varchar(40),
    reemplazado_en     timestamptz,
    creado_en          timestamptz  not null,
    actualizado_en     timestamptz  not null
);

create index idx_consentimientos_de_movimientos_hogar on consentimientos_de_movimientos (hogar_id, otorgado_en desc);

-- US-37: the cloud retention chosen by the owner (null: not stored in the cloud, the default).
create table configuraciones_de_movimientos (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    retencion_dias  integer check (retencion_dias in (7, 30, 90, 365)),
    cambiada_en     timestamptz,
    cambiada_por    uuid,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create unique index uq_configuraciones_de_movimientos_hogar on configuraciones_de_movimientos (hogar_id);

-- US-37 / CA-37.2: one row per movement file (5 minutes of one camera).
-- clave: movimientos/<hogar>/<camara>/YYYY/MM/DD/HH/mm.parquet
create table archivos_de_movimiento (
    id              uuid primary key,
    hogar_id        uuid         not null references hogares (id),
    camara_id       uuid         not null references camaras (id),
    desde           timestamptz  not null,
    hasta           timestamptz  not null,
    tamano_bytes    bigint       not null,
    sha256          varchar(64)  not null,
    fotogramas      integer      not null,
    clave           varchar(300) not null,
    -- PENDIENTE (upload URL issued) or DISPONIBLE (confirmed; part of the index)
    estado          varchar(20)  not null,
    subido_en       timestamptz,
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null
);

create unique index uq_archivos_de_movimiento_clave on archivos_de_movimiento (clave);
create unique index uq_archivos_de_movimiento_camara_desde on archivos_de_movimiento (camara_id, desde);
create index idx_archivos_de_movimiento_hogar on archivos_de_movimiento (hogar_id, camara_id, desde);
create index idx_archivos_de_movimiento_hasta on archivos_de_movimiento (hogar_id, hasta);

-- CA-37.2 «un resumen»: counts per camera and day of the household; no landmarks.
create table resumenes_de_movimiento (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    camara_id       uuid        not null references camaras (id),
    dia             date        not null,
    archivos        integer     not null,
    fotogramas      bigint      not null,
    segundos        bigint      not null,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create unique index uq_resumenes_de_movimiento on resumenes_de_movimiento (camara_id, dia);
create index idx_resumenes_de_movimiento_hogar on resumenes_de_movimiento (hogar_id, dia);

-- CA-35.4: deletion of the cloud movements after a revocation of their consent.
create table eliminaciones_de_movimientos (
    id                  uuid primary key,
    hogar_id            uuid        not null references hogares (id),
    programada_en       timestamptz not null,
    terminada_en        timestamptz,
    archivos_eliminados bigint,
    creado_en           timestamptz not null,
    actualizado_en      timestamptz not null
);

create index idx_eliminaciones_de_movimientos_pendientes on eliminaciones_de_movimientos (terminada_en, hogar_id);

-- US-39 / CA-39.3: replays, which are also entries of the access log.
create table repeticiones_de_movimiento (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    camara_id       uuid        not null references camaras (id),
    usuario_id      uuid        not null,
    momento_desde   timestamptz not null,
    momento_hasta   timestamptz not null,
    inicio          timestamptz not null,
    expira_en       timestamptz not null,
    fin             timestamptz,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create index idx_repeticiones_de_movimiento_hogar on repeticiones_de_movimiento (hogar_id, inicio desc);

-- US-36: the clip's body landmarks (clips/<alerta>.landmarks.parquet).
alter table alertas
    add column puntos_clave varchar(300);

-- US-37: movement files deleted with the recordings after a monitoring consent revocation.
alter table eliminaciones_de_grabaciones
    add column movimientos_eliminados bigint;

-- US-40 / CA-40.4: «Hubo una caída y no avisó», a possible false negative for the evaluation.
create table reportes_de_caida_sin_alerta (
    id                           uuid primary key,
    hogar_id                     uuid         not null references hogares (id),
    usuario_id                   uuid         not null,
    camara_id                    uuid references camaras (id),
    habitacion                   varchar(40),
    ocurrida_aproximadamente_en  timestamptz  not null,
    nota                         varchar(300),
    movimientos_disponibles      boolean      not null,
    creado_en                    timestamptz  not null,
    actualizado_en               timestamptz  not null
);

create index idx_reportes_de_caida_hogar on reportes_de_caida_sin_alerta (hogar_id, creado_en desc);
