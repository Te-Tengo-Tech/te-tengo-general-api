-- US-28: the PC's installation id, a UUID Te Tengo Captura generates once and keeps in the operating
-- system's credential store. It travels with the device authorization request, so the app can say
-- «Esta PC ya está vinculada» instead of linking the same PC twice. Optional: older agents send none.

alter table autorizaciones_de_dispositivo
    add column id_instalacion uuid;

alter table pcs
    add column id_instalacion uuid;

create index idx_pcs_id_instalacion on pcs (id_instalacion) where estado = 'VINCULADA';
