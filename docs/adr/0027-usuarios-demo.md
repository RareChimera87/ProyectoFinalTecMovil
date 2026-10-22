# ADR-0027: Usuarios demo honestos y acotados

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E4.9, E4.2, E4.4, E4.6, E5.1, E10.3, E10.9

## Contexto
Con pocos testers, la fila de match está vacía y la demo de la sustentación se ve pobre. Pero cuentas que parecen personas reales en una app de conexión, y más en Pareja, contradicen el tono honesto y pueden verse como engañosas en la revisión de Play.

## Decisión
- 20 usuarios `is_demo` solo en dev y durante la prueba cerrada (`DEMO_USERS_ENABLED`), purgados antes de la 1.0.
- **Nunca en Pareja.**
- Insignia 'Perfil de demostración' en F2, D2, resultados y chat; banner fijo en el chat: 'Esta es una cuenta de demostración: no responde mensajes'. Los demo nunca escriben.
- Declarados en las instrucciones de la prueba cerrada y en `docs/play-console.md`.
- Los mensajes del tester a un demo siguen las reglas normales de consentimiento (solo sus propios mensajes entrenan a su gemelo).

## Alternativas consideradas
- **Demo sin rotular:** engañoso.
- **Sin demo:** fila vacía; se mantienen además el QR para el curso y la fila de 24 h.

## Consecuencias
- La demo funciona sin engañar a nadie; en producción no existen.
