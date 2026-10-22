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
