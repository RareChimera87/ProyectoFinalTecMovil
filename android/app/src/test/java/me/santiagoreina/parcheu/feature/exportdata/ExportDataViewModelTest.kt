package me.santiagoreina.parcheu.feature.exportdata

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.data.account.FakeAccountRepository
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportDataViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val account = FakeAccountRepository()
    private val files = FakeExportFileStore()

    private fun viewModel() = ExportDataViewModel(account, files) { "2026-10-25" }

    @Test
    fun theFileNameIsParcheuDatosWithTheDate() {
        assertEquals("parcheu-datos-2026-10-25.json", exportFileName("2026-10-25"))
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(todayInBogota()))
    }

    @Test
    fun downloadFetchesTheJsonAndAsksWhereToSaveIt() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onDownload()
            assertEquals(
                ExportDataEffect.CreateDocument("parcheu-datos-2026-10-25.json"),
                awaitItem()
            )
        }
        assertEquals(listOf("export"), account.calls)
        vm.onDocumentCreated("content://docs/1")
        assertEquals(FakeAccountRepository.SAMPLE_EXPORT, files.written["content://docs/1"])
        assertEquals(ExportOutcome.SAVED, vm.state.value.outcome)
    }

    @Test
    fun cancellingThePickerSavesNothing() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onDownload()
            awaitItem()
        }
        vm.onDocumentCreated(null)
        assertTrue(files.written.isEmpty())
        assertEquals(ExportOutcome.CANCELLED, vm.state.value.outcome)
    }

    @Test
    fun aWriteFailureIsShown() = runTest {
        files.writeOk = false
        val vm = viewModel()
        vm.effects.test {
            vm.onDownload()
            awaitItem()
        }
        vm.onDocumentCreated("content://docs/1")
        assertEquals(ExportOutcome.WRITE_FAILED, vm.state.value.outcome)
    }

    @Test
    fun shareOpensTheSystemSheetWithTheFile() = runTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onShare()
            val effect = awaitItem() as ExportDataEffect.Share
            assertEquals("parcheu-datos-2026-10-25.json", effect.fileName)
            assertTrue(effect.uri.endsWith("/parcheu-datos-2026-10-25.json"))
        }
        assertEquals(
            listOf("parcheu-datos-2026-10-25.json" to FakeAccountRepository.SAMPLE_EXPORT),
            files.shared
        )
    }

    @Test
    fun anApiErrorIsShownAndNothingIsLaunched() = runTest {
        account.exportResult = ApiResult.Failure(AppError.Network)
        val vm = viewModel()
        vm.effects.test {
            vm.onDownload()
            expectNoEvents()
        }
        assertEquals(AppError.Network, vm.state.value.error)
        assertNull(vm.state.value.loading)
    }
}
