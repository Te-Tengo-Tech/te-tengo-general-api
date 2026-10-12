-- EP06 (US-28, US-30, US-31; ADR 0009): the household's PCs, each with its own cameras, linked by the
-- owner from the app with the OAuth 2.0 device authorization grant (RFC 8628).
-- Additive: no column is dropped or renamed; installation credentials keep working.

create table pcs (
    id                     uuid primary key,
    hogar_id               uuid        not null references hogares (id),
    nombre                 varchar(60) not null,
    sistema_operativo      varchar(60),
    version_agente         varchar(40),
    -- VINCULACION (linked from the app) or INSTALACION (installation credential of the project team)
    origen                 varchar(20) not null,
    -- VINCULADA or DESVINCULADA; rows are never deleted (the activity log names them)
    estado                 varchar(20) not null,
    vinculada_en           timestamptz not null,
    vinculada_por          uuid,
    ultima_senal           timestamptz,
    ultimo_uso_en          timestamptz,
    desvinculada_en        timestamptz,
    desvinculada_por       uuid,
    motivo_desvinculacion  varchar(30),
    creado_en              timestamptz not null,
    actualizado_en         timestamptz not null
);

create index idx_pcs_hogar on pcs (hogar_id, estado);
create index idx_pcs_ultimo_uso on pcs (ultimo_uso_en) where estado = 'VINCULADA';

-- Every camera belongs to one PC (null only for rows that had no installation). A retired camera
-- (its PC was unlinked) keeps its row, so its alerts and accesses keep pointing at it.
alter table camaras
    add column pc_id       uuid references pcs (id),
    add column retirada_en timestamptz;

create index idx_camaras_pc on camaras (pc_id);

alter table instalaciones
    add column pc_id uuid references pcs (id);

-- Existing data: each installation with a camera runs on its own PC, of origin INSTALACION, linked
-- when the camera was first registered. Cameras without an installation (none in production) get a
-- PC of their own too, so every camera in use has one.
create temporary table pcs_migradas on commit drop as
select distinct on (c.id)
       c.id                 as camara_id,
       i.id                 as instalacion_id,
       c.hogar_id           as hogar_id,
       c.creado_en          as vinculada_en,
       c.ultima_senal       as ultima_senal,
       gen_random_uuid()    as pc_id
from camaras c
left join instalaciones i on i.camara_id = c.id
order by c.id, i.creado_en;

insert into pcs (id, hogar_id, nombre, origen, estado, vinculada_en, ultima_senal, creado_en, actualizado_en)
select pc_id, hogar_id, 'PC de la casa', 'INSTALACION', 'VINCULADA', vinculada_en, ultima_senal, now(), now()
from pcs_migradas;

update camaras c set pc_id = m.pc_id from pcs_migradas m where c.id = m.camara_id;

update instalaciones i set pc_id = m.pc_id from pcs_migradas m where i.id = m.instalacion_id;

-- RFC 8628 device authorization requests. Global: the PC has no household until the owner approves.
-- Both codes are stored as SHA-256 hashes only.
create table autorizaciones_de_dispositivo (
    id                       uuid primary key,
    codigo_dispositivo_hash  varchar(64) not null,
    codigo_usuario_hash      varchar(64) not null,
    cliente                  varchar(60) not null,
    nombre_pc                varchar(60) not null,
    sistema_operativo        varchar(60),
    version_agente           varchar(40),
    -- PENDIENTE, APROBADA, RECHAZADA or CANJEADA
    estado                   varchar(20) not null,
    expira_en                timestamptz not null,
    intervalo_segundos       integer     not null,
    ultima_consulta_en       timestamptz,
    hogar_id                 uuid references hogares (id),
    pc_id                    uuid references pcs (id),
    resuelta_por             uuid,
    resuelta_en              timestamptz,
    canjeada_en              timestamptz,
    creado_en                timestamptz not null,
    actualizado_en           timestamptz not null
);

create unique index uq_autorizaciones_codigo_dispositivo on autorizaciones_de_dispositivo (codigo_dispositivo_hash);
create index idx_autorizaciones_codigo_usuario on autorizaciones_de_dispositivo (codigo_usuario_hash, creado_en desc);
create index idx_autorizaciones_expira on autorizaciones_de_dispositivo (expira_en);

-- Rotating refresh tokens of the PCs (RFC 9700 §2.2.2), stored hashed. Global: the token request
-- carries no JWT, so the token tells the household.
create table tokens_de_renovacion_de_pc (
    id              uuid primary key,
    pc_id           uuid        not null references pcs (id),
    hogar_id        uuid        not null references hogares (id),
    token_hash      varchar(64) not null,
    cliente         varchar(60) not null,
    expira_en       timestamptz not null,
    usado_en        timestamptz,
    revocado_en     timestamptz,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create unique index uq_tokens_de_renovacion_de_pc on tokens_de_renovacion_de_pc (token_hash);
create index idx_tokens_de_renovacion_pc on tokens_de_renovacion_de_pc (pc_id);

-- CA-28.6: wrong link codes in a row per account (5 lock code entry for 15 minutes).
create table intentos_de_vinculacion (
    id               uuid primary key,
    usuario_id       uuid        not null,
    fallidos         integer     not null,
    bloqueada_hasta  timestamptz,
    creado_en        timestamptz not null,
    actualizado_en   timestamptz not null
);

create unique index uq_intentos_de_vinculacion_usuario on intentos_de_vinculacion (usuario_id);
