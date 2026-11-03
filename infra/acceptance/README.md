# Aceptación EP2

La colección comprueba la parte HTTP de las pruebas 1–6 y 11–13 de la sección 13.2. No reemplaza las pruebas de broker 7–10 y 14 ni el recorrido de los cinco usuarios.

Ejecutar en EC2 o Codespaces con Docker, sin instalar Newman en el PC. Proveer un archivo de entorno local `ep2.postman_environment.json` con `base_url`, `admin_token`, `dispatcher_token`, `client_token`, `service_id` (servicio activo con cupo) y `run_id` (sufijo único por ejecución). Los tokens se obtienen mediante Entra ID; el archivo real no se versiona.

```sh
docker run --rm -v "$PWD/infra/acceptance:/etc/newman" postman/newman:alpine run ep2.postman_collection.json -e ep2.postman_environment.json --delay-request 1000 --reporters cli,junit --reporter-junit-export resultado.xml
```

La colección crea un envío y lo acepta; verificar después RESERVADA en la UI, consumo de catalog, correo en Mailpit y auditoría. Los recursos de administración usan `ex.ep2.<run_id>` y `q.ep2.<run_id>` y se eliminan al terminar. Si la ejecución se interrumpe, retirarlos desde el administrador. No ejecutarla sobre datos ajenos a la evaluación.

Para completar la evidencia del plan:

| Prueba | Evidencia adicional |
|---|---|
| 1–4 | HTTP del Gateway, configuración del autorizador y preflight CORS |
| 5–6 | 409 sin publicación; PENDIENTE → RESERVADA, capacidad descontada, Mailpit y línea de tiempo |
| 7 | `rabbitmqctl cluster_status` con ambos nodos |
| 8 | Logs de los tres consumidores con ACK y colas sin pendientes |
| 9 | Plantilla inválida de FE-16: NACK, DLQ y log con x-death/rejected |
| 10 | Mailpit detenido: reintentos acotados y DLQ; volver a iniciarlo |
| 11–13 | HTTP y capturas de los recursos creados/eliminados y validaciones |
| 14 | FE-16 repite seis mensajes: cinco confirmados y el sexto 409; TTL a DLQ, luego drop-head/maxlen y Policies |

Guardar capturas y logs sanitizados en el expediente de entrega. No adjuntar archivos de entorno, tokens ni cabeceras Authorization al repositorio.