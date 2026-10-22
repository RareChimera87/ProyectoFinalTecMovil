# ADR-0021: Login con Credential Manager y Firebase Auth

- **Estado:** Aceptada · 22 oct 2026
- **Features:** E2.2, E8.2

## Contexto
El requisito es login con Google y Firebase Auth. La API antigua `GoogleSignInClient` (play-services-auth) está obsoleta para inicio de sesión.

## Decisión
- **Credential Manager** (`androidx.credentials` + `credentials-play-services-auth`) con `GetGoogleIdOption` y el client id web del proyecto Firebase → `GoogleAuthProvider.getCredential(idToken)` → `FirebaseAuth.signInWithCredential` → `POST /v1/session`.
- Cerrar sesión: `FirebaseAuth.signOut()` + `CredentialManager.clearCredentialState()` + limpieza local (E8.2).
- SHA-1 y SHA-256 de debug, de subida y de **Play App Signing** registrados en cada app de Firebase (la de prod se valida con la 0.2 instalada desde Play).

## Alternativas consideradas
- **GoogleSignInClient:** obsoleto; los agentes encontrarían ejemplos viejos y mezclarían APIs.
- **FirebaseUI Auth:** agrega pantallas que no coinciden con A4 y más dependencias.

## Consecuencias
- Una sola ruta de login, sin contraseñas que guardar.
- El fallo más común (SHA de App Signing sin registrar) solo aparece al instalar desde Play: por eso la verificación está en E10.3 y E10.8.
