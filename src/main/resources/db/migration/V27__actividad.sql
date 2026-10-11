-- US-33 (EP07): the activity log. Append-only (CA-33.1: «nadie puede editar ni borrar el registro»):
-- a trigger refuses updates and deletes, and the API has no endpoint for them.
create table actividad (
    id              uuid primary key,
    hogar_id        uuid         not null references hogares (id),
    -- unique per household: an event delivered twice, or a PC resending its queue, is stored once
    clave           varchar(80)  not null,
    ocurrida_en     timestamptz  not null,
    tipo            varchar(40)  not null,
    -- FAMILIAR, PC or SISTEMA
    origen          varchar(10)  not null,
    usuario_id      uuid,
    pc_id           uuid,
    -- the PC's name when the change happened
    pc_nombre       varchar(60),
    camara_id       uuid,
    objeto          varchar(120),
    valor_anterior  varchar(200),
    valor_nuevo     varchar(200),
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null
);

create unique index uq_actividad_clave on actividad (hogar_id, clave);
create index idx_actividad_hogar on actividad (hogar_id, ocurrida_en desc);
create index idx_actividad_pc on actividad (hogar_id, pc_id, ocurrida_en desc);

create function actividad_solo_agregar() returns trigger
    language plpgsql as
$$
begin
    raise exception 'El registro de actividad no se puede modificar ni borrar';
end;
$$;

create trigger actividad_solo_agregar
    before update or delete on actividad
    for each row execute function actividad_solo_agregar();
