-- =======================================================
-- Flyway Migration V2: estado de reserva de capacidad y auditoría de eventos RabbitMQ
-- =======================================================

ALTER TABLE tb_shipments ADD (capacity_status VARCHAR2(20) DEFAULT 'NO_APLICA' NOT NULL);

-- Los envíos existentes con capacidad reservada quedan marcados como RESERVADA.
UPDATE tb_shipments SET capacity_status = 'RESERVADA' WHERE capacity_reserved = 1;

CREATE TABLE tb_auditoria_envio (
    event_id     VARCHAR2(64)  NOT NULL,
    shipment_id  VARCHAR2(36)  NOT NULL,
    event_type   VARCHAR2(80)  NOT NULL,
    routing_key  VARCHAR2(120) NOT NULL,
    detail       VARCHAR2(250),
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_auditoria_envio PRIMARY KEY (event_id)
);

CREATE INDEX idx_auditoria_envio_shipment ON tb_auditoria_envio (shipment_id);
