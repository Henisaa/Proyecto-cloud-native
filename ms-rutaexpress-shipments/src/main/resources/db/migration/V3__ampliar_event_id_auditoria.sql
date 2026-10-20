-- =======================================================
-- Flyway Migration V3: amplía event_id de la auditoría
-- El resultado de una compensación puede superar los 64 caracteres.
-- V2 no se edita nunca: Flyway guarda su firma.
-- =======================================================

ALTER TABLE tb_auditoria_envio MODIFY (event_id VARCHAR2(100));
