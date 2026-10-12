-- US-43 (EP09), CA-43.7: whether «Actualizar automáticamente» is on in Te Tengo Captura, as the PC
-- reports it with its heartbeat. Null until the PC reports it (older agents never do).
alter table pcs add column actualizacion_automatica boolean;
