# ADR-0033: Backend de pruebas en el homeserver en vez de Cloud SQL + Cloud Run

- **Estado:** Aceptada · 27 oct 2026
- **Features:** E0.5, E0.6

## Contexto
Aplicar el Terraform de `parcheu-dev` (Cloud SQL, Cloud Run, Cloud Tasks, Scheduler) cuesta dinero fijo desde el primer día y exige la facturación y los pasos humanos de E0.5, mientras el equipo solo necesita una API viva para probar la app. El usuario tiene un homeserver (Debian, Docker Compose, Tailscale, túnel de Cloudflare) con poca memoria libre (~2,9 GB).

## Decisión
- Un `compose.yaml` en `deploy/homelab/` con Postgres 16 (sin puertos publicados), un servicio one-shot `migrate` (ADR-0011) y la API de la misma imagen Jib, publicada en `0.0.0.0:8090`.
- `desplegar.sh` corre desde el portátil del usuario (build, scp, `docker load`, migración, salud, cron de respaldo); nunca desde CI y nunca sobrescribe el `.env` remoto.
- Arranca con `ENV=local` (Fake de auth, IA, FCM y colas; sin credenciales de Google). Con una llave de cuenta de servicio con cuatro roles mínimos puede pasar a `ENV=dev` (Firebase, Vertex, Firestore y FCM reales). La llave es una excepción temporal y manual a la política de ADR-0014 (sin llaves): vive solo en `~/parcheu/secrets/sa.json`, fuera del repo.
- No se aplica Terraform por ahora. `deploy-dev.yml` se conserva pero queda condicionado a la variable de repositorio `GCP_DEV_ENABLED` para no fallar en cada push a `dev`.
- Acceso principal por HTTPS: Nginx Proxy Manager del servidor (certificado comodín `*.homeserverlab.site`) con `api-parcheu.homeserverlab.site` → `http://192.168.0.10:8090`; AdGuard resuelve el dominio en la LAN.
- Android: como respaldo, el `devDebug` (y solo él) permite HTTP hacia `192.168.0.10`, `100.104.43.91`, `homelab` y `10.0.2.2`; `release` y `prod` mantienen la red segura por defecto.

## Alternativas consideradas
- **Aplicar Terraform de dev ya:** costo fijo y pasos humanos pendientes; se mantiene como camino de regreso.
- **Publicar solo con `ENV=local` por Cloudflare:** abre la suplantación de usuarios a internet; se descarta (el túnel es solo para `dev`).
- **Postgres compartido del host:** el 5432 está ocupado y mezcla datos con otros servicios.

## Consecuencias
- Pruebas baratas y con datos propios, pero sin SLA: un solo servidor, respaldo diario local de 7 días.
- En modo `local` la app real no inicia sesión; para eso hace falta la llave de Google (`deploy/homelab/README.md`).
- Sin colas de Cloud Tasks reales, las tareas asíncronas fallan en modo `dev` hasta que se creen (opcional).
- Cloud Run y Cloud SQL siguen siendo el destino de prod; este despliegue no sustituye a prod.
