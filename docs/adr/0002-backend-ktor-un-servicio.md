# ADR-0002: Un solo servicio Ktor en Cloud Run

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E0.3

## Contexto
El backend debe ser preferiblemente Kotlin, usar los créditos de GCP y desplegarse por CI con infraestructura como código. El tráfico esperado es de cientos de usuarios.

## Decisión
Un solo servicio Ktor 3 (Netty, JVM 21) en Cloud Run, organizado en paquetes por dominio, con DI manual por constructores y la imagen construida con Jib. `min-instances=0` en dev y `1` en prod. El mismo contenedor sirve para el Cloud Run Job `migrate`.

## Alternativas consideradas
- **Microservicios:** latencia, costo y más unidades que desplegar y probar, sin beneficio a esta escala.
- **Cloud Functions / solo Firebase:** TypeScript u otro runtime, sin transacciones relacionales cómodas.
- **Render (free):** se duerme, fuera de la red de GCP, sin WIF hacia Vertex.
- **Spring Boot:** más pesado y con arranque más lento; Ktor comparte estilo con la app.
- **Dockerfile:** el usuario no está en el grupo `docker`; Jib no necesita daemon.

## Consecuencias
- Mismo lenguaje y mismo contrato que la app.
- La JVM tiene arranque en frío: se mitiga con una instancia mínima en prod y migraciones fuera del arranque.
- DI manual exige disciplina para no crear dependencias circulares; se compensa con legibilidad.
