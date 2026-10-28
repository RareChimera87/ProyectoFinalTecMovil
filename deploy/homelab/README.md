# ParcheU en el homeserver

Despliegue del backend Ktor en el homeserver del equipo (Docker Compose), en vez de Cloud SQL + Cloud Run, para pruebas. Decisión en [ADR-0033](../../docs/adr/0033-backend-en-homeserver.md).

| Pieza | Qué es |
|---|---|
| `parcheu-db` | Postgres 16 con volumen `parcheu-pgdata`; **no publica puertos** |
| `parcheu-migrate` | Flyway (modo `migrate`) una sola vez antes de la API |
| `parcheu-api` | Ktor publicado en `127.0.0.1:8090` y `192.168.0.10:8090` del host (loopback y LAN; no en `0.0.0.0`, ni Tailscale; el router no lo expone). Se cambia con `PARCHEU_BIND_LOCAL` / `PARCHEU_BIND_LAN` en `.env` |

Memoria: API con `-Xmx384m` y tope de 640 MB, Postgres 384 MB, migración 512 MB solo mientras corre.
No toca los demás contenedores del servidor (seafile, n8n, nginx-proxy-manager, etc.).

## Desplegar (desde el portátil)

Requisitos: SSH sin contraseña a `user@homelab`, JDK para Gradle y red para que Jib baje `eclipse-temurin:21-jre` (la imagen es amd64).

```bash
deploy/homelab/desplegar.sh                 # destino user@homelab
deploy/homelab/desplegar.sh --host usuario@192.168.0.10
deploy/homelab/desplegar.sh --sin-build     # reutiliza backend/build/jib-image.tar
```

El script: comprueba que el 8090 esté libre (o sea de ParcheU) y aborta si no, construye la imagen, copia imagen y compose a `~/parcheu/`, crea `~/parcheu/.env` con contraseñas aleatorias la primera vez (`chmod 600`, nunca se sobrescribe; solo agrega claves que falten, como `TASKS_MODE=inline`), carga la imagen y la etiqueta con su SHA (`PARCHEU_TAG`, la anterior queda en `PARCHEU_PREV_TAG`), levanta la API con un solo `docker compose up` (la migración corre antes por `depends_on`), verifica la salud desde el servidor y, **si falla, revierte solo a la imagen anterior** (las migraciones de Flyway no se deshacen: usa migraciones compatibles hacia atrás). Tras un despliegue sano borra las etiquetas anteriores a la previa (nunca `docker image prune`), avisa si el HTTPS responde con `PARCHEU_ENV=local` e instala un cron de respaldo diario (03:17, `respaldar.sh`, 7 días en `~/parcheu/backups`, `respaldo.log` rotado a 1 MiB). Es idempotente: se puede repetir para actualizar. Las ediciones del `.env` son atómicas (archivo temporal + `mv`, con `.env.bak` previo) y los despliegues simultáneos se serializan con `flock`; si `docker compose up` falla, el `.env` vuelve a la etiqueta anterior.

Advertencias operativas:
- **La IP `192.168.0.10` del servidor debe ser estática** (reserva DHCP en el router): el compose publica el 8090 en esa IP y, si cambia, la API no arranca o queda inalcanzable.
- **Evita desplegar con cambios sin commit**: la etiqueta sale del SHA; el script le agrega `-dirty` si el árbol tiene cambios, pero esa imagen no es reproducible.
- **Reversión y migraciones:** Flyway no se deshace. Si la versión nueva aplicó una migración y luego se revierte la imagen, el código viejo corre contra el esquema nuevo; y si la anterior trae migraciones que la actual no conoce (por ejemplo tras volver a una rama con migraciones en otro orden), Flyway falla por orden. Escribe migraciones compatibles hacia atrás y, ante un fallo así, restaura un respaldo.
- Una simulación cuyo proceso muere varias veces seguidas se reencola en cada ciclo de recuperación (cada 15 min, ventana de 24 h); no hay contador de recuperaciones porque exigiría una migración. Vigila el gasto de IA si ves el mismo id repetido en los registros.

Los respaldos viven en el mismo servidor: copia `~/parcheu/backups` fuera de él (p. ej. a Seafile) si los datos importan.

Datos de demostración: `ssh user@homelab 'cd ~/parcheu && docker compose run --rm parcheu-migrate seed-demo'` (la misma imagen, modo `seed-demo`; en `local` o `dev`).

### Tareas en proceso (`TASKS_MODE=inline`)

Sin Cloud Tasks ni Cloud Scheduler, la API ejecuta las tareas asíncronas dentro del proceso (`InProcessTaskQueue`): `build-persona`, `simulate`, `purge-user` y `extract-memories`, con hasta 5 intentos (10/30/90/270 s con jitter), concurrencia por cola (simulaciones 2, IA ligera 4, mantenimiento 1) y deduplicación por nombre durante 1 h. Un programador interno corre los jobs `match-sweep` (cada 60 s), `purge-sweep` (cada hora) `simulation-recovery` (cada 15 min; reencola las simulaciones QUEUED/RUNNING de entre 10 min y 24 h, p. ej. tras un reinicio) y `memories` (diario a las 02:00 de Bogotá, como en Cloud Scheduler; encola `extract-memories-<uuid>-<fecha>` por usuario con mensajes aprendibles nuevos; el nombre por día solo deduplica dentro del mismo proceso, porque la deduplicación de `InProcessTaskQueue` vive en memoria y se pierde al reiniciar: lo que evita el trabajo doble es el cursor de aprendizaje, que avanza junto con las memorias guardadas. Tras un reinicio el job no corre hasta las 02:00 siguientes; si el servidor está caído a esa hora, se recupera la noche siguiente, con un tope de 200 mensajes por usuario y ejecución). `twin-accuracy` (E9.3) corre los domingos a las 23:00 de Bogotá y guarda un punto semanal por usuario con datos (upsert por semana: repetirlo no duplica; si el servidor está caído esa noche, esa semana queda sin punto). Communities sigue siendo un stub y no se programa. `inline` se rechaza con `ENV=prod` y dentro de Cloud Run.

Respaldo a mano: `ssh user@homelab '~/parcheu/respaldar.sh'`. Restaurar, en orden: `docker compose stop parcheu-api`; vaciar la base (`docker compose exec parcheu-db psql -U parcheu -d postgres -c 'DROP DATABASE parcheu WITH (FORCE)' -c 'CREATE DATABASE parcheu OWNER parcheu'`; no uses `down -v`, porque `parcheu-migrate` correría Flyway antes); `gunzip -c parcheu-AAAAMMDD-HHMMSS.sql.gz | docker compose exec -T parcheu-db psql -v ON_ERROR_STOP=1 -U parcheu parcheu`; fijar en `PARCHEU_TAG` una imagen compatible con el esquema del respaldo y `docker compose up -d`.
Registros: `ssh user@homelab 'cd ~/parcheu && docker compose logs -f parcheu-api'`.

## Qué funciona sin la llave de Google (`PARCHEU_ENV=local`, el valor inicial)

El backend arranca con `ENV=local`: autenticación Fake (`Authorization: Bearer fake-<uid>` para `/v1/*`), IA falsa (`FakeLlmClient`, sin gastar), FCM falso, chat y espejos de Firestore en memoria, tareas en proceso (`TASKS_MODE=inline`).

Funciona: `GET /v1/health`, sesión y términos, perfil, gemelo (`build-persona` en línea), grafo, invitaciones, cuotas, `grant-plus` de prueba, `seed-demo`, y todo con Postgres real. Se prueba con `curl`:

```bash
curl http://192.168.0.10:8090/v1/health        # desde la LAN (o https://api-parcheu.homeserverlab.site si ya hay Proxy Host)
curl -XPOST -H 'Authorization: Bearer fake-ana' http://192.168.0.10:8090/v1/session
```

No funciona: iniciar sesión desde la app Android (envía un ID token real de Firebase, que en modo `local` no se valida), IA real (Vertex), notificaciones push, espejos de Firestore (chat y progreso en vivo), borrado de usuarios en Firebase Auth, verificación de compras en Play (`verify` responde 500; Plus se prueba con `grant-plus`). El gemelo y las simulaciones corren en proceso, con IA falsa.

**Seguridad.** En modo `local` cualquiera con acceso al puerto puede suplantar a cualquier usuario. Por eso el 8090 queda solo en loopback y la LAN: **no lo publiques por Cloudflare en modo `local`.** Mientras `PARCHEU_ENV=local`, el Proxy Host de NPM debe llevar una *Access List* que permita solo `192.168.0.0/24` y `100.64.0.0/10` (Tailscale), o crearse únicamente después de pasar a `dev`; `desplegar.sh` avisa si el HTTPS responde con `local`. El token `fake-admin` (moderación y lectura de todos los reportes, con los textos de los chats) solo existe si defines `LOCAL_FAKE_ADMIN=true`; no va en `.env.example` ni conviene activarlo en el homeserver, donde viven datos de testers.

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

No hace falta Secret Manager: la clave de `anonId` va directa en `HMAC_ANON_KEY` del `.env`, ni Cloud Tasks (las tareas corren en proceso). Play Billing (`PLAY_SA_JSON`) queda fuera: `verify` responderá 500 y las notificaciones RTDN de Play (renovaciones y cancelaciones) no se sincronizan en el homeserver; Plus se prueba con `/internal/dev/grant-plus`. Esa ruta exige un token OIDC de la cuenta de `TASKS_SERVICE_ACCOUNT`:

```bash
gcloud auth activate-service-account --key-file sa.json
TOKEN=$(gcloud auth print-identity-token --audiences=https://api-parcheu.homeserverlab.site)
```

y se envía como `Authorization: Bearer $TOKEN`.

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

y `cd ~/parcheu && docker compose up -d parcheu-api`. Usa el mismo proyecto de Firebase que el flavor `dev` de la app (`parcheu-dev`). Las migraciones son iguales. `ENV=dev` exige proyecto de Firebase, audiencia https y una cuenta de servicio como `TASKS_SERVICE_ACCOUNT`; si falta algo, la API no arranca y el mensaje sale en los registros.

Las tareas asíncronas (gemelo, memorias, simulaciones, purga de cuentas) y los jobs periódicos corren dentro de la API (`TASKS_MODE=inline`); no hace falta crear colas.

## HTTPS con Nginx Proxy Manager (paso del usuario)

El servidor ya tiene NPM con el certificado comodín `*.homeserverlab.site` (Let's Encrypt, vence en ene 2027) y AdGuard reescribe `*.homeserverlab.site` a `192.168.0.10` en la LAN. En NPM crea un *Proxy Host*: dominio `api-parcheu.homeserverlab.site`, esquema `http`, destino `192.168.0.10` puerto `8090`, pestaña SSL con el certificado existente `npm-1` y *Force SSL*. Nada de esto lo hace `desplegar.sh`. **Mientras `PARCHEU_ENV=local`** agrega una *Access List* en NPM (pestaña Details del Proxy Host) que permita solo `192.168.0.0/24` y `100.64.0.0/10` y deniegue el resto, o crea el Proxy Host solo después de pasar a `dev`. Después, `curl https://api-parcheu.homeserverlab.site/v1/health` debe responder. Funciona en el emulador y en teléfonos de la LAN (o por Tailscale usando el DNS de AdGuard).

## Exponerlo a testers de Play (túnel de Cloudflare existente)

Fuera de alcance por ahora: no hay testers ni publicación en Play. Para publicar más adelante habría que alinear el proyecto de Firebase y de Vertex de la instancia (`FIREBASE_PROJECT_ID`, `AI_VERTEX_PROJECT`) con el del flavor publicado (`prod` apunta a `parcheu-prod`, que aún no existe; la alternativa es publicar con el flavor `dev`) y fijar la variable de repositorio `PARCHEU_API_URL_PROD`; la subida automática a alpha depende de `deploy-prod` (sin GCP queda como `workflow_dispatch`). Solo en modo `dev` (token real de Firebase). El dominio está en Cloudflare: en Zero Trust, en el túnel que ya corre en el servidor, agrega la ruta del hostname `api-parcheu.homeserverlab.site` → `HTTP` `localhost:8090` (el túnel usa `network_mode: host`). Luego la app debe apuntar a `https://api-parcheu.homeserverlab.site/` (`-Pparcheu.apiUrl.prod=...` al construir el AAB de la prueba cerrada). Los testers no necesitan Tailscale ni red local.

## Probar la app de debug en el teléfono

Ver «Backend en el homeserver» en [docs/desarrollo-local.md](../../docs/desarrollo-local.md): `parcheu.apiUrl.dev=https://api-parcheu.homeserverlab.site/` en `android/local.properties` (respaldo HTTP: `http://192.168.0.10:8090/`). El `devDebug` permite HTTP, como respaldo, solo hacia 192.168.0.10 y 10.0.2.2; el release y el flavor `prod` no.

## Volver a Cloud SQL y Cloud Run

Nada del código cambia. Baja el homeserver (`ssh user@homelab 'cd ~/parcheu && docker compose down'`; `down -v` borra la base), aplica el Terraform de dev (ver `docs/pendientes-humanos.md`), define la variable de repositorio `GCP_DEV_ENABLED=true` para reactivar `deploy-dev` y quita `parcheu.apiUrl.dev` de `local.properties` (la app vuelve a la URL de Cloud Run). Si quieres conservar datos: restaura un respaldo en Cloud SQL con `psql`.
