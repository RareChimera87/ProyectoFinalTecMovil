# ADR-0018: QR con ZXing para generar y Google Code Scanner para leer

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E6.1

## Contexto
C5 permite agregar amigos por QR y por código `XXXX-0000`. La pestaña 'Escanear' no está dibujada.

## Decisión
- Generar el QR con ZXing core (sin UI).
- Leer con Google Code Scanner (Play services): la UI de cámara la provee el sistema y **no requiere permiso de cámara**.
- El QR codifica el App Link `https://<hosting>/i/{codigo}` con `assetlinks.json` en Firebase Hosting, para que también funcione desde la cámara del teléfono.

## Alternativas consideradas
- CameraX + ML Kit: permiso de cámara, más pantallas y más código.

## Consecuencias
- Menos permisos declarados en Data safety.
- Depende de Google Play services (presente en el público objetivo).
