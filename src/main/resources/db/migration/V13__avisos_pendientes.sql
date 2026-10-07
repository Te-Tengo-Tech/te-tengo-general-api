-- CA-16.4: push notices the push service did not accept, retried by a job.
-- destinatarios: comma-separated user ids, or null for every member of the household.
create table avisos_pendientes (
    id               uuid primary key,
    hogar_id         uuid        not null references hogares (id),
    tipo             varchar(40) not null,
    alerta_id        uuid references alertas (id),
    camara_id        uuid,
    habitacion       varchar(40),
    ocurrida_en      timestamptz not null,
    destinatarios    text,
    excluido         uuid,
    intentos         integer     not null,
    proximo_intento  timestamptz not null,
    creado_en        timestamptz not null,
    actualizado_en   timestamptz not null
);

create index idx_avisos_pendientes_proximo on avisos_pendientes (proximo_intento);
create index idx_avisos_pendientes_hogar on avisos_pendientes (hogar_id);
