-- API contract: since when the camera's detection is unreliable (CA-15.3); null while reliable.
alter table camaras
    add column no_confiable_desde timestamptz;
