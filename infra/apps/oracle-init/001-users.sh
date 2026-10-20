#!/bin/bash
# Crea los cuatro usuarios de aplicación en el PDB FREEPDB1. Cada servicio recibe solo su usuario y su contraseña;
# las contraseñas llegan por variables de entorno (una por esquema) y son distintas de la del administrador de Oracle.
# La imagen gvenzl/oracle-free ejecuta este script únicamente cuando el volumen de datos está vacío.
# Las contraseñas no pueden contener comillas dobles.
set -euo pipefail

: "${SHIPMENTS_DB_PASSWORD:?falta SHIPMENTS_DB_PASSWORD}"
: "${CATALOG_DB_PASSWORD:?falta CATALOG_DB_PASSWORD}"
: "${AUDIT_DB_PASSWORD:?falta AUDIT_DB_PASSWORD}"
: "${REPORT_DB_PASSWORD:?falta REPORT_DB_PASSWORD}"

sqlplus -s / as sysdba <<SQL
WHENEVER SQLERROR EXIT SQL.SQLCODE
ALTER SESSION SET CONTAINER = FREEPDB1;

CREATE USER ruta_shipments IDENTIFIED BY "${SHIPMENTS_DB_PASSWORD}";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ruta_shipments;
ALTER USER ruta_shipments QUOTA UNLIMITED ON USERS;

CREATE USER ruta_catalog IDENTIFIED BY "${CATALOG_DB_PASSWORD}";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ruta_catalog;
ALTER USER ruta_catalog QUOTA UNLIMITED ON USERS;

CREATE USER ruta_audit IDENTIFIED BY "${AUDIT_DB_PASSWORD}";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ruta_audit;
ALTER USER ruta_audit QUOTA UNLIMITED ON USERS;

CREATE USER ruta_report IDENTIFIED BY "${REPORT_DB_PASSWORD}";
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE INDEX TO ruta_report;
ALTER USER ruta_report QUOTA UNLIMITED ON USERS;
EXIT;
SQL
