package me.santiagoreina.parcheu.api.platform.auth

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

/** Verificador OIDC Fake: solo acepta [VALID_TOKEN] y lo atribuye a la cuenta de tareas. */
class FakeOidcVerifier(private val tasksAccount: String) : OidcVerifier {
    override suspend fun verify(token: String): ServiceCaller? =
        if (token == VALID_TOKEN) ServiceCaller(tasksAccount) else null

    companion object {
        const val VALID_TOKEN = "oidc-tasks-fake"
    }
}
