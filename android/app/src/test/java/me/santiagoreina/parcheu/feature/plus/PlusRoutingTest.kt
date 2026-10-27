package me.santiagoreina.parcheu.feature.plus

import kotlin.time.Instant
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada error premium llega a la pantalla correcta (criterio de E7.3, junto con PlusNavigationTest). */
class PlusRoutingTest {
    @Test
    fun plusRequiredOpensG1WithItsSource() {
        val route = PlusErrorRouter.route(AppError.PlusRequired, "fof")
        assertEquals(Paywall("fof"), route)
        assertEquals(PlusEntry.Paywall("fof"), PlusSources.parse((route as Paywall).source))
    }

    @Test
    fun quotaExceededOpensTheLimitSheetOfEachKind() {
        QuotaKind.entries.forEach { kind ->
            val route =
                PlusErrorRouter.route(AppError.QuotaExceeded(kind, null), "home") as Paywall
            assertEquals(PlusEntry.Limit(kind, null), PlusSources.parse(route.source))
        }
    }

    @Test
    fun quotaExceededCarriesWhenTheQuotaRenews() {
        val resets = Instant.parse("2026-11-09T05:00:00Z")
        val route =
            PlusErrorRouter.route(
                AppError.QuotaExceeded(QuotaKind.SIMULATION, resets),
                "home"
            ) as Paywall
        assertEquals(PlusEntry.Limit(QuotaKind.SIMULATION, resets), PlusSources.parse(route.source))
    }

    @Test
    fun quotaExceededWithoutKindFallsBackToG1() {
        val route = PlusErrorRouter.route(AppError.QuotaExceeded(null, null), "home")
        assertEquals(Paywall("home"), route)
    }

    @Test
    fun nonPremiumErrorsDoNotOpenAnything() {
        listOf(
            AppError.Network,
            AppError.Unauthorized,
            AppError.Forbidden,
            AppError.Blocked,
            AppError.NotFound,
            AppError.Unknown(500)
        ).forEach {
            assertNull(PlusErrorRouter.route(it, "x"))
            assertFalse(PlusErrorRouter.isPremium(it))
        }
        assertTrue(PlusErrorRouter.isPremium(AppError.PlusRequired))
    }

    @Test
    fun unknownOrBrokenLimitSourcesOpenG1() {
        assertEquals(PlusEntry.Paywall("limit:NOPE:-"), PlusSources.parse("limit:NOPE:-"))
        assertEquals(PlusEntry.Paywall("home"), PlusSources.parse("home"))
        assertEquals(
            PlusEntry.Limit(QuotaKind.FULL_REPORT, null),
            PlusSources.parse("limit:FULL_REPORT:basura")
        )
    }
}
