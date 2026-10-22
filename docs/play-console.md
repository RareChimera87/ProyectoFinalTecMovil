# Play Console

> Este archivo lo crea E8.4 solo con la sección de URLs legales. E10.1 (`feature/play-console-setup`) agrega
> el registro de la cuenta; al mezclar ambos, conservar todas las secciones.

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
