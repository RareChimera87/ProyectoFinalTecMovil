# ADR-0033: Backend de pruebas en el homeserver en vez de Cloud SQL + Cloud Run

- **Estado:** Aceptada · 27 oct 2026
- **Features:** E0.5, E0.6

## Contexto
Aplicar el Terraform de `parcheu-dev` (Cloud SQL, Cloud Run, Cloud Tasks, Scheduler) cuesta dinero fijo desde el primer día y exige la facturación y los pasos humanos de E0.5, mientras el equipo solo necesita una API viva para probar la app. El usuario tiene un homeserver (Debian, Docker Compose, Tailscale, túnel de Cloudflare) con poca memoria libre (~2,9 GB).

## Decisión
- Un `compose.yaml` en `deploy/homelab/` con Postgres 16 (sin puertos publicados), un servicio one-shot `migrate` (ADR-0011) y la API de la misma imagen Jib, publicada en `127.0.0.1:8090` y `192.168.0.10:8090` (no en `0.0.0.0`).
- `desplegar.sh` corre desde el portátil del usuario (build, scp, `docker load`, migración, salud, cron de respaldo); nunca desde CI y nunca sobrescribe el `.env` remoto.
- Arranca con `ENV=local` (Fake de auth, IA y FCM; sin credenciales de Google). Con una llave de cuenta de servicio con cuatro roles mínimos puede pasar a `ENV=dev` (Firebase, Vertex, Firestore y FCM reales). La llave es una excepción temporal y manual a la política de ADR-0014 (sin llaves): vive solo en `~/parcheu/secrets/sa.json`, fuera del repo.
- Las colas y los jobs corren en el proceso de la API (`TASKS_MODE=inline`): `InProcessTaskQueue` (reintentos con backoff, concurrencia por cola, deduplicación 1 h) y `InProcessScheduler` (`match-sweep`, `purge-sweep`, recuperación de simulaciones). Se rechaza con `ENV=prod` y en Cloud Run, donde siguen Cloud Tasks y Cloud Scheduler. Una tarea en vuelo se pierde si el proceso se reinicia; los barridos la recuperan.
- Cada despliegue etiqueta la imagen con el SHA y `desplegar.sh` revierte solo a la anterior si la salud falla.
- No se aplica Terraform por ahora. `deploy-dev.yml` se conserva pero queda condicionado a la variable de repositorio `GCP_DEV_ENABLED` para no fallar en cada push a `dev`.
- Acceso principal por HTTPS: Nginx Proxy Manager del servidor (certificado comodín `*.homeserverlab.site`) con `api-parcheu.homeserverlab.site` → `http://192.168.0.10:8090`; AdGuard resuelve el dominio en la LAN. Mientras `PARCHEU_ENV=local` (auth Fake) el Proxy Host lleva una Access List que permite solo `192.168.0.0/24` y `100.64.0.0/10`, o se crea solo tras pasar a `dev`; `desplegar.sh` avisa si el HTTPS responde con `local`.
- Android: como respaldo, el `devDebug` (y solo él) permite HTTP hacia `192.168.0.10` y `10.0.2.2`; `release` y `prod` mantienen la red segura por defecto.

## Alternativas consideradas
- **Aplicar Terraform de dev ya:** costo fijo y pasos humanos pendientes; se mantiene como camino de regreso.
- **Cloud Tasks real desde el homeserver:** exige exponer la API a Cloud Tasks y crear colas; la cola en proceso evita ambas cosas.
- **Publicar solo con `ENV=local` por Cloudflare:** abre la suplantación de usuarios a internet; se descarta (el túnel es solo para `dev`).
- **Postgres compartido del host:** el 5432 está ocupado y mezcla datos con otros servicios.

## Consecuencias
- Pruebas baratas y con datos propios, pero sin SLA: un solo servidor, respaldo diario local de 7 días.
- En modo `local` la app real no inicia sesión; para eso hace falta la llave de Google (`deploy/homelab/README.md`).
- Las tareas son en proceso: sin garantía de entrega ante un reinicio (los barridos recuperan simulaciones y purgas) y sin las notificaciones RTDN de Play (renovaciones y cancelaciones no se sincronizan).
- Cloud Run y Cloud SQL siguen siendo el destino de prod; este despliegue no sustituye a prod.
