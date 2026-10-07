-- Household agent installations (AGENT_CONTRACT.md). The project team creates one per installed
-- webcam with scripts/create-installation.sh; only the SHA-256 of the credential is stored. Global
-- table: registration happens before the agent has a token, so the credential identifies the household.
create table instalaciones (
    id               uuid primary key,
    hogar_id         uuid        not null references hogares (id),
    credencial_hash  varchar(64) not null,
    camara_id        uuid references camaras (id),
    creado_en        timestamptz not null,
    actualizado_en   timestamptz not null
);

create unique index uq_instalaciones_credencial on instalaciones (credencial_hash);
create index idx_instalaciones_hogar on instalaciones (hogar_id);
