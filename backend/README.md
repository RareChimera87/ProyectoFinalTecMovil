# backend

Servicio Ktor 3 (Netty, JVM 21) con Gradle independiente que consume `../contract` con `includeBuild`.

```bash
cd backend
./gradlew build -PsinContenedores   # compila y corre los tests sin Docker
./gradlew run                       # modo serve en :8080 con autenticación Fake (ENV=local)
./gradlew jibBuildTar               # imagen en build/jib-image.tar, sin daemon de Docker
```

Estructura: `src/main/kotlin/me/santiagoreina/parcheu/api/` (`Application.kt` fijo; `platform/` con
config, auth, errores, logs y base de datos; un paquete por dominio con su `XRoutes.kt`).
Detalle y variables de entorno en [docs/desarrollo-local.md](../docs/desarrollo-local.md).
