-- =======================================================
-- Flyway Migration V5: alinea service_code con catalog
-- catalog_services.code admite 50 caracteres; con 30, un código largo hacía fallar el INSERT (ORA-12899 -> HTTP 500).
-- V1 no se edita nunca: Flyway guarda su firma.
-- =======================================================

ALTER TABLE tb_shipments MODIFY (service_code VARCHAR2(50));
