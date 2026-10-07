-- API contract: Camara carries its pause end (US-22) and whether detection is reliable (US-15).
alter table camaras
    add column pausada_hasta        timestamptz,
    add column deteccion_confiable  boolean not null default true;
