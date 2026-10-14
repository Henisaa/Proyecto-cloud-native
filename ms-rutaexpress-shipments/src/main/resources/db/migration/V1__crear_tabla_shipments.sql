CREATE TABLE tb_shipments (
    id                 VARCHAR2(36)  NOT NULL,
    tracking_code      VARCHAR2(30)  NOT NULL,
    service_id         NUMBER(19)    NOT NULL,
    service_code       VARCHAR2(30),
    service_type       VARCHAR2(30),
    status             VARCHAR2(20)  NOT NULL,
    zone               VARCHAR2(30),
    vehicle_type       VARCHAR2(30),
    recipient_name     VARCHAR2(120) NOT NULL,
    recipient_email    VARCHAR2(120),
    recipient_phone    VARCHAR2(30),
    origin_address     VARCHAR2(250) NOT NULL,
    destination_address VARCHAR2(250) NOT NULL,
    weight_kg          NUMBER(10, 2) NOT NULL,
    volume_m3          NUMBER(10, 3) NOT NULL,
    packages_count     NUMBER(5)     NOT NULL,
    price              NUMBER(12, 2),
    customer_email     VARCHAR2(120),
    capacity_reserved  NUMBER(1)     NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_shipments PRIMARY KEY (id),
    CONSTRAINT uq_shipments_tracking UNIQUE (tracking_code)
);

CREATE INDEX idx_shipments_status ON tb_shipments (status);
CREATE INDEX idx_shipments_service ON tb_shipments (service_id);
CREATE INDEX idx_shipments_created ON tb_shipments (created_at);
