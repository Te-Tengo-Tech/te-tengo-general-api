-- US-11 to US-21: alerts created from the events of the household agent.
create table alertas (
    id                uuid primary key,
    hogar_id          uuid         not null references hogares (id),
    camara_id         uuid         not null references camaras (id),
    tipo              varchar(30)  not null,
    severidad         varchar(10)  not null,
    estado            varchar(20)  not null,
    confirmada        boolean      not null,
    origen_inestable  boolean      not null,
    habitacion        varchar(40)  not null,
    ocurrida_en       timestamptz  not null,
    notificada_en     timestamptz,
    recuperada_en     timestamptz,
    atendida_por      uuid,
    atendida_en       timestamptz,
    escalada_en       timestamptz,
    creado_en         timestamptz  not null,
    actualizado_en    timestamptz  not null
);

create index idx_alertas_hogar_ocurrida on alertas (hogar_id, ocurrida_en desc);
create index idx_alertas_hogar_camara on alertas (hogar_id, camara_id, tipo, estado);

-- Every event the agent sent, idempotent by (household, eventoId).
create table eventos_de_agente (
    id              uuid primary key,
    hogar_id        uuid        not null references hogares (id),
    evento_id       uuid        not null,
    camara_id       uuid        not null references camaras (id),
    tipo            varchar(30) not null,
    ocurrido_en     timestamptz not null,
    parametros      text,
    alerta_id       uuid references alertas (id),
    creado_en       timestamptz not null,
    actualizado_en  timestamptz not null
);

create unique index uq_eventos_de_agente_hogar_evento on eventos_de_agente (hogar_id, evento_id);
