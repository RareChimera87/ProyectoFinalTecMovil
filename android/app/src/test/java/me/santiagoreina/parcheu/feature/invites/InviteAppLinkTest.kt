package me.santiagoreina.parcheu.feature.invites

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import me.santiagoreina.parcheu.MainActivity
import me.santiagoreina.parcheu.ParcheUApp
import me.santiagoreina.parcheu.ParcheUApplication
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.Home
import me.santiagoreina.parcheu.push.DeepLinks
import me.santiagoreina.parcheu.push.PendingDeepLink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * E6.1: el intent `https://<hosting>/i/ABCD-1234` lo atiende MainActivity (filtro App Link del
 * manifiesto fusionado), se traduce a `AddFriends("ABCD-1234")` y abre C5 con el código precargado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InviteAppLinkTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val link = "https://${InviteLinks.defaultHost}/i/ABCD-1234"

    @Before
    fun fakeApi() {
        ApplicationProvider.getApplicationContext<ParcheUApplication>()
        loadKoinModules(module { single<InvitesRepository> { FakeInvitesRepository() } })
    }

    @After
    fun clear() {
        PendingDeepLink.route.value?.let(PendingDeepLink::consume)
    }

    @Test
    fun theManifestRoutesTheInviteLinkToMainActivity() {
        val context = ApplicationProvider.getApplicationContext<ParcheUApplication>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        assertTrue(
            "MainActivity debe atender $link",
            handlers.any { it.activityInfo.name == MainActivity::class.java.name }
        )
        // Otro camino del mismo sitio (p. ej. /privacidad) no abre la app.
        val legal = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://${InviteLinks.defaultHost}/privacidad")
        )
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(legal, 0).isEmpty())
    }

    @Test
    fun theIntentOpensC5WithTheCodePreloaded() {
        assertEquals(AddFriends(code = "ABCD-1234"), DeepLinks.fromUri(link))
        assertNull(DeepLinks.fromUri("https://evil.example/i/ABCD-1234"))

        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            ParcheUApp(navController = navController, startDestination = Home)
        }
        // Lo mismo que hace MainActivity.offerDeepLink con un ACTION_VIEW.
        PendingDeepLink.offer(
            DeepLinks.fromUri(Intent(Intent.ACTION_VIEW, Uri.parse(link)).dataString)
        )
        composeRule.waitForIdle()

        assertEquals(
            AddFriends(code = "ABCD-1234"),
            navController.currentBackStackEntry!!.toRoute<AddFriends>()
        )
        composeRule.onNodeWithTag(InvitesTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(InvitesTags.INPUT).assertTextContains("ABCD-1234")
    }
}
