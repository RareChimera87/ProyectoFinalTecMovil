# ADR-0028: Firebase Hosting para páginas legales, App Links y admin.html

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.8, E8.4, E6.1, E8.5

## Contexto
Play exige URL de política de privacidad y de borrado de cuenta; los App Links necesitan `/.well-known/assetlinks.json`; el equipo necesita una herramienta mínima para resolver reportes.

## Decisión
- **Firebase Hosting** por proyecto sirve `/privacidad`, `/terminos`, `/borrar-cuenta`, `/i/{codigo}` (respaldo web de invitaciones), `assetlinks.json` y `admin.html` (estática, Firebase Auth JS, llama a `/v1/admin/*` con el claim `admin` en el mismo origen: rewrite `/v1/**` → Cloud Run `api`; CSP estricta y SRI en el SDK).
- Lo despliega el workflow `firebase-deploy` (E0.8): dev en cada push a `dev` que toca `firebase/**`; prod con el tag `v*` y la aprobación del environment `production`.
- El claim `admin` solo se asigna con `scripts/grant-admin.sh`.

## Alternativas consideradas
- **Páginas servidas por Ktor:** mezcla contenido estático con la API y obliga a desplegar la API por un cambio de texto.
- **Panel de administración en la app:** más pantallas y más superficie de ataque.

## Consecuencias
- Hosting gratuito con TLS y dominio `*.web.app`; los textos legales tienen su propio ciclo de despliegue.
- `admin.html` es mínima; la autorización real está en la API.
