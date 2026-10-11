-- ADR 0011 (US-37, US-39): the light replay file (TTMOV v1, gzip) next to each movement file, under the
-- Parquet file's key with .ttmov. Its size and SHA-256 as reported by the PC when it asks for the upload
-- URL; ligero_subido_en is set once the PC confirmed it and the storage has it (the size is then the
-- storage's). Null: no light file, or not confirmed yet.
alter table archivos_de_movimiento
    add column ligero_tamano_bytes bigint,
    add column ligero_sha256       varchar(64),
    add column ligero_subido_en    timestamptz;
