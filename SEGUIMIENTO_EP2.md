# Seguimiento de cumplimiento EP2

Especificación: PLAN_CORRECCIONES_EP2.md versión 2 y los dos documentos de evaluación. Rama de continuación: ep2-continuacion-completa. Este archivo distingue implementación de aceptación: ninguna tarea se considera terminada según §0.2 sin CI, imagen, despliegue y evidencia.

| Fase del plan | Implementación en la rama | Aceptación pendiente |
|---|---|---|
| 0 Preparación | Se conservaron tenant, imágenes GHCR, Oracle en Compose y frontend en contenedor; sessionStorage | Confirmar dominios, EC2 y decisiones D1–D8 con el entorno real |
| 1 Base en Docker | V2 original + V3, Dockerfiles con pruebas, CI y publicación por SHA | Revisión de pareja, PR a main y etiqueta ep2-base; validar todos los jobs del commit final |
| 2 Consistencia | Reservas únicas con INSERT, commandId, @Version, liberación idempotente y reconciliación | Compensación con broker detenido y concurrencia contra despliegue real |
| 3 Seguridad, errores y mensajería | Roles/oid, formatos ProblemDetail compatibles, DTOs, panel tolerante, DLQ acumulada, notify processing | Recorrido real de JWT/roles, revisión final por endpoint y logs |
| 4 Frontend | Pantallas FE-01–FE-23 y FE-26; pruebas FE-25; advertencias de CSS corregidas al desactivar extracción crítica incompatible con nesting | Verificar CI final, accesibilidad/foco y recorrido de cinco usuarios con datos reales |
| 5 Nube | Compose mq/apps/kafka/edge, secretos, scripts y ejemplos | EC2, DNS/TLS, Entra ID, Gateway JWT/CORS, logs y 14 evidencias |
| 6 Repositorios separados | Monorepo mantenido como indica §14 hasta terminar main | División con historial, CI por repo, protección y enlaces. No archivar ni dividir antes de completar main |
| 7 Aceptación y ensayo | Colección Newman y guía de evidencias en infra/acceptance | Ejecutar contra Gateway real; pruebas de broker y dos ensayos por integrante |

## Pendientes de ingeniería que requieren cierre explícito

- P2-04: se redujo el timeout de confirmación, pero falta esperar juntas las confirmaciones de una transición.
- P2-05: decidir prefetch de comandos 1–5 y concurrencia según prueba de carga; actualmente configurable, 10 por defecto.
- P2-12: crecimiento coordinado de particiones/DLT y política de compactación de audit.timeline.
- P3-07: definir y probar renovación de capacidad por fecha. No reiniciar contadores sin considerar reservas activas.
- FE-11: se expone acumulado y tasa reciente de DLQ; no existe una ventana histórica persistente de la última hora.
- P3-12: los README principales, de frontend, infraestructura, BFF, shipments, catalog y rabbit-admin fueron actualizados; completar revisión de los componentes EP1/notify al preparar repos separados.
- Outbox figura como mejora posterior en D7, P1-05 y V5; esta rama usa confirms y reconciliación y no implementa una transacción distribuida.

## Verificación disponible

Los reportes y resultados exactos se consultan en Actions por commit. Los primeros ciclos detectaron errores reales de compilación Kotlin, doble reserva concurrente, codificación de fechas y fixtures fuera del largo de columna; se corrigieron y se volvió a ejecutar CI. Las pruebas de frontend corren con Chromium dentro de Docker. También se comprueba la imagen nginx servida, config.json, fallback SPA, cabeceras y usuario no root.

No se ejecutaron compilaciones, Gradle, npm ni instalación de herramientas en el PC. La colección Newman está preparada pero no ejecutada contra el Gateway de la evaluación. No hay evidencia de despliegue EC2 ni recorrido autenticado real en esta sesión.