-- =======================================================
-- Flyway Migration V4: bloqueo optimista en las líneas de capacidad (P1-06)
-- Una edición administrativa concurrente con una reserva ya no pisa el contador.
-- =======================================================

ALTER TABLE fleet_capacities ADD version NUMBER(19) DEFAULT 0 NOT NULL;
