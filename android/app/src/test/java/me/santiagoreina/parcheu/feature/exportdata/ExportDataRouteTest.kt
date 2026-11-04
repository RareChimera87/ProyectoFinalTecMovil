package me.santiagoreina.parcheu.feature.exportdata

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import me.santiagoreina.parcheu.data.account.FakeAccountRepository
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.AppRoute
import me.santiagoreina.parcheu.navigation.ExportData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Criterio de E8.3 (UI con Fakes): 'Descargar mis datos' pide el JSON y lanza el intent de crear documento
 * (`ACTION_CREATE_DOCUMENT`, `application/json`) con el nombre `parcheu-datos-<fecha>.json`; al elegir el
 * destino, el JSON se escribe ahí. El registro de resultados es de prueba: captura el intent real que arma
 * el contrato y responde como lo haría el selector del sistema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportDataRouteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private object NoopNavigator : AppNavigator {
        override fun navigate(route: AppRoute) = Unit
        override fun navigateToTopLevel(route: AppRoute) = Unit
        override fun replaceAll(route: AppRoute) = Unit
        override fun back(): Boolean = true
        override fun canGoBack(): Boolean = true
    }

    private class PickerRegistry(private val picked: Uri?) : ActivityResultRegistry() {
        val launched = mutableListOf<Intent>()

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            launched += contract.createIntent(ApplicationProvider.getApplicationContext(), input)
            val data = picked?.let { Intent().setData(it) }
            dispatchResult(
                requestCode,
                if (picked !=
                    null
                ) {
                    Activity.RESULT_OK
                } else {
                    Activity.RESULT_CANCELED
                },
                data
            )
        }
    }

    private fun show(registry: ActivityResultRegistry, viewModel: ExportDataViewModel) {
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry get() = registry
            }
        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ParcheUTheme { ExportDataRoute(ExportData, NoopNavigator, viewModel = viewModel) }
            }
        }
    }

    @Test
    fun downloadRequestsTheJsonAndLaunchesCreateDocumentWithTheDatedName() {
        val account = FakeAccountRepository()
        val files = FakeExportFileStore()
        val picked = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Fx.json"
        )
        val registry = PickerRegistry(picked)
        show(registry, ExportDataViewModel(account, files) { "2026-10-25" })

        composeRule.onNodeWithTag(ExportDataTags.DOWNLOAD).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("export"), account.calls)
        val intent = registry.launched.single()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/json", intent.type)
        assertEquals("parcheu-datos-2026-10-25.json", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals(FakeAccountRepository.SAMPLE_EXPORT, files.written[picked.toString()])
        composeRule.onNodeWithText(
            "Listo: guardamos tus datos donde elegiste."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancellingThePickerWritesNothing() {
        val files = FakeExportFileStore()
        val registry = PickerRegistry(picked = null)
        show(registry, ExportDataViewModel(FakeAccountRepository(), files) { "2026-10-25" })

        composeRule.onNodeWithTag(ExportDataTags.DOWNLOAD).performClick()
        composeRule.waitForIdle()

        assertEquals(1, registry.launched.size)
        assertEquals(emptyMap<String, String>(), files.written)
        composeRule.onNodeWithText(
            "No se guardó nada: cancelaste antes de elegir dónde."
        ).performScrollTo().assertIsDisplayed()
    }
}
