# ParcheU en el homeserver

Despliegue del backend Ktor en el homeserver del equipo (Docker Compose), en vez de Cloud SQL + Cloud Run, para pruebas. Decisión en [ADR-0033](../../docs/adr/0033-backend-en-homeserver.md).

| Pieza | Qué es |
|---|---|
| `parcheu-db` | Postgres 16 con volumen `parcheu-pgdata`; **no publica puertos** |
| `parcheu-migrate` | Flyway (modo `migrate`) una sola vez antes de la API |
| `parcheu-api` | Ktor en `0.0.0.0:8090` del host (LAN y Tailscale; el router no lo expone) |

Memoria: API con `-Xmx384m` y tope de 640 MB, Postgres 384 MB, migración 512 MB solo mientras corre.
No toca los demás contenedores del servidor (seafile, n8n, nginx-proxy-manager, etc.).

## Desplegar (desde el portátil)

Requisitos: SSH sin contraseña a `user@homelab`, JDK para Gradle y red para que Jib baje `eclipse-temurin:21-jre` (la imagen es amd64).

```bash
deploy/homelab/desplegar.sh                 # destino user@homelab
deploy/homelab/desplegar.sh --host usuario@100.104.43.91
deploy/homelab/desplegar.sh --sin-build     # reutiliza backend/build/jib-image.tar
```

El script: comprueba que el 8090 esté libre (o sea de ParcheU) y aborta si no, construye la imagen, copia imagen y compose a `~/parcheu/`, crea `~/parcheu/.env` con contraseñas aleatorias la primera vez (`chmod 600`, nunca se sobrescribe), carga la imagen, migra, levanta la API, verifica `http://homelab:8090/v1/health` (y `https://api-parcheu.homeserverlab.site/v1/health` si ya resuelve) e instala un cron de respaldo diario (03:17, `respaldar.sh`, 7 días en `~/parcheu/backups`). Es idempotente: se puede repetir para actualizar.

Respaldo a mano: `ssh user@homelab '~/parcheu/respaldar.sh'`. Restaurar: `gunzip -c parcheu-AAAAMMDD-HHMMSS.sql.gz | docker compose exec -T parcheu-db psql -U parcheu parcheu` (con la base vacía).
Registros: `ssh user@homelab 'cd ~/parcheu && docker compose logs -f parcheu-api'`.

## Qué funciona sin la llave de Google (`PARCHEU_ENV=local`, el valor inicial)

El backend arranca con `ENV=local`: autenticación Fake (`Authorization: Bearer fake-<uid>` para `/v1/*`), IA falsa (`FakeLlmClient`, sin gastar), FCM falso, chat y espejos de Firestore en memoria, cola de tareas Fake.

Funciona: `GET /v1/health`, sesión y términos, perfil, gemelo (`build-persona` en línea), grafo, invitaciones, cuotas, `grant-plus` de prueba, `seed-demo`, y todo con Postgres real. Se prueba con `curl`:

```bash
curl http://homelab:8090/v1/health
curl -XPOST -H 'Authorization: Bearer fake-ana' http://homelab:8090/v1/session
```

No funciona: iniciar sesión desde la app Android (envía un ID token real de Firebase, que en modo `local` no se valida), IA real (Vertex), notificaciones push, espejos de Firestore (chat y progreso en vivo), borrado de usuarios en Firebase Auth, verificación de compras en Play y las simulaciones y tareas encoladas (la cola Fake de `local` solo ejecuta en línea el gemelo).

**Seguridad.** En modo `local` cualquiera con acceso al puerto puede suplantar a cualquier usuario. Por eso el 8090 queda solo en LAN y Tailscale: **no lo publiques por Cloudflare en modo `local`.**

## Con la app real: `PARCHEU_ENV=dev` y la llave de Google

En `dev` la API verifica ID tokens de Firebase, usa Vertex, Firestore y FCM con las credenciales de `~/parcheu/secrets/sa.json`.

### Roles mínimos de la cuenta de servicio

Según el código (`sa-api` en el Terraform, sin los de Cloud SQL, logs y Cloud Run que aquí no aplican):

| Rol | Para qué |
|---|---|
| `roles/aiplatform.user` | Vertex AI (Gemini/Claude): gemelo, simulación, juez, moderación |
| `roles/firebaseauth.admin` | Verificar ID tokens y borrar usuarios (`FirebaseAuthAccountRemover`) |
| `roles/datastore.user` | Espejos de Firestore (chat, `matchTickets`, `simulations`) |
| `roles/firebasecloudmessaging.admin` | Enviar push (FCM) |

No hace falta Secret Manager: la clave de `anonId` va directa en `HMAC_ANON_KEY` del `.env`. Cloud Tasks (`roles/cloudtasks.enqueuer`) solo si configuras colas reales (más abajo). Play Billing (`PLAY_SA_JSON`) queda fuera: `verify` responderá 500, y Plus se prueba con `/internal/dev/grant-plus`.

### Crear la llave (pasos del usuario)

```bash
gcloud config set project parcheu-dev
gcloud iam service-accounts create sa-homelab --display-name "ParcheU homelab"
for r in aiplatform.user firebaseauth.admin datastore.user firebasecloudmessaging.admin; do
  gcloud projects add-iam-policy-binding parcheu-dev \
    --member serviceAccount:sa-homelab@parcheu-dev.iam.gserviceaccount.com --role roles/$r
done
gcloud iam service-accounts keys create /tmp/sa.json \
  --iam-account sa-homelab@parcheu-dev.iam.gserviceaccount.com
scp /tmp/sa.json user@homelab:parcheu/secrets/sa.json
ssh user@homelab 'chmod 600 ~/parcheu/secrets/sa.json'
shred -u /tmp/sa.json
```

Si la organización bloquea llaves (`iam.disableServiceAccountKeyCreation`), no hay alternativa sin ADC; en ese caso quédate en modo `local`. La cuenta se creó fuera de Terraform a propósito (es temporal): bórrala al terminar las pruebas.

### Activar el modo

Edita `~/parcheu/.env` en el servidor:

```
PARCHEU_ENV=dev
FIREBASE_PROJECT_ID=parcheu-dev
AI_VERTEX_PROJECT=parcheu-dev
TASKS_SERVICE_ACCOUNT=sa-homelab@parcheu-dev.iam.gserviceaccount.com
INTERNAL_OIDC_AUDIENCE=https://api-parcheu.homeserverlab.site
```

y `cd ~/parcheu && docker compose up -d parcheu-api`. Las migraciones son iguales. `ENV=dev` exige proyecto de Firebase, audiencia https y una cuenta de servicio como `TASKS_SERVICE_ACCOUNT`; si falta algo, la API no arranca y el mensaje sale en los registros.

Limitación: sin colas de Cloud Tasks, las tareas asíncronas (construir el gemelo, simulaciones, purga de cuentas) fallan al encolar. Para tenerlas hay que crear las colas `ai-light`, `simulations` y `maintenance` en `parcheu-dev`, dar `roles/cloudtasks.enqueuer` (y `iam.serviceAccountUser` sobre `TASKS_SERVICE_ACCOUNT`) a `sa-homelab`, y llenar `GCP_REGION`, `TASKS_QUEUE_*` y `SERVICE_URL=https://api-parcheu.homeserverlab.site` en el `.env`; Cloud Tasks llamará a esa URL pública con un token OIDC, así que antes hay que exponer la API (siguiente sección). Es opcional y no se probó.

## HTTPS con Nginx Proxy Manager (paso del usuario)

El servidor ya tiene NPM con el certificado comodín `*.homeserverlab.site` (Let's Encrypt, vence en ene 2027) y AdGuard reescribe `*.homeserverlab.site` a `192.168.0.10` en la LAN. En NPM crea un *Proxy Host*: dominio `api-parcheu.homeserverlab.site`, esquema `http`, destino `192.168.0.10` puerto `8090`, pestaña SSL con el certificado existente `npm-1` y *Force SSL*. Nada de esto lo hace `desplegar.sh`. Después, `curl https://api-parcheu.homeserverlab.site/v1/health` debe responder. Funciona en el emulador y en teléfonos de la LAN (o por Tailscale usando el DNS de AdGuard).

## Exponerlo a testers de Play (túnel de Cloudflare existente)

Solo en modo `dev` (token real de Firebase). El dominio está en Cloudflare: en Zero Trust, en el túnel que ya corre en el servidor, agrega la ruta del hostname `api-parcheu.homeserverlab.site` → `HTTP` `localhost:8090` (el túnel usa `network_mode: host`). Luego la app debe apuntar a `https://api-parcheu.homeserverlab.site/` (`-Pparcheu.apiUrl.prod=...` al construir el AAB de la prueba cerrada). Los testers no necesitan Tailscale ni red local.

## Probar la app de debug en el teléfono

Ver «Backend en el homeserver» en [docs/desarrollo-local.md](../../docs/desarrollo-local.md): `parcheu.apiUrl.dev=https://api-parcheu.homeserverlab.site/` en `android/local.properties` (respaldo HTTP: `http://192.168.0.10:8090/`). El `devDebug` permite HTTP, como respaldo, solo hacia 192.168.0.10, 100.104.43.91, `homelab` y 10.0.2.2; el release y el flavor `prod` no.

## Volver a Cloud SQL y Cloud Run

Nada del código cambia. Baja el homeserver (`ssh user@homelab 'cd ~/parcheu && docker compose down'`; `down -v` borra la base), aplica el Terraform de dev (ver `docs/pendientes-humanos.md`), define la variable de repositorio `GCP_DEV_ENABLED=true` para reactivar `deploy-dev` y quita `parcheu.apiUrl.dev` de `local.properties` (la app vuelve a la URL de Cloud Run). Si quieres conservar datos: restaura un respaldo en Cloud SQL con `psql`.
