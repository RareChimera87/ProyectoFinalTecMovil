# Desarrollo local

Guía para preparar una máquina de desarrollo de ParcheU. Las decisiones de fondo están en [arquitectura.md](arquitectura.md) y [ADR-0001](adr/0001-monorepo-y-git-flow.md).

## Toolchains de Java

| Parte | JDK | Notas |
|---|---|---|
| `android/` | 17 | `JAVA_HOME` por defecto de la máquina |
| `contract/` | 17 | toolchain 17 |
| `backend/` | 21 | toolchain 21, resuelto por Gradle |

- Instala **JDK 17** y úsalo como `JAVA_HOME`.
- El **JBR 21** de Android Studio (`/opt/android-studio/jbr` en Linux) sirve como JDK 21 del backend. Para que Gradle lo encuentre sin intervención, agrega a `~/.gradle/gradle.properties`:

  ```properties
  org.gradle.java.installations.paths=/opt/android-studio/jbr
  ```

- Gradle también puede descargar toolchains con el plugin foojay (`foojay-resolver-convention`) si hay red.
- Memoria: `org.gradle.jvmargs=-Xmx2g` y un solo build de Gradle a la vez en máquinas con poca RAM o disco.
- El SDK de Android se declara en `android/local.properties` (`sdk.dir=...`); ese archivo **no se versiona**.

## Comandos habituales

```bash
# Android
cd android && ./gradlew :app:testDebugUnitTest

# Backend (cuando exista, E0.3): sin contenedores en local
./gradlew -p backend test -PsinContenedores

# Validador de commits
scripts/test/commit-lint.test.sh
```

## Testcontainers solo en CI

Los tests de backend que necesitan Postgres usan Testcontainers y **solo corren en CI**. En local no se requiere Docker: pasa `-PsinContenedores` para omitirlos (los demás tests usan fakes como `FakeLlmClient` o `FakeTokenVerifier`). Si quieres correrlos localmente, agrega tu usuario al grupo `docker` y vuelve a iniciar sesión.

## google-services.json

`google-services.json` **no se versiona** (está en `.gitignore`). Para obtenerlo:

1. Pide acceso al proyecto Firebase de dev a I1 o I3.
2. Descárgalo desde Firebase Console (Configuración del proyecto, app Android `dev`) o con `scripts/fetch-google-services` cuando exista (E0.8).
3. Colócalo en `android/app/src/dev/google-services.json` (y `src/prod/` para prod).

Hasta que exista, la app compila solo las partes que no dependen de Firebase.

## Secretos

- Nunca en el repo. Los valores locales viven fuera del árbol, p. ej. en `~/.parcheu/`.
- En CI: secretos de GitHub (`gh secret set`). En GCP: Secret Manager, con autenticación WIF.
- Keystores (`*.jks`, `*.keystore`), credenciales de Play y `.env*` están ignorados.

## Commits y ramas

1. Crea una rama `feature/<kebab>` desde `dev` (el nombre está en la ficha del backlog).
2. Instala el hook: `scripts/instalar-hooks.sh`.
3. Escribe commits en español e imperativo con un verbo de la lista blanca `scripts/verbos-imperativo.txt`. Si falta un verbo legítimo, agrégalo en el mismo PR.
4. Abre un PR contra `dev` con la plantilla; el título sigue la misma regla (se valida en `pr-title`).

Reglas del formato: mayúscula inicial, sin punto final, máximo 72 caracteres y primer verbo en la lista blanca. Se admite un prefijo de feature opcional (`E0.1 Agrega ...`).
