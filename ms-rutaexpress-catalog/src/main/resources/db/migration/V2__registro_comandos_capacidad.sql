-- =======================================================
-- Flyway Migration V2: registro idempotente de comandos de capacidad (RabbitMQ)
-- Oracle 23 & H2 compatible
-- =======================================================

CREATE TABLE tb_capacity_commands (
    command_id          VARCHAR2(64)  NOT NULL,
    command_type        VARCHAR2(20)  NOT NULL,
    shipment_id         VARCHAR2(36)  NOT NULL,
    result              VARCHAR2(20)  NOT NULL,
    vehicle_type        VARCHAR2(30),
    remaining_capacity  NUMBER(10)    DEFAULT 0 NOT NULL,
    detail              VARCHAR2(250),
    processed_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_capacity_commands PRIMARY KEY (command_id)
);

CREATE INDEX idx_capacity_commands_shipment ON tb_capacity_commands (shipment_id);
