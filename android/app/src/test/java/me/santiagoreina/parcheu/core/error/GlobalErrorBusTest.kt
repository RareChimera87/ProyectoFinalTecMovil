package me.santiagoreina.parcheu.core.error

import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.QuotaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** arquitectura.md §2.3: qué errores llegan al bus global y cuándo se descartan. */
class GlobalErrorBusTest {
    private var signedIn = true
    private val time = TestTimeSource()
    private val bus = GlobalErrorBus(hasSession = { signedIn }, timeSource = time)

    @Test
    fun publishesSessionTermsPlusAndQuotaErrors() = runTest {
        val resetsAt = Instant.fromEpochSeconds(1_800_000_000)
        bus.errors.test {
            bus.publish(AppError.Unauthorized)
            assertEquals(GlobalError.SessionExpired, awaitItem())
            bus.publish(AppError.Suspended)
            assertEquals(GlobalError.AccountSuspended, awaitItem())
            bus.publish(AppError.TermsRequired)
            assertEquals(GlobalError.TermsRequired, awaitItem())
            bus.publish(AppError.PlusRequired)
            assertEquals(GlobalError.PlusRequired, awaitItem())
            bus.publish(AppError.QuotaExceeded(QuotaKind.SIMULATION, resetsAt))
            assertEquals(GlobalError.QuotaExceeded(QuotaKind.SIMULATION, resetsAt), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun screenErrorsAreNotPublished() = runTest {
        bus.errors.test {
            val local =
                listOf(
                    AppError.Network,
                    AppError.Blocked,
                    AppError.Underage,
                    AppError.NotFound,
                    AppError.Validation("x"),
                    AppError.Unknown(500)
                )
            local.forEach { assertFalse(bus.publish(it)) }
            expectNoEvents()
        }
    }

    @Test
    fun repeatedErrorsWithinTheWindowArePublishedOnce() = runTest {
        bus.errors.test {
            // Al vencer la sesión fallan a la vez todas las llamadas en curso: se navega una sola vez.
            assertTrue(bus.publish(AppError.Unauthorized))
            assertFalse(bus.publish(AppError.Unauthorized))
            time += 1.seconds
            assertFalse(bus.publish(AppError.Unauthorized))
            assertEquals(GlobalError.SessionExpired, awaitItem())
            expectNoEvents()

            // Pasada la ventana el mismo error vuelve a publicarse; uno distinto, siempre.
            time += GlobalErrorBus.DEDUPE_WINDOW
            assertTrue(bus.publish(AppError.Unauthorized))
            assertTrue(bus.publish(AppError.PlusRequired))
            assertEquals(GlobalError.SessionExpired, awaitItem())
            assertEquals(GlobalError.PlusRequired, awaitItem())
        }
    }

    @Test
    fun unauthorizedWithoutSessionIsIgnored() = runTest {
        // Splash, A4 o justo después de cerrar sesión: el 401 es esperado y no hay nada que cerrar.
        signedIn = false
        bus.errors.test {
            assertFalse(bus.publish(AppError.Unauthorized))
            expectNoEvents()
        }
    }

    @Test
    fun premiumErrorsAreSkippedWhenTheCallOptsOut() = runTest {
        bus.errors.test {
            assertFalse(bus.publish(AppError.PlusRequired, routesPremium = false))
            assertFalse(bus.publish(AppError.QuotaExceeded(null, null), routesPremium = false))
            // La sesión se atiende siempre, aunque la llamada maneje sus 402.
            assertTrue(bus.publish(AppError.Unauthorized, routesPremium = false))
            assertEquals(GlobalError.SessionExpired, awaitItem())
        }
    }
}
