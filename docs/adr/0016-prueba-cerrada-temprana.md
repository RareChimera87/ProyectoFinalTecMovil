# ADR-0016: Prueba cerrada temprana (29 oct) y fechas de Play

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E10.1, E10.3, E10.8, E10.9

## Contexto
Las cuentas personales de Play creadas después de noviembre de 2023 deben tener una prueba cerrada con al menos 12 testers inscritos durante 14 días seguidos antes de pedir acceso a producción. Si la prueba cerrada empieza el 9 nov, los 14 días se cumplen el 23 nov y la revisión deja la publicación en riesgo.

## Decisión
- Verificar el tipo y la fecha de la cuenta el 22 oct.
- Subir una versión 0.1 a prueba cerrada el **29 oct** con 15-20 testers (ver la enmienda: sin login y con Play Billing de E7.0).
- 0.2 con el ciclo completo el 9 nov (fecha de la materia); solicitud de acceso a producción cuando se cumplan los 14 días; 1.0 el 24-25 nov con despliegue escalonado.

## Alternativas consideradas
- Esperar al 9 nov: sin margen para la revisión.
- Cuenta de organización: no exige la regla, pero requiere D-U-N-S y verificación que no caben en el plazo.

## Consecuencias
- La 0.1 es muy básica; los testers deben saberlo (mensaje en la ficha de prueba).
- Hay que mantener a los 12+ testers inscritos todo el periodo (seguimiento diario).

## Enmienda (22 oct 2026, ronda de corrección)
- **Billing:** Play solo deja crear suscripciones si hay un AAB subido con Play Billing. Una feature nueva, **E7.0**, agrega `billing-ktx` y el 28 oct I3 sube **a mano** el AAB 0.0.1 a la pista interna (la primera subida de una app nueva es manual); ese día se crea `parcheu_plus`. E7.2 (cliente) pasa al sprint 2 y la compra con license tester se verifica con la **0.3 del 13 nov**.
- **Login:** la 0.1 no incluye login (E2.2 llega en la ola 6); trae el esqueleto, Billing y los enlaces a términos y política. El login desde una instalación de Play se verifica con la 0.2.
- **Reloj de 14 días:** corre desde que Play aprueba la versión y los testers se inscriben, no desde la subida. Con aprobación estimada el 30-31 oct, los 14 días se cumplen hacia el 14 nov y ese día se pide el acceso a producción.
- **Producción:** la respuesta al acceso puede tardar ~7 días y la revisión de la primera versión de producción otros tantos. Por eso la 0.9 (20 nov) se envía a revisión de producción apenas llega el acceso, con publicación administrada (verificar), y la 1.0 entra como actualización el 24-25 nov. Si no alcanza, se sustenta con la app instalada desde la prueba cerrada.

## Actualización (E10.7, 28 oct 2026): prioridad del usuario
- El 27 oct el usuario fijó como prioridad una app completa y funcional en el flavor `dev` y dejó la publicación en Play (testers, prueba cerrada, acceso a producción) fuera de alcance por ahora. Esta decisión sigue siendo el plan de publicación cuando se retome; el estado real y los pasos humanos viven en [pendientes-humanos.md](../pendientes-humanos.md) y [play-console.md](../play-console.md), y [sustentacion.md](../sustentacion.md#8-estado-honesto-y-preguntas-esperadas) obliga a declarar el estado antes de sustentar.
