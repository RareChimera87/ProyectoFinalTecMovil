package me.santiagoreina.parcheu.api.platform.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Verificador Fake para pruebas y desarrollo local (`ENV=local`). Acepta `fake-<uid>`
 * (p. ej. `fake-ana`) y rechaza cualquier otro valor; los uid de [adminUids] llevan el claim `admin`.
 * Nunca se cablea en dev ni en prod.
 */
class FakeTokenVerifier(private val adminUids: Set<String> = emptySet()) : TokenVerifier {
    override suspend fun verify(token: String): FirebaseUser? {
        if (!token.startsWith(PREFIX) || token.length == PREFIX.length) return null
        val uid = token.removePrefix(PREFIX)
        return FirebaseUser(
            uid = uid,
            email = "$uid@fake.parcheu.test",
            name = uid,
            admin = uid in adminUids,
            isTest = uid.startsWith(TEST_UID_PREFIX),
        )
    }

    companion object {
        const val PREFIX = "fake-"

        /** Los uid que empiezan así se tratan como cuentas de prueba (`is_test`), como el claim del token real. */
        const val TEST_UID_PREFIX = "e2e-"

        fun tokenFor(uid: String) = "$PREFIX$uid"
    }
}

/**
 * Verificador OIDC Fake: solo acepta [validToken] y lo atribuye a la cuenta de tareas. No hay token fijo: el
 * cableado de `ENV=local` usa `FAKE_OIDC_TOKEN` si está definido o, si no, [randomToken] (uno nuevo por arranque,
 * que se escribe en el registro). La cola y el programador en proceso llaman a los handlers directamente, sin token.
 */
class FakeOidcVerifier(private val tasksAccount: String, val validToken: String) : OidcVerifier {
    init {
        require(validToken.length >= MIN_LENGTH) { "El token OIDC Fake debe tener al menos $MIN_LENGTH caracteres" }
    }

    override suspend fun verify(token: String): ServiceCaller? =
        if (MessageDigest.isEqual(token.toByteArray(), validToken.toByteArray())) ServiceCaller(tasksAccount) else null

    companion object {
        /** El valor fijo de antes de la auditoría del 28 oct (B-A2); se rechaza siempre aunque alguien lo configure. */
        const val LEGACY_TOKEN = "oidc-tasks-fake"

        const val MIN_LENGTH = 16

        /** 32 bytes aleatorios en hexadecimal. */
        fun randomToken(): String {
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            return bytes.joinToString("") { "%02x".format(it) }
        }

        /** `FAKE_OIDC_TOKEN` si está definido (pruebas y scripts); si no, uno aleatorio. */
        fun tokenFromEnv(vars: Map<String, String>): String {
            val fixed = vars["FAKE_OIDC_TOKEN"]?.trim()?.takeIf { it.isNotEmpty() } ?: return randomToken()
            require(fixed != LEGACY_TOKEN) { "FAKE_OIDC_TOKEN no puede ser el token fijo antiguo ($LEGACY_TOKEN)" }
            return fixed
        }
    }
}
