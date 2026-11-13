package me.santiagoreina.parcheu.feature.paywall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlin.reflect.KClass
import me.santiagoreina.parcheu.contract.QuotaKind
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.billing.FakeBillingRepository
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileSamples
import me.santiagoreina.parcheu.feature.basicprofile.FakeBasicProfileRepository
import me.santiagoreina.parcheu.feature.plus.PlusSources
import me.santiagoreina.parcheu.feature.plus.PlusTags
import me.santiagoreina.parcheu.feature.plus.PlusTestData
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.Paywall
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La hoja G2 decide entre gratis y uso justo con el `plus` que confirma el servidor (`GET /v1/me`), no con
 * el derecho en memoria de la capa de compras, que está vacío hasta abrir G1 o G4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
class PaywallRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private object NoopNavigator : AppNavigator {
        override fun navigate(route: AppRoute) = Unit
        override fun navigateReplacing(route: AppRoute, replacing: KClass<out AppRoute>) = Unit
        override fun navigateToTopLevel(route: AppRoute) = Unit
        override fun replaceAll(route: AppRoute) = Unit
        override fun back(): Boolean = true
        override fun canGoBack(): Boolean = true
    }

    private val limitRoute = Paywall(PlusSources.limit(QuotaKind.SIMULATION))

    private fun open(billing: FakeBillingRepository, profile: FakeBasicProfileRepository) {
        composeRule.setContent {
            ParcheUTheme(darkTheme = true) {
                PaywallRoute(
                    route = limitRoute,
                    navigator = NoopNavigator,
                    billing = billing,
                    profile = profile
                )
            }
        }
    }

    @Test
    fun aPlusPersonConfirmedByTheServerSeesFairUseEvenWithoutBillingInMemory() {
        // Nadie abrió G1 ni G4: el derecho en memoria sigue en null.
        val billing = FakeBillingRepository()
        val profile =
            FakeBasicProfileRepository(
                meResult = ApiResult.Success(BasicProfileSamples.newUser.copy(plus = true))
            )
        open(billing, profile)
        composeRule.onNodeWithText("Llegaste al uso justo de hoy").assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.LIMIT_SEE_PLUS).assertDoesNotExist()
    }

    @Test
    fun aFreePersonSeesTheFreeSheetEvenIfStaleBillingSaysPlus() {
        val billing = FakeBillingRepository().apply {
            entitlementFlow.value =
                PlusTestData.activeAnnual
        }
        val profile =
            FakeBasicProfileRepository(meResult = ApiResult.Success(BasicProfileSamples.newUser))
        open(billing, profile)
        composeRule.onNodeWithText("Ya usaste tus simulaciones de esta semana").assertIsDisplayed()
        composeRule.onNodeWithTag(PlusTags.LIMIT_SEE_PLUS).assertIsDisplayed()
    }

    @Test
    fun withoutServerAnswerItFallsBackToTheBillingEntitlement() {
        val billing = FakeBillingRepository().apply {
            entitlementFlow.value =
                PlusTestData.activeAnnual
        }
        val profile = FakeBasicProfileRepository(meResult = ApiResult.Failure(AppError.Network))
        open(billing, profile)
        composeRule.onNodeWithText("Llegaste al uso justo de hoy").assertIsDisplayed()
    }
}
