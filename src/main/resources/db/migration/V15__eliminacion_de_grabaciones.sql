-- US-09: deletion of every recording of the household after the consent is revoked (CA-09.1, CA-09.3).
create table eliminaciones_de_grabaciones (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    solicitada_en   timestamptz not null,
    completada_en   timestamptz,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create index idx_eliminaciones_pendientes on eliminaciones_de_grabaciones (completada_en, hogar_id);
