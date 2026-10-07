-- US-10: who is the primary and secondary contact, and how long to wait before escalating (3, 5 or 10 min).
-- Null means "not chosen": the owner is the primary, there is no secondary and the wait is 5 min (CA-10.3).
alter table hogares
    add column aviso_principal_id   uuid,
    add column aviso_secundario_id  uuid,
    add column aviso_espera_minutos integer;
