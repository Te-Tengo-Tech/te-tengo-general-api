-- US-05: consent of the older adult, with the date and time it was granted (CA-05.3, Law No. 29733).
create table consentimientos (
    id                         uuid primary key,
    hogar_id                   uuid         not null references hogares (id),
    otorgado_en                timestamptz  not null,
    otorgado_por               varchar(120) not null,
    registrado_por             uuid         not null,
    aceptado_por_adulto_mayor  boolean      not null,
    vista_en_vivo_aceptada     boolean      not null,
    reemplazado_en             timestamptz,
    revocado_en                timestamptz,
    creado_en                  timestamptz  not null,
    actualizado_en             timestamptz  not null
);

create index idx_consentimientos_hogar on consentimientos (hogar_id, otorgado_en desc);

-- camaras: capture state of each household, kept up to date from the consent events of hogares.
create table estados_de_captura (
    id                     uuid primary key,
    hogar_id               uuid        not null references hogares (id),
    consentimiento_vigente boolean     not null,
    creado_en              timestamptz not null,
    actualizado_en         timestamptz not null
);

create unique index uq_estados_de_captura_hogar on estados_de_captura (hogar_id);
