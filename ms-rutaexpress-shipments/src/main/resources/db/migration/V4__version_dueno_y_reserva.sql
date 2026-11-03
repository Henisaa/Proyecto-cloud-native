-- =======================================================
-- Flyway Migration V4: bloqueo optimista, dueño del envío y última reserva
--  - version: dos escrituras simultáneas sobre el mismo envío ya no se pisan (P1-06).
--  - owner_oid: identificador estable (oid de Entra ID) del cliente que creó el envío (P1-08).
--  - last_reserve_command_id: descarta resultados de reservas anteriores (P1-02).
-- =======================================================

ALTER TABLE tb_shipments ADD (version NUMBER(19) DEFAULT 0 NOT NULL);
ALTER TABLE tb_shipments ADD (owner_oid VARCHAR2(64));
ALTER TABLE tb_shipments ADD (last_reserve_command_id VARCHAR2(36));

CREATE INDEX idx_shipments_owner ON tb_shipments (owner_oid);
