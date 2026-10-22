# ADR-0031: Fecha de nacimiento para todos y perfil básico en el onboarding

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E2.6, E2.5, E4.2, E4.4, E9.1

## Contexto
La app se publica para 18+ (público objetivo en Play), pero la fecha de nacimiento solo se pedía al activar Pareja (B3). F2 muestra 'Alguien de la UIS · 20-22 años' e intereses, y C3 muestra carrera y universidad, sin ninguna pantalla que capture esos datos.

## Decisión
- Pantalla nueva **B0 'Tu perfil'** entre A4 y B1: fecha de nacimiento (obligatoria para todos, validada en el servidor con zona America/Bogota e inmutable), universidad (lista cerrada + 'Otra'), carrera e intereses (1 a 5 de un catálogo).
- Menor de 18 → la cuenta queda bloqueada (`underage_at`) con una pantalla que lo explica y permite borrarla.
- B3 deja de pedir la fecha: muestra la verificada y pide la confirmación explícita para Pareja (`pareja_confirmed_at`).
- `AnonProfile` usa rango de edad (18-19, 20-22, 23-25, 26+), universidad e intereses; nunca nombre, foto, carrera ni id.
- C3 permite editar todo menos la fecha.

## Alternativas consideradas
- **Pedir la fecha solo para Pareja:** deja sin control de edad a quien usa Amistad o Estudio en una app 18+.
- **Inferir universidad por el dominio del correo:** la mayoría entra con Gmail personal.

## Consecuencias
- Un paso más en el onboarding (A4 → B0 → B1); se mitiga con un formulario corto.
- F2 y C3 tienen fuente de datos real y la verificación de edad es uniforme.
