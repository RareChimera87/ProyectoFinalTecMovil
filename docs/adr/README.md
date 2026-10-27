# Registro de decisiones de arquitectura (ADR)

Formato: contexto, decisión, alternativas, consecuencias. Una decisión nueva o un cambio de una existente se registra con un ADR nuevo (no se reescriben los aceptados; se marcan como reemplazados).

| ADR | Decisión |
|---|---|
| [0001](0001-monorepo-y-git-flow.md) | Monorepo y git flow con auto-merge a dev |
| [0002](0002-backend-ktor-un-servicio.md) | Un solo servicio Ktor en Cloud Run |
| [0003](0003-postgres-fuente-de-verdad.md) | Postgres como única fuente de verdad, sin pgvector ni CTE recursivas |
| [0004](0004-firestore-canal-solo-lectura.md) | Firestore como canal de tiempo real de solo lectura |
| [0005](0005-android-koin-datastore-sin-ksp.md) | Koin y DataStore, sin KSP ni kapt |
| [0006](0006-navegacion-y-esqueleto-android.md) | Navigation Compose 2.9 tipado y esqueleto con todo declarado |
| [0007](0007-grafo-canvas-layout-radial.md) | Grafo en Canvas con layout radial determinista |
| [0008](0008-ia-vertex-proveedores-intercambiables.md) | IA en Vertex con proveedores intercambiables |
| [0009](0009-simulacion-agentes-y-juez.md) | Dos agentes independientes y un juez con rúbrica |
| [0010](0010-control-de-costo-ia.md) | Control de costo de la IA |
| [0011](0011-migraciones-flyway-cloud-run-job.md) | Flyway en Cloud Run Job, versiones por fecha |
| [0012](0012-privacidad-pareja.md) | Privacidad de Pareja: policy, SQL y pruebas por propiedades |
| [0013](0013-billing-servidor-rtdn.md) | Billing con verificación en servidor y RTDN |
| [0014](0014-dos-proyectos-terraform-wif.md) | Dos proyectos GCP con Terraform y WIF |
| [0015](0015-app-check-diferido.md) | App Check diferido |
| [0016](0016-prueba-cerrada-temprana.md) | Prueba cerrada temprana |
| [0017](0017-consentimiento-ley-1581.md) | Consentimiento por modo (Ley 1581) |
| [0018](0018-qr-google-code-scanner.md) | QR con ZXing y Google Code Scanner |
| [0019](0019-cloud-tasks-trabajo-asincrono.md) | Cloud Tasks para el trabajo asíncrono (tres colas) |
| [0020](0020-region-y-transferencia-internacional.md) | Región us-central1 y transferencia internacional (Ley 1581) |
| [0021](0021-login-credential-manager.md) | Login con Credential Manager y Firebase Auth |
| [0022](0022-estrategia-de-pruebas-android.md) | Pruebas Android con Robolectric y Roborazzi; comparación humana con mockups |
| [0023](0023-afinidad-estimada-amigos-de-amigos.md) | Afinidad estimada para amigos de amigos |
| [0024](0024-comunidades-propagacion-de-etiquetas.md) | Comunidades con propagación de etiquetas |
| [0025](0025-borrado-purga-y-retencion.md) | Borrado lógico, purga y retención |
| [0026](0026-moderacion-ia-fail-open.md) | Moderación con IA asíncrona y fail-open |
| [0027](0027-usuarios-demo.md) | Usuarios demo honestos y acotados |
| [0028](0028-firebase-hosting-y-admin.md) | Firebase Hosting para legales, App Links y admin.html |
| [0029](0029-precio-anual-y-ahorro.md) | Texto de ahorro del plan anual (pendiente del usuario) |
| [0030](0030-contraste-tema-claro.md) | Ajustes de contraste AA en el tema claro |
| [0031](0031-edad-y-perfil-en-onboarding.md) | Fecha de nacimiento para todos y perfil básico (B0) |
| [0032](0032-memorias-por-modo.md) | Memorias del gemelo aisladas por modo |
| [0033](0033-backend-en-homeserver.md) | Backend de pruebas en el homeserver en vez de Cloud SQL + Cloud Run |

Enmiendas del 22 oct 2026 (ronda de corrección): 0001 (merge y releases intermedios), 0005 y 0008 (fecha límite de los spikes), 0012 (memorias), 0016 (billing, login y tiempos de Play), 0017 (términos, política y aviso de IA).
