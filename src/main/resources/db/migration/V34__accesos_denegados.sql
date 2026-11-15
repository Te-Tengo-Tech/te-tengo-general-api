-- ADR 0015: every denied attempt to reach a camera's live view, written in its own transaction so the
-- denial survives the rollback of the request that was refused. hogar_id is the household of the camera
-- (not the requester's), null when the camera id exists nowhere: such rows count for the measurement but
-- no household lists them. camara_id has no foreign key, so unknown ids are kept as they were asked for.
-- Never stores tokens, publish keys or the hook's secret.
create table accesos_denegados (
    id                uuid primary key,
    hogar_id          uuid references hogares (id),
    ocurrido_en       timestamptz not null,
    canal             varchar(10) not null,
    motivo            varchar(40) not null,
    accion            varchar(20),
    protocolo         varchar(20),
    camara_id         uuid,
    usuario_id        uuid,
    hogar_del_usuario uuid,
    creado_en         timestamptz not null,
    actualizado_en    timestamptz not null
);

create index idx_accesos_denegados_hogar on accesos_denegados (hogar_id, ocurrido_en desc);
