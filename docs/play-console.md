# Play Console

> Este archivo lo crean varias features en paralelo: E10.1 (registro de la cuenta, §1-§10), E7.0 (billing y
> primera subida, §11), E8.4 (URLs legales) y E10.3 (prueba cerrada 0.1, §12). Al mezclar, **conservar todas
> las secciones**; la tabla de fechas de la §9 de E10.1 se reemplaza por la §12.11 de este archivo.
> Sin secretos ni correos de testers: solo pasos, decisiones, conteos y fechas.

## 12. Prueba cerrada temprana 0.1 (E10.3)

Responsable: I3 · Revisor par: I1 · **Envío a revisión: 29 oct 2026** · Referencias: [ADR-0016](adr/0016-prueba-cerrada-temprana.md),
[PLAN §5 'Releases intermedios'](PLAN.md), [arquitectura §10](arquitectura.md), [pendientes humanos](pendientes-humanos.md#e103--prueba-cerrada-temprana-01).

### 12.1 Qué es la 0.1 y qué no

| Incluye | No incluye |
|---|---|
| Esqueleto navegable de E0.7 (stubs de las 36 pantallas, NavBar) | Login (E2.2 llega en la ola 6) |
| Enlaces 'Términos' y 'Política de privacidad' en A4 → `https://parcheu-prod.web.app/terminos` y `/privacidad` (E8.4, `BuildConfig.TERMS_URL`/`PRIVACY_URL` del flavor `prod`) | Backend: la 0.1 no llama a la API |
| Play Billing de E7.0 (`billing-ktx`, `BillingClientProvider`; `parcheu_plus` existe en Play desde el 28 oct) | UI de compra (E7.2/E7.3, 0.3) |
| | Datos personales: la 0.1 no los trata (ver §12.6) |

El reloj de los **14 días corre desde que Play aprueba la versión y ≥ 12 testers están inscritos (opt-in)**, no desde
la subida. Aprobación estimada: 30-31 oct.

### 12.2 Requisitos previos (verificar el 28 oct)

| # | Requisito | Feature | Cómo se comprueba |
|---|---|---|---|
| 1 | Cuenta verificada, app creada, Play App Signing activo, grupo de testers con ≥ 15 confirmados | E10.1 | §1 de este archivo |
| 2 | AAB 0.0.1 en la pista interna y `parcheu_plus` creada | E7.0 | §11.3 |
| 3 | Esqueleto, billing, Firebase y páginas legales mezclados en `dev` | E0.7 (#8), E7.0 (#17), E0.8 (#6), E8.4 (#16) | `gh pr list --state merged` |
| 4 | `parcheu-prod` creado y Terraform de prod aplicado por el usuario | E0.5 | `gcloud projects describe parcheu-prod` |
| 5 | App Android `me.santiagoreina.parcheu` registrada en Firebase prod | E0.8 | `firebase apps:list ANDROID --project parcheu-prod` |
| 6 | Pipeline `android-release` que sube a la pista cerrada (si no está, plan B de §12.3 paso 6) | E10.2 | `gh workflow list` |

### 12.3 Proceso de release 0.1 (PLAN §5)

1. **Rama de release** (el 28-29 oct, con todo lo de §12.2 en `dev`):

   ```bash
   git fetch origin && git switch -c release/0.1.0 origin/dev
   ```

2. **Versión.** Hoy `android/app/build.gradle.kts` fija `versionCode = 1` y `versionName = "0.0.1"` (los usó el AAB de
   E7.0). La 0.1 necesita `versionName = "0.1.0"` y un `versionCode` **mayor que 1** (Play rechaza un `versionCode`
   repetido). Si E10.2 ya deriva el `versionCode` del número de ejecución, solo cambia `versionName`; si no, sube
   ambos a mano (`versionCode = 2`). El cambio entra como `fix/version-0.1.0` contra `release/0.1.0`.
3. **PR `release/0.1.0` → `main`** con el título `Release 0.1.0`. **Lo aprueba el usuario** (nadie más mezcla a `main`).
4. **Tag** tras el merge: `git tag -a v0.1.0 -m "ParcheU 0.1.0" origin/main && git push origin v0.1.0`.
5. **`deploy-prod`** (environment `production`, aprobación del usuario): en la 0.1 solo corre `firebase-deploy` prod
   (reglas, índices y Hosting; la 0.1 no usa backend). Verifica que las páginas legales respondan:

   ```bash
   for p in privacidad terminos borrar-cuenta; do
     printf '%s ' "$p"; curl -s -o /dev/null -w '%{http_code}\n' "https://parcheu-prod.web.app/$p"
   done   # las tres deben dar 200
   ```

6. **AAB a la pista cerrada.** Camino normal: `android-release` (E10.2) construye el AAB `prod` firmado y lo sube a la
   pista **cerrada** con las notas de §12.8. Plan B si E10.2 no está mezclado el 29 oct: construir a mano como en
   §11.1 (llave de subida de `~/.parcheu/`, con el `versionCode` > 1 del paso 2) y subirlo en *Pruebas > Prueba
   cerrada > Crear versión*. Comprueba antes de subir:

   ```bash
   jarsigner -verify -certs app/build/outputs/bundle/prodRelease/app-prod-release.aab | grep 'CN=ParcheU Upload'
   ```

   Al subirlo, Play muestra el `versionCode` y el `versionName` del AAB: deben ser > 1 y `0.1.0` (si tienes
   `bundletool`, también: `bundletool dump manifest --bundle <aab> | grep -oE 'android:version(Code|Name)="[^"]*"'`).

7. **`release/0.1.0` → `dev`** (PR de vuelta) para que el cambio de versión no se pierda.

### 12.4 Huellas de Play App Signing en Firebase prod

Las huellas del **certificado de firma de la app** (no las de la llave de subida) aparecen en *Integridad de la app >
Firma de apps* después de la primera subida (E7.0, §11.3). Son públicas. Sin ellas el login con Google de una
instalación de Play falla (se verifica en la 0.2, criterio de E10.8).

```bash
export PATH=$HOME/google-cloud-sdk/bin:$PATH
APP_ID=$(firebase apps:list ANDROID --project parcheu-prod --json \
  | jq -r '.result[] | select(.packageName=="me.santiagoreina.parcheu") | .appId')
firebase apps:android:sha:create "$APP_ID" <SHA-1 de firma de la app>   --project parcheu-prod
firebase apps:android:sha:create "$APP_ID" <SHA-256 de firma de la app> --project parcheu-prod
firebase apps:android:sha:list   "$APP_ID" --project parcheu-prod   # deben aparecer ambas (y las de subida, si E0.8 las registró)
# El google-services.json cambia (nuevos oauth_client): recárgalo
firebase apps:sdkconfig ANDROID "$APP_ID" --project parcheu-prod > ~/.parcheu/google-services.prod.json
base64 -w0 ~/.parcheu/google-services.prod.json | gh secret set GOOGLE_SERVICES_JSON_PROD -R RareChimera87/ProyectoFinalTecMovil
```

Captura para el PR de E10.3: *Configuración del proyecto > Tus apps > ParcheU (Android)* en la consola de Firebase de
prod con las huellas visibles. Registra las huellas (son públicas) en §12.11.

### 12.5 Contenido de la app (Política > Contenido de la app)

| Declaración | Respuesta para la 0.1 | Nota |
|---|---|---|
| Política de privacidad | `https://parcheu-prod.web.app/privacidad` | Debe responder 200 antes de enviar (§12.3 paso 5) |
| Anuncios | **No**, la app no contiene anuncios | |
| Acceso a la app | **Todas las funciones están disponibles sin restricciones de acceso** | Desde la 0.2 (login) cambia a 'Algunas funciones están restringidas' con instrucciones y una cuenta de demostración (E10.8) |
| Clasificación de contenido | Cuestionario IARC (§12.7) | |
| Público objetivo y contenido | Solo **18 años o más**; la app **no** atrae a niños | arquitectura §10.1 (+18, ADR-0031) |
| Apps de noticias | No | |
| Apps de salud | No | |
| Funciones financieras | Ninguna | La suscripción no es una función financiera |
| Apps gubernamentales | No | |
| Seguridad de los datos | Borrador de §12.6 | |
| ID de publicidad | No usa el ID de publicidad | Ningún SDK de anuncios ni de analítica en la 0.1 |

### 12.6 Seguridad de los datos (borrador de la 0.1)

La 0.1 no envía datos a ningún servidor propio ni de terceros: no tiene login, no llama a la API, no incluye
Firebase Analytics ni Crashlytics y la compra no tiene UI. Play Billing lo opera Google Play.

| Pregunta | Respuesta 0.1 |
|---|---|
| ¿Tu app recopila o comparte alguno de los tipos de datos obligatorios? | **No** |
| ¿Los datos se encriptan en tránsito? | No aplica (no hay datos recopilados) |
| ¿Los usuarios pueden pedir que se borren sus datos? / ¿Permite crear cuentas? | La 0.1 no permite crear cuentas. Igual existe `https://parcheu-prod.web.app/borrar-cuenta` |

**Verificar en la ayuda de Play el día del envío** si la información de compras gestionada por la biblioteca de
Play Billing debe declararse como 'Historial de compras' aunque la app no la envíe a un servidor propio; si Play lo
exige, declararla como *recopilada, no compartida, necesaria para la funcionalidad de la app*.

**Esta declaración cambia con la 0.2** (login, perfil, chat, gemelo, fecha de nacimiento, reportes): la
actualización completa la hace E10.8 a partir de la tabla de retención de arquitectura §10.2. No enviar la 0.2 con
este borrador.

### 12.7 Clasificación de contenido (cuestionario IARC)

Recomendación: responder por la app **final**, no solo por la 0.1, para no obtener una clasificación más baja que
luego cambie con la 0.2 (cambiar respuestas después genera una nueva clasificación).

| Pregunta | Respuesta |
|---|---|
| Categoría | Redes sociales / comunicación (no es un juego) |
| Violencia, sexualidad, lenguaje soez, sustancias, apuestas | No |
| ¿Los usuarios pueden interactuar o intercambiar contenido? | **Sí** (chat entre usuarios desde la 0.2) |
| ¿Comparte la ubicación del usuario con otros usuarios? | No |
| ¿Permite compras de productos digitales? | **Sí** (`parcheu_plus`) |
| ¿Contenido generado por IA o sin moderar? | Responder lo que el formulario pregunte con base en arquitectura §10 (moderación, reportes y bloqueo de E8.1) |

Registra la clasificación obtenida (p. ej. 'Adolescentes'/'PEGI 12' o la que asigne) en §12.11.

### 12.8 Ficha de prueba, instrucciones para testers y notas de versión

**Ficha principal mínima** (Play la exige antes de enviar a revisión cualquier pista que no sea la interna; la
definitiva es de E10.7): nombre `ParcheU`; descripción breve (≤ 80): `Tu gemelo digital para conocer gente afín en
tu universidad.`; descripción completa: la de abajo; ícono 512×512 y gráfico de funciones 1024×500 de E1.2; al menos
2 capturas de teléfono del esqueleto (`./gradlew :app:installProdRelease` y capturas con `adb exec-out screencap -p`);
categoría Social; correo de contacto (el de E8.4).

**Descripción / instrucciones para testers** (es-419, pegar en *Prueba cerrada > Testers > Comentarios* y en la
descripción completa mientras dure la prueba):

> Esta es una **versión temprana (0.1)** de ParcheU para la prueba cerrada. Por ahora solo trae la navegación de
> la app, las pantallas en construcción y los enlaces a los Términos y la Política de privacidad. Todavía **no hay
> inicio de sesión** ni se guardan datos tuyos. En las próximas versiones (desde el 9 de noviembre) llegarán el
> inicio de sesión, el gemelo digital, las simulaciones y el chat, y verás **perfiles de demostración** marcados
> como tales para que la app no se sienta vacía. Te pedimos que mantengas la app instalada y no te salgas de la
> prueba durante al menos 14 días: Google lo exige para publicar la app. Si algo falla, escríbenos desde
> 'Enviar comentarios' en la ficha de Play.

**Notas de versión 0.1.0** (es-419, ≤ 500 caracteres; las usa E10.2 o se pegan a mano):

```
Primera versión de prueba cerrada: navegación de la app, enlaces a Términos y Política de privacidad, y base de la suscripción ParcheU+. Aún sin inicio de sesión.
```

**Países de la prueba cerrada:** Colombia.

### 12.9 Envío, inscripción y reloj de 14 días

1. **29 oct:** con la versión en la pista cerrada y el contenido de la app completo (§12.5-§12.8), *Resumen de
   la publicación > Enviar cambios a revisión*. Registra la fecha.
2. **Al llegar la aprobación** (correo de Play o *Resumen de la publicación*): copia el **enlace de inscripción**
   de *Prueba cerrada > Testers* y envíalo **ese mismo día** al grupo de testers con un mensaje corto: abrir el
   enlace con la cuenta de Google del teléfono, pulsar 'Convertirse en tester', instalar desde Play y **no salirse
   de la prueba durante 14 días**.
3. **Conteo diario** de inscritos: *Prueba cerrada > Testers* muestra cuántos se inscribieron. Anota el conteo del
   día de la aprobación (meta ≥ 12, con 15-20 reclutados) y repórtalo cada día en el diario del equipo. Si baja de
   12, el reloj de Play se reinicia: recluta reemplazos el mismo día.
4. **Fecha de los 14 días.** Con `INICIO` = primer día con ≥ 12 inscritos (día 1):

   ```bash
   INICIO=2026-10-31   # reemplazar por la fecha real
   echo "Día 14: $(date -d "$INICIO + 13 days" +%F) · solicitar acceso a producción desde: $(date -d "$INICIO + 14 days" +%F)"
   # Con 2026-10-31 → Día 14: 2026-11-13 · solicitar acceso a producción desde: 2026-11-14 (coincide con ADR-0016)
   ```

   Confirma la fecha en el *Panel* de Play Console, que muestra el progreso del requisito de 14 días; si difiere,
   manda la de Play.

### 12.10 Prueba de instalación desde la pista cerrada

Con un teléfono inscrito (no el del desarrollador con build local; desinstala antes cualquier build `prod` de debug
para que Play pueda instalar):

1. Instala ParcheU desde el enlace de Play (pestaña 'Testers' de la ficha). Debe instalar `me.santiagoreina.parcheu`
   versión 0.1.0: `adb shell dumpsys package me.santiagoreina.parcheu | grep -E 'versionName|installerPackageName'`
   (instalador `com.android.vending`).
2. Abre la app: arranca sin cerrarse y muestra el esqueleto.
3. En A4 pulsa **Términos** → abre `https://parcheu-prod.web.app/terminos`; pulsa **Política de privacidad** → abre
   `https://parcheu-prod.web.app/privacidad`. Ambas cargan el texto de E8.4 (no un 404 ni el sitio de dev).
4. Deja una captura de cada paso (sin datos personales) en el PR de E10.3 y marca la fila de §12.11.

### 12.11 Registro de la prueba cerrada (reemplaza la tabla de §9)

| Hito / dato | Valor | Fecha | Quién |
|---|---|---|---|
| `release/0.1.0` → `main` aprobado y tag `v0.1.0` | _pendiente_ | | usuario |
| Hosting prod: `/privacidad`, `/terminos`, `/borrar-cuenta` responden 200 | _pendiente_ | | I3 |
| AAB 0.1.0 en la pista cerrada (`versionCode`) | _pendiente_ | | I3 |
| Huella SHA-1 de firma de la app (Play) registrada en Firebase prod | _pendiente_ | | I3 |
| Huella SHA-256 de firma de la app (Play) registrada en Firebase prod | _pendiente_ | | I3 |
| Clasificación de contenido obtenida | _pendiente_ | | I3 |
| **Envío a revisión** | _pendiente (meta 29 oct)_ | | I3 |
| **Aprobación** | _pendiente (estimada 30-31 oct)_ | | I3 |
| Inscritos el día de la aprobación (meta ≥ 12) | _pendiente_ | | I3 |
| **Primer día con ≥ 12 inscritos** (día 1 del reloj) | _pendiente_ | | I3 |
| **Fecha calculada de los 14 días** (§12.9 paso 4) | _pendiente (≈ 13-14 nov)_ | | I3 |
| Instalación desde la pista cerrada y enlaces de A4 a prod verificados (§12.10) | _pendiente_ | | I3 |

### 12.12 Estado verificado por CLI (22 oct 2026)

- `parcheu-prod` **no existe todavía** (`gcloud projects list` y `firebase projects:list` solo muestran
  `parcheu-dev`); por eso no se pudo registrar ninguna huella en Firebase prod ni comprobar el Hosting de prod.
- Las huellas de Play App Signing aún no existen: aparecen tras la primera subida (E7.0, 28 oct).
- En `feature/android-skeleton` (E0.7) los enlaces de A4 (`LegalLinks`) abren `BuildConfig.TERMS_URL` y
  `BuildConfig.PRIVACY_URL`, que en el flavor `prod` valen `https://parcheu-prod.web.app/terminos` y `/privacidad`
  (configurables con `-Pparcheu.hostingUrl.prod`). Coincide con las páginas de E8.4.
- La 0.1 declara `android.permission.INTERNET` (necesario para abrir los enlaces y para Billing) y no incluye SDKs de
  Firebase ni de anuncios: base del borrador de §12.6.
- `versionCode = 1` / `versionName = "0.0.1"` en `android/app/build.gradle.kts`: hay que subirlos para la 0.1 (§12.3
  paso 2).
- La consola de Play no tiene API para enviar a revisión, llenar el contenido de la app, el cuestionario IARC ni
  la Seguridad de los datos, ni para leer el conteo de testers inscritos: todo eso es manual (I3).

### 12.13 Lista de verificación de E10.3

- [ ] Requisitos de §12.2 cumplidos el 28 oct.
- [ ] `v0.1.0` en `main` (aprobado por el usuario) y Hosting prod con las páginas legales en 200.
- [ ] Contenido de la app completo: privacidad, sin anuncios, acceso, IARC, 18+, Data safety (borrador 0.1).
- [ ] Ficha mínima e instrucciones para testers publicadas.
- [ ] 0.1 enviada a revisión el 29 oct.
- [ ] SHA-1 y SHA-256 de Play App Signing en la app Android de Firebase prod (captura en el PR).
- [ ] Enlace de inscripción enviado el día de la aprobación; ≥ 12 inscritos ese día.
- [ ] Fechas de §12.11 completas, incluida la de los 14 días.
- [ ] Instalación desde la pista cerrada abre la app y A4 lleva a `/terminos` y `/privacidad` de prod.
