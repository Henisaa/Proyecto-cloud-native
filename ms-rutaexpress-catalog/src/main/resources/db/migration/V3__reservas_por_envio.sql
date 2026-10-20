-- =======================================================
-- Flyway Migration V3: reserva de capacidad por envío (P1-03)
-- Una reserva por envío (shipment_id único): evita la doble reserva y la doble liberación,
-- y permite liberar siempre la línea que realmente se reservó.
-- Oracle 23 & H2 compatible
-- =======================================================

CREATE TABLE tb_capacity_reservations (
    shipment_id         VARCHAR2(36)  NOT NULL,
    capacity_id         NUMBER(19)    NOT NULL,
    packages            NUMBER(10)    NOT NULL,
    vehicle_type        VARCHAR2(30),
    state               VARCHAR2(20)  NOT NULL,
    reserve_command_id  VARCHAR2(64)  NOT NULL,
    release_command_id  VARCHAR2(64),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at         TIMESTAMP WITH TIME ZONE,
    CONSTRAINT pk_capacity_reservations PRIMARY KEY (shipment_id),
    CONSTRAINT fk_reservation_capacity FOREIGN KEY (capacity_id) REFERENCES fleet_capacities (id)
);

CREATE INDEX idx_reservations_capacity ON tb_capacity_reservations (capacity_id);
