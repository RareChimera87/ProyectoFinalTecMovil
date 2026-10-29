# Play Console: guía de configuración y registro (E10.1)

Responsable: I3 (tarea humana). Este documento **no contiene secretos**: solo pasos, decisiones y el registro de estado. Los valores sensibles viven en `~/.parcheu/` (fuera del repo) y en secretos de GitHub.

Referencias: [PLAN §9](PLAN.md), [ADR-0016](adr/0016-prueba-cerrada-temprana.md), [arquitectura §9 (billing)](arquitectura.md), [pendientes humanos](pendientes-humanos.md).

## 1. Registro de estado (completar a medida que avanza)

| Dato | Valor | Fecha | Quién |
|---|---|---|---|
| Tipo de cuenta (personal / organización) | _pendiente_ | | I3 |
| Fecha de creación de la cuenta de desarrollador | _pendiente_ | | I3 |
| ¿Aplica la regla de 12 testers × 14 días? | _pendiente (sí si es personal creada después de nov 2023)_ | | I3 |
| Verificación de identidad | _pendiente_ (enviada / en revisión / aprobada) | | I3 |
| Verificación de dispositivo Android (si Play la pide) | _pendiente_ | | I3 |
| App `me.santiagoreina.parcheu` creada | _pendiente_ | | I3 |
| Play App Signing activo | _pendiente_ | | I3 |
| Perfil de pagos | _pendiente_ (creado / verificado) | | I3 |
| Cuenta de servicio de CI con acceso a la app | _pendiente_ | | I3 |
| Grupo de Google de testers (dirección del grupo, sin correos individuales) | _pendiente_ | | I3 |
| Testers con correo confirmado | _0 de 15 (meta: 15-20 antes del 28 oct)_ | | I3 |
| License testers (≥ 3) | _pendiente_ (cantidad; sin listar correos) | | I3 |

No escribas correos personales de testers en este archivo: registra solo cantidades y la dirección del grupo.

## 2. Estado verificado por CLI (22 oct 2026)

Lo que sí se pudo comprobar o preparar sin la consola:

- `applicationId` y `namespace` del proyecto Android son `me.santiagoreina.parcheu` (coincide con el nombre de paquete que hay que registrar en Play; no se puede cambiar después).
- Secretos de GitHub del repo `RareChimera87/ProyectoFinalTecMovil`: **ninguno cargado todavía** (`gh secret list` vacío).
- Llave de subida **generada** en la máquina del usuario, fuera del repo: `~/.parcheu/upload-keystore.jks` (PKCS12, RSA 2048, alias `upload`, validez hasta 2054) y `~/.parcheu/upload-key.env` con las contraseñas (permisos 600). Huellas del certificado de subida (públicas, útiles para Play al registrar la llave):
  - SHA-1: `6B:C7:57:2F:5F:27:BF:0C:AD:8F:C4:BA:9B:51:26:93:7B:26:41:1E`
  - SHA-256: `7F:51:20:F3:06:45:12:A5:B4:A5:6C:0D:44:E1:67:2B:18:A4:BA:1F:EA:CB:62:1F:B5:E3:A2:AE:FF:8F:03:D3`
- **No se subieron** los secretos a GitHub: la acción fue bloqueada por el permiso del entorno, así que ese paso queda para el usuario (sección 6).
- Los proyectos GCP de ParcheU aún no existen (los crea el usuario el 22-23 oct), por lo que la cuenta de servicio de Play no se puede crear por CLI todavía.
- La consola de Play y la API de Play **no permiten** crear la cuenta de desarrollador, verificar identidad, crear la app ni aceptar términos por CLI; esos pasos son manuales.

## 3. Cuenta de desarrollador y regla de los 12 testers

1. Entra a <https://play.google.com/console> con la cuenta de Google del equipo (la del dueño I3). Si no hay cuenta, crea una (cuota única de USD 25).
2. En **Configuración > Detalles de la cuenta** anota el **tipo de cuenta** (personal u organización) y la **fecha de creación**. Regístralos en la tabla de la sección 1 **a más tardar el 24 oct**.
3. Interpretación:
   - **Personal creada después de nov 2023:** debes tener una prueba cerrada con **≥ 12 testers inscritos (opt-in) durante 14 días seguidos** antes de pedir acceso a producción. El reloj corre desde que Play **aprueba** la versión de la prueba cerrada y los testers se inscriben, no desde la subida (ADR-0016).
   - **Personal anterior a nov 2023 u organización:** la regla no aplica; registra igualmente la decisión y sigue el plan (la prueba temprana sigue siendo útil). Una cuenta de organización requiere D-U-N-S y no cabe en el plazo (ADR-0016).
4. Completa la **verificación de identidad** (documento, dirección, teléfono) y, si Play lo pide, la **verificación de un dispositivo Android** con la app Play Console. Puede tardar días: hazlo el primer día.

## 4. Crear la app

1. **Crear app**: nombre `ParcheU`, idioma predeterminado **es-419**, tipo **App**, **Gratis** (con compras dentro de la app), categoría **Social**.
2. Acepta las declaraciones del programa y las normas de exportación de EE. UU.
3. Nombre de paquete: se fija con el primer AAB (`me.santiagoreina.parcheu`, flavor `prod`). El flavor `dev` es `me.santiagoreina.parcheu.dev` y no va a Play.
4. **Play App Signing**: actívalo (viene activo por defecto en apps nuevas con AAB). Google guarda la llave de firma; nosotros usamos solo la **llave de subida**.
5. Cuando exista el primer AAB (E7.0, 28 oct), en **Integridad de la app > Firma de apps** copia las huellas SHA-1 y SHA-256 del **certificado de firma de la app** para registrarlas en Firebase prod (E10.3).

### 4.1 Primera subida: manual desde la consola (E7.0, 28 oct)

- La **primera subida** de la app es el AAB `prod` **0.0.1 con Play Billing** (`~/.parcheu/parcheu-0.0.1-prod-release.aab`, firmado con la llave de subida) y la hace I3 **a mano desde la consola**, en la pista interna, a más tardar el **28 oct** (E7.0, pasos en la [§11.2](#112-pasos-en-la-consola-i3-28-oct)). La API de Play no permite crear la primera versión de una app nueva.
- Es **prerrequisito para crear la suscripción `parcheu_plus`**: Play solo deja crear suscripciones cuando la app ya tiene un AAB subido que incluye Play Billing Library. Sin esa subida no hay `parcheu_plus` ni license testers útiles, y se bloquea E7.1-E7.3.
- **E10.2 valida la subida por API solo después** de esa primera subida manual: el workflow `android-release` (con `sa-play`) sube las versiones siguientes (0.1 en adelante) y su prueba de subida a la pista interna no puede pasar antes del 28 oct. No intentes adelantar la primera subida desde CI.

## 5. Perfil de pagos (comerciante)

Necesario para vender la suscripción `parcheu_plus` (riesgo n.º 4 del plan).

1. **Configuración > Perfil de pagos**: crea el perfil de comerciante en Colombia con los datos legales de quien recibirá los pagos.
2. Completa la verificación del perfil (datos fiscales y cuenta bancaria). Puede tardar días; no lo dejes para el 28 oct.
3. Registra el estado en la sección 1.

## 6. Llave de subida y secretos de GitHub (nunca en el repo)

La llave ya está generada (sección 2). Pasos para el usuario:

1. Haz una copia de seguridad de `~/.parcheu/upload-keystore.jks` y `~/.parcheu/upload-key.env` en un gestor de contraseñas. Si se pierde, se puede pedir a Play un restablecimiento de la llave de subida (con App Signing), pero toma días.
2. Carga los secretos del repo (desde tu máquina, sin pegar los valores en chats ni en el repo):

   ```bash
   cd ~/.parcheu && set -a && . ./upload-key.env && set +a
   R=RareChimera87/ProyectoFinalTecMovil
   base64 -w0 upload-keystore.jks | gh secret set UPLOAD_KEYSTORE -R $R
   printf %s "$UPLOAD_KEYSTORE_PASSWORD" | gh secret set UPLOAD_KEYSTORE_PASSWORD -R $R
   printf %s "$UPLOAD_KEY_ALIAS"         | gh secret set UPLOAD_KEY_ALIAS -R $R
   printf %s "$UPLOAD_KEY_PASSWORD"      | gh secret set UPLOAD_KEY_PASSWORD -R $R
   gh secret list -R $R   # deben aparecer los cuatro
   ```
3. La primera vez que subas un AAB (E7.0), Play te pedirá registrar la llave de subida si no la tomó del propio AAB; el certificado se exporta con:
   `keytool -export -rfc -alias upload -keystore ~/.parcheu/upload-keystore.jks -file ~/.parcheu/upload-certificate.pem`.
4. Ni el `.jks`, ni las contraseñas, ni `play-sa.json` se versionan jamás (gitleaks en CI, E10.2).
5. **Variables del release (no son secretos):** `PARCHEU_API_URL_PROD` y `PARCHEU_HOSTING_URL_PROD` (URLs `https://` de la API y del Hosting de `parcheu-prod`), a nivel de repo o en los environments `play`/`play-alpha`. Sin ellas `android-release` falla antes de compilar (el AAB no puede llevar la URL de relleno de `app/build.gradle.kts`). Ver `docs/pendientes-humanos.md`, "URLs de prod del release".

## 7. Cuenta de servicio para la API de Play (CI y backend)

Se necesita en cuanto existan los proyectos GCP (22-23 oct). Hay dos usos: publicar desde CI (E10.2) y verificar compras desde el backend (E7.1).

1. En el proyecto GCP **prod** de ParcheU (ID real en [arquitectura.md](arquitectura.md)), habilita las APIs **Google Play Android Developer API** (`androidpublisher.googleapis.com`) y, para RTDN, Pub/Sub.
2. Crea la cuenta de servicio `sa-play` (sin roles de IAM de GCP). Crea una llave JSON **solo si Play no admite WIF para este caso** (arquitectura §Secretos: verificar); si la creas, guárdala en `~/.parcheu/play-sa.json` (permisos 600).
3. En Play Console: **Usuarios y permisos > Invitar usuarios** con el correo de `sa-play`. Concede, sobre la app ParcheU: **Lanzar a pistas de prueba** (interna y cerrada), **Ver información de la app y descargar** y, para el backend, **Ver datos financieros** y **Administrar pedidos y suscripciones**. No des permiso de producción hasta E10.x.
4. Carga el secreto: `gh secret set PLAY_SERVICE_ACCOUNT_JSON -R RareChimera87/ProyectoFinalTecMovil < ~/.parcheu/play-sa.json`. Para el backend, el valor va a Secret Manager (`play-sa`) con `scripts/cargar-secretos.sh` (el contenedor lo crea Terraform sin valor).
5. Los permisos tardan hasta ~24 h en propagarse: hazlo con margen. E10.2 valida que esta cuenta puede subir a la pista interna.

## 8. Testers (prueba cerrada) y license testers

1. Crea un **Google Group** (p. ej. `parcheu-testers@googlegroups.com`) y recluta **15-20 personas** (meta: ≥ 15 correos confirmados antes del 28 oct; 12 inscritos es el mínimo de Play, el margen cubre bajas).
2. Cada tester debe tener una cuenta de Google en un dispositivo Android real. Pide confirmación explícita por mensaje y registra solo el conteo.
3. En **Pruebas > Prueba cerrada**, crea la pista y asigna el grupo como lista de testers; guarda el **enlace de inscripción (opt-in)**.
4. Envía el enlace el **mismo día** que Play apruebe la versión; mide cuántos se inscribieron ese día (meta ≥ 12) y registra la fecha en la sección 9.
5. **License testers** (**Configuración > Pruebas de licencia**): agrega al menos 3 cuentas (la tuya y las de dos compañeros) para probar la suscripción sin cobro real. Registra la cantidad.
6. Seguimiento diario de inscritos mientras corra el reloj (se reporta en el diario del equipo).

## 9. Fechas de la prueba cerrada (las completa E10.3)

| Hito | Fecha |
|---|---|
| Envío de la 0.1 a revisión | _pendiente (meta 29 oct)_ |
| Aprobación | _pendiente (estimada 30-31 oct)_ |
| Primer día con ≥ 12 testers inscritos | _pendiente_ |
| Fecha calculada de los 14 días | _pendiente_ |

## 10. Lista de verificación de E10.1

- [ ] Tipo de cuenta, fecha y verificación registrados (sección 1).
- [ ] App `me.santiagoreina.parcheu` creada, es-419, Social, gratis con compras.
- [ ] Play App Signing activo.
- [ ] Perfil de pagos verificado.
- [ ] Cuatro secretos de llave de subida cargados en GitHub.
- [ ] `sa-play` invitada a Play Console con permisos mínimos; `PLAY_SERVICE_ACCOUNT_JSON` cargado.
- [ ] Grupo de testers con ≥ 15 correos confirmados antes del 28 oct.
- [ ] ≥ 3 license testers.
- [ ] Primera subida (AAB 0.0.1 con Billing) hecha **a mano** en la consola antes del 28 oct (E7.0, ver [§4.1](#41-primera-subida-manual-desde-la-consola-e70-28-oct)); E10.2 valida la subida por API después.

## URLs legales (E8.4)

Las publica Firebase Hosting (`firebase/public`, workflow `firebase-deploy`). Versión de los textos: **2026-10-29**
(`<meta name="legal-text-version">` de cada página); debe ser igual a `LEGAL_TEXT_VERSION` de E2.1.

| Uso en Play Console | dev | prod |
|---|---|---|
| Política de privacidad (Contenido de la app > Política de privacidad) | https://parcheu-dev.web.app/privacidad | https://parcheu-prod.web.app/privacidad |
| Términos | https://parcheu-dev.web.app/terminos | https://parcheu-prod.web.app/terminos |
| Eliminación de cuenta (Seguridad de los datos > URL para borrar la cuenta) | https://parcheu-dev.web.app/borrar-cuenta | https://parcheu-prod.web.app/borrar-cuenta |

Registro:

| Dato | Valor |
|---|---|
| URL de `/privacidad` de prod (la que se pega en Play) | https://parcheu-prod.web.app/privacidad |
| Estado en prod | _pendiente: se despliega con el tag `v0.1.0`_ |
| Versión del texto | 2026-10-29 |
| Verificada con `curl -I` (200) | _pendiente tras el despliegue de dev y de prod_ |

## 11. Billing mínimo, primera subida y suscripción `parcheu_plus` (E7.0)

Responsable: I3 · Revisor par: I1 · Fecha límite de la subida: **28 oct 2026**. Play solo deja crear suscripciones cuando la app tiene un AAB subido que incluye Play Billing Library; la primera subida de una app nueva se hace **a mano** desde la consola (las siguientes las hace E10.2 por API).

### 11.1 Lo que ya quedó hecho por CLI (22 oct)

- `billing-ktx` 8.3.0 en `android/app` (versión fijada en E0.7; compilada con Kotlin 2.2.10, compatible). Su manifiesto aporta `com.android.vending.BILLING`; la tarea `./gradlew :app:verifyProdReleaseBillingPermission` (también en `check` y en `android-ci`) falla si el manifiesto fusionado de `prodRelease` no lo declara.
- `BillingClientProvider` (`android/app/.../data/billing/`): conecta, reintenta con backoff exponencial (1 s, 2 s, 4 s; máx. 3 reintentos), reconecta si Play corta el servicio y consulta los `ProductDetails` de `parcheu_plus`. Sin UI de compra (E7.2/E7.3).
- **AAB 0.0.1 construido y firmado con la llave de subida**, fuera del repo: `~/.parcheu/parcheu-0.0.1-prod-release.aab` (flavor `prod`, `me.santiagoreina.parcheu`, `versionCode` 1, `versionName` 0.0.1, R8 activo, contiene `com.android.vending.BILLING`). Firma verificada con `jarsigner -verify`: certificado `CN=ParcheU Upload`, SHA-256 `7F:51:20:F3:06:45:12:A5:B4:A5:6C:0D:44:E1:67:2B:18:A4:BA:1F:EA:CB:62:1F:B5:E3:A2:AE:FF:8F:03:D3` (la misma huella de la §2). Sin `google-services.json` de prod (la 0.0.1 no usa Firebase ni backend).
- Para reconstruirlo (por ejemplo si cambia algo antes del 28 oct), desde `android/`:

  ```bash
  set -a && . ~/.parcheu/upload-key.env && set +a
  ./gradlew :app:bundleProdRelease \
    -Pandroid.injected.signing.store.file=$HOME/.parcheu/upload-keystore.jks \
    -Pandroid.injected.signing.store.password="$UPLOAD_KEYSTORE_PASSWORD" \
    -Pandroid.injected.signing.key.alias="$UPLOAD_KEY_ALIAS" \
    -Pandroid.injected.signing.key.password="$UPLOAD_KEY_PASSWORD"
  jarsigner -verify -certs app/build/outputs/bundle/prodRelease/app-prod-release.aab   # debe decir CN=ParcheU Upload
  ```

  **No subas un AAB firmado con la llave de debug** (el `signingConfig` por defecto de `release` hasta E10.2): en una app nueva la llave del primer AAB queda registrada como llave de subida.
- Lo que **no** se puede por CLI: la primera subida de una app nueva, crear la suscripción sin AAB previo, los license testers y la configuración de RTDN (no hay API pública para esos dos últimos). Además `parcheu-prod` aún no existe (22 oct), así que el tema `play-rtdn` de prod tampoco.

### 11.2 Pasos en la consola (I3, 28 oct)

Requisitos: app creada (§4), perfil de pagos verificado (§5) y verificación de identidad aprobada.

1. **Subida a la pista interna.** *Pruebas > Pruebas internas > Crear versión* → sube `~/.parcheu/parcheu-0.0.1-prod-release.aab`. Si Play pide elegir la llave de firma, usa **la llave generada por Google** (Play App Signing). Nombre de la versión: `0.0.1`; notas (es-419): `Primera versión interna: esqueleto y Play Billing.` Agrega como testers internos al menos a los 3 integrantes. *Revisar versión > Iniciar lanzamiento en pruebas internas*. Registra la fecha en la tabla 11.3.
2. **Huellas del certificado de firma de la app** (*Integridad de la app > Firma de apps*): copia SHA-1 y SHA-256 del certificado de **firma de la app** (no el de subida) para Firebase prod (E10.3). Son públicas; regístralas en la tabla 11.3.
3. **Suscripción.** *Monetizar > Productos > Suscripciones > Crear suscripción*: ID de producto `parcheu_plus` (no se puede cambiar después), nombre `ParcheU+`. Dentro, *Agregar plan base*:
   - `mensual`: renovación automática, período **1 mes (P1M)**, precio base **COP 5.000** (*Establecer precios* → Colombia 5.000; deja que Play convierta los demás países o restringe la disponibilidad a Colombia).
   - `anual`: renovación automática, período **1 año (P1Y)**, precio base **COP 45.000**.
   - Sin ofertas ni pruebas gratis por ahora (las decide ADR-0029 antes del 10 nov). Período de gracia y suspensión: valores por defecto.
   - **Activa** ambos planes base (quedan en estado *Activo*). La app los lee con `ProductDetails`: el texto de precio nunca se escribe a mano (arquitectura §9).
4. **License testers.** *Configuración (de la cuenta) > Pruebas de licencia*: agrega ≥ 3 cuentas de Google (los 3 integrantes) con respuesta `RESPOND_NORMALLY`. Registra solo la cantidad.
5. **RTDN.** Requiere que el usuario haya aplicado Terraform de prod (E0.5): el módulo `platform` crea el tema `play-rtdn` y le da `roles/pubsub.publisher` a `google-play-developer-notifications@system.gserviceaccount.com`. En *Monetizar > Configuración de monetización > Notificaciones de desarrolladores en tiempo real*: tema `projects/<ID real de parcheu-prod>/topics/play-rtdn` (ID real en arquitectura §12), tipo de notificación *Suscripciones, compras anuladas y todas las compras únicas*, **Enviar mensaje de prueba** y confirma que llegó:

   ```bash
   gcloud pubsub topics describe play-rtdn --project <ID de prod>   # el tema existe
   gcloud pubsub topics get-iam-policy play-rtdn --project <ID de prod> \
     --format='value(bindings.members)' | grep google-play-developer-notifications
   ```

   Si el `apply` de prod aún no está el 28 oct, crea igual la suscripción y los license testers y deja RTDN para cuando exista el tema. No crees el tema a mano: quedaría fuera del estado de Terraform (si no hay otra salida, créalo con `gcloud pubsub topics create play-rtdn`, da el rol de publicador a la cuenta de Play y luego `terraform import` del tema y del binding).
6. **Captura para el PR de E7.0** (sin correos ni datos personales): la lista de planes base de `parcheu_plus` mostrando `mensual` y `anual` *Activos*, y la pantalla de RTDN con el tema configurado. Recorta o tapa correos de license testers.

### 11.3 Registro de E7.0

| Dato | Valor | Fecha | Quién |
|---|---|---|---|
| AAB 0.0.1 subido a la pista interna | _pendiente (meta 28 oct)_ | | I3 |
| SHA-256 del AAB subido | `44f441274702f8faa3053cf014dc555ca667830f376ea80fffaf9e9ad5f492f7` (construido el 22 oct) | 22 oct | agente |
| Huellas SHA-1 / SHA-256 del certificado de firma de la app (Play) | _pendiente_ | | I3 |
| `parcheu_plus` creada; `mensual` (P1M, COP 5.000) y `anual` (P1Y, COP 45.000) activos | _pendiente_ | | I3 |
| License testers (cantidad, ≥ 3) | _pendiente_ | | I3 |
| RTDN → `projects/<prod>/topics/play-rtdn`, mensaje de prueba recibido | _pendiente_ | | I3 |

### 11.4 Lista de verificación de E7.0

- [x] `billing-ktx` en el AAB; `com.android.vending.BILLING` verificado en el manifiesto fusionado de `prodRelease`.
- [x] `BillingClientProvider` con reintentos, reconexión y consulta de `parcheu_plus` (tests con `FakeBillingClient`).
- [x] AAB 0.0.1 `prod` firmado con la llave de subida, listo en `~/.parcheu/`.
- [ ] AAB 0.0.1 en la pista interna (≤ 28 oct).
- [ ] `parcheu_plus` con `mensual` y `anual` activos.
- [ ] ≥ 3 license testers.
- [ ] RTDN apuntando a `play-rtdn` de prod, mensaje de prueba recibido.
- [ ] Capturas en el PR.

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
| 6 | Pipeline `android-release` que sube a la pista cerrada (si no está, plan B de §12.3 paso 8) | E10.2 | `gh workflow list` |

### 12.3 Proceso de release 0.1 (PLAN §5)

1. **Rama de release** (el 28-29 oct, con todo lo de §12.2 en `dev`):

   ```bash
   git fetch origin && git switch -c release/0.1.0 origin/dev
   ```

2. **Versión.** Hoy `android/app/build.gradle.kts` fija `versionCode = 1` y `versionName = "0.0.1"` (los usó el AAB de
   E7.0). La 0.1 necesita `versionName = "0.1.0"` y un `versionCode` **mayor que 1** (Play rechaza un `versionCode`
   repetido). Si E10.2 ya deriva el `versionCode` del número de ejecución, solo cambia `versionName`; si no, sube
   ambos a mano (`versionCode = 2`). El cambio entra como `fix/version-0.1.0` contra `release/0.1.0`.
3. **Declaraciones y ficha antes del tag.** El tag dispara `deploy-prod` y, encadenado con `workflow_run`,
   `android-release` (E10.2), que **sube el AAB a la pista cerrada** y hace commit de la versión. Play rechaza o envía
   a revisión de inmediato una versión cuando faltan las declaraciones. Por eso, **antes de crear el tag**, completa
   en Play Console: Contenido de la app (§12.5, con §12.6 y §12.7) y la ficha mínima (§12.8). Y escribe las notas de
   §12.8 en `android/app/src/prod/play/release-notes/es-419/default.txt` dentro de `release/0.1.0` (así la subida
   automática no usa las notas por defecto del repo).
4. **Publicación administrada.** En *Descripción general de la publicación* activa la **publicación administrada**
   (o configura la subida de E10.2 en estado `draft`), para que la versión subida por el tag **no** se envíe a
   revisión sola. El envío a revisión es manual (§12.9), el 29 oct.
5. **PR `release/0.1.0` → `main`** con el título `Release 0.1.0`. **Lo aprueba el usuario** (nadie más mezcla a `main`).
6. **Tag** tras el merge: `git tag -a v0.1.0 -m "ParcheU 0.1.0" origin/main && git push origin v0.1.0`.
7. **`deploy-prod`** (environment `production`, aprobación del usuario): en la 0.1 solo corre `firebase-deploy` prod
   (reglas, índices y Hosting; la 0.1 no usa backend). Verifica que las páginas legales respondan:

   ```bash
   for p in privacidad terminos borrar-cuenta; do
     printf '%s ' "$p"; curl -s -o /dev/null -w '%{http_code}\n' "https://parcheu-prod.web.app/$p"
   done   # las tres deben dar 200
   ```

8. **AAB a la pista cerrada.** Camino normal: el tag encadena `android-release` (E10.2), que construye el AAB `prod`
   firmado y lo sube a la pista **cerrada** (con la publicación administrada del paso 4, queda pendiente de envío).
   Si E10.2 está mezclado, `versionName` sale del tag y `fix/version-0.1.0` **no** debe tocar `defaultConfig`.
   Plan B si E10.2 no está mezclado el 29 oct: construir a mano como en §11.1 (llave de subida de `~/.parcheu/`, con
   el `versionCode` > 1 del paso 2) y subirlo en *Pruebas > Prueba cerrada > Crear versión*. Comprueba antes de subir:

   ```bash
   jarsigner -verify -certs app/build/outputs/bundle/prodRelease/app-prod-release.aab | grep 'CN=ParcheU Upload'
   ```

   Al subirlo, Play muestra el `versionCode` y el `versionName` del AAB: deben ser > 1 y `0.1.0` (si tienes
   `bundletool`, también: `bundletool dump manifest --bundle <aab> | grep -oE 'android:version(Code|Name)="[^"]*"'`).

9. **`release/0.1.0` → `dev`** (PR de vuelta) para que el cambio de versión no se pierda.

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
| Política de privacidad | `https://parcheu-prod.web.app/privacidad` | Debe responder 200 antes de enviar (§12.3 paso 7) |
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
2. **Al llegar la aprobación** (correo de Play o *Resumen de la publicación*): con la publicación administrada
   (§12.3 paso 4) la versión aprobada queda **retenida y no instalable**. Ese mismo día pulsa *Resumen de la
   publicación > Publicar cambios* y registra la fecha en la fila 'Publicada en la pista cerrada' de §12.11. Después
   **desactiva la publicación administrada** (*Descripción general de la publicación > Publicación administrada*,
   con la 0.1 ya publicada; no la desactives antes, porque se publicaría lo pendiente sin control) para que la 0.2 y
   la 0.3 no queden retenidas; si prefieres mantenerla, habrá que pulsar 'Publicar cambios' tras cada aprobación.
   Comprueba que la pista cerrada muestre la 0.1.0 como 'Disponible' y solo entonces copia el **enlace de
   inscripción** de *Prueba cerrada > Testers* y envíalo **ese mismo día** al grupo de testers con un mensaje corto: abrir el
   enlace con la cuenta de Google del teléfono, pulsar 'Convertirse en tester', instalar desde Play y **no salirse
   de la prueba durante 14 días**.
3. **Conteo diario** de inscritos: *Prueba cerrada > Testers* muestra cuántos se inscribieron. Anota el conteo del
   día de la publicación (meta ≥ 12, con 15-20 reclutados) y repórtalo cada día en el diario del equipo. Si baja de
   12, el reloj de Play se reinicia: recluta reemplazos el mismo día.
4. **Fecha de los 14 días.** Con `INICIO` = primer día en que la 0.1 está **publicada** en la pista cerrada **y** hay ≥ 12 inscritos (día 1;
   inscribirse sin la versión publicada no cuenta, porque nadie puede instalarla):

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
| **Publicada en la pista cerrada** ('Publicar cambios' tras la aprobación) y publicación administrada desactivada o no | _pendiente_ | | I3 |
| Inscritos el día de la publicación (meta ≥ 12) | _pendiente_ | | I3 |
| **Primer día con la 0.1 publicada y ≥ 12 inscritos** (día 1 del reloj) | _pendiente_ | | I3 |
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
- [ ] Declaraciones (§12.5-§12.8), ficha mínima y notas en `release-notes/es-419/default.txt` listas **antes** del tag.
- [ ] Publicación administrada activada (o subida en `draft`).
- [ ] `v0.1.0` en `main` (aprobado por el usuario) y Hosting prod con las páginas legales en 200.
- [ ] Contenido de la app completo: privacidad, sin anuncios, acceso, IARC, 18+, Data safety (borrador 0.1).
- [ ] Ficha mínima e instrucciones para testers publicadas.
- [ ] 0.1 enviada a revisión el 29 oct.
- [ ] Tras la aprobación: 'Publicar cambios' pulsado, 0.1 disponible en la pista cerrada y publicación administrada desactivada.
- [ ] SHA-1 y SHA-256 de Play App Signing en la app Android de Firebase prod (captura en el PR).
- [ ] Enlace de inscripción enviado el día de la publicación; ≥ 12 inscritos ese día.
- [ ] Fechas de §12.11 completas, incluida la de los 14 días.
- [ ] Instalación desde la pista cerrada abre la app y A4 lleva a `/terminos` y `/privacidad` de prod.

## 13. Usuarios demo en la prueba cerrada (E4.9, ADR-0027)

Durante la prueba cerrada la fila de match tiene **20 perfiles de demostración** para que no esté vacía. Son
ficticios, se declaran en todas partes y se borran antes de la 1.0 (`purge-demo`, E10.9). Nunca aparecen en Pareja
y nunca escriben mensajes.

**Texto para las instrucciones de la prueba** (Play Console > Pruebas > Prueba cerrada > *Detalles de la prueba* /
correo de bienvenida al grupo de testers; es-419):

> Para que siempre haya alguien con quien probar, ParcheU incluye perfiles de demostración. No son personas reales:
> llevan la insignia "Perfil de demostración" y su nombre termina en "(demo)". Solo aparecen en Amistad y Estudio,
> nunca en Pareja. Si conectas con uno, el chat muestra "Esta es una cuenta de demostración: no responde mensajes";
> lo que tú escribas ahí sigue tus ajustes de privacidad normales (tu gemelo solo aprende de tus propios mensajes si
> lo permitiste). Todos los perfiles de demostración se borran antes del lanzamiento público.

| Paso | Cuándo | Comando o lugar |
|---|---|---|
| Pegar el texto de arriba en las instrucciones de la prueba y en el correo al grupo | Antes de enviar la 0.2 a revisión | Play Console |
| Encender la bandera en prod durante la prueba cerrada | Con el `apply` de prod de la 0.2 | `terraform apply -var demo_users_enabled=true` (`infra/terraform/envs/prod`) |
| Sembrar los demo | Tras el `apply` y el despliegue de la 0.2; repetir cada día de la prueba para devolverlos a la fila | `gcloud run jobs execute seed-demo --region us-central1 --project parcheu-prod --wait` |
| Apagar la bandera y purgar | Antes de enviar la 1.0 (E10.9) | `terraform apply -var demo_users_enabled=false` y luego `gcloud run jobs execute purge-demo --region us-central1 --project parcheu-prod --wait` |

