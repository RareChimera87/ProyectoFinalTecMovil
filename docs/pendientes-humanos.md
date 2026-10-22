# Pendientes humanos

Pasos que solo puede hacer una persona (consolas, pagos, términos, identidad). Cada feature agrega su sección.

## E10.1 · Configuración de Play Console

Guía completa y registro de estado en [play-console.md](play-console.md).

| # | Paso | Dueño | Fecha límite |
|---|---|---|---|
| 1 | Confirmar tipo de cuenta de desarrollador y fecha de creación; registrarlos en `play-console.md` | I3 | 24 oct |
| 2 | Iniciar verificación de identidad (y de dispositivo si se pide) | I3 | 22 oct |
| 3 | Crear la app `me.santiagoreina.parcheu` (es-419, Social, gratis con compras) y confirmar Play App Signing | I3 | 24 oct |
| 4 | Crear el perfil de pagos y completar su verificación | I3 | 25 oct |
| 5 | Respaldar `~/.parcheu/upload-keystore.jks` y cargar los 4 secretos `UPLOAD_*` en GitHub (comandos en `play-console.md` §6) | I3 / usuario | 25 oct |
| 6 | Crear `sa-play` en GCP prod, invitarla a Play Console con permisos mínimos y cargar `PLAY_SERVICE_ACCOUNT_JSON` (§7) | I3 | 27 oct |
| 7 | Crear el Google Group y confirmar ≥ 15 correos de testers | I3 | 26-28 oct |
| 8 | Agregar ≥ 3 license testers | I3 | 28 oct |

Nota: la llave de subida ya está generada localmente; falta cargarla a GitHub porque el entorno automatizado no tiene permiso para escribir secretos.
