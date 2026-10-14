CREATE TABLE AUDITORIA_EVENTOS (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_entidad VARCHAR2(100) NOT NULL,
    tipo_entidad VARCHAR2(50) NOT NULL,
    tipo_evento VARCHAR2(50) NOT NULL,
    origen VARCHAR2(50),
    datos_payload CLOB NOT NULL,
    usuario_responsable VARCHAR2(100),
    fecha_evento TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_entidad ON AUDITORIA_EVENTOS(id_entidad);
CREATE INDEX idx_audit_tipo ON AUDITORIA_EVENTOS(tipo_entidad);
CREATE INDEX idx_audit_fecha ON AUDITORIA_EVENTOS(fecha_evento);
