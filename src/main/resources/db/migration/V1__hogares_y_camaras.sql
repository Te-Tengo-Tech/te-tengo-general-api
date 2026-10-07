-- Hogar: el tenant. Tabla global (sin hogar_id).
create table hogares (
    id              uuid primary key,
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

-- Toda tabla de datos del hogar lleva hogar_id y un índice que empieza por él.
create table camaras (
    id                 uuid primary key,
    hogar_id           uuid        not null references hogares (id),
    nombre_habitacion  varchar(40) not null,
    estado_conexion    varchar(20) not null,
    ultima_senal       timestamptz,
    creado_en          timestamptz not null,
    actualizado_en     timestamptz not null
);

create index idx_camaras_hogar on camaras (hogar_id);
