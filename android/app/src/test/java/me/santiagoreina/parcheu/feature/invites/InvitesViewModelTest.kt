package me.santiagoreina.parcheu.feature.invites

import me.santiagoreina.parcheu.contract.Mode
import me.santiagoreina.parcheu.contract.PersonRef
import me.santiagoreina.parcheu.contract.RedeemResult
import me.santiagoreina.parcheu.contract.RedeemStatus
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InvitesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val host = "parcheu-dev.web.app"

    private fun vm(repo: FakeInvitesRepository = FakeInvitesRepository(), code: String? = null) =
        InvitesViewModel(repo, code, { "Mariana Gómez" }, host)

    @Test
    fun loadsMyCodeWithItsQr() {
        val vm = vm()
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals("MARI-4827", state.invite?.code)
        assertNotNull(state.qr)
        assertEquals("Mariana Gómez", state.name)
    }

    @Test
    fun aFailedLoadCanBeRetried() {
        val repo = FakeInvitesRepository(invite = FakeInvitesRepository.networkFailure)
        val vm = vm(repo)
        assertEquals(AppError.Network, vm.state.value.loadError)
        repo.invite = ApiResult.Success(FakeInvitesRepository.sampleInvite())
        vm.load()
        assertNull(vm.state.value.loadError)
        assertEquals("MARI-4827", vm.state.value.invite?.code)
    }

    @Test
    fun theAppLinkCodeArrivesPreloaded() {
        assertEquals("ABCD-1234", vm(code = "abcd-1234").state.value.codeInput)
        assertEquals("", vm().state.value.codeInput)
    }

    @Test
    fun scanningOurInviteLinkPreloadsTheCode() {
        val vm = vm()
        val scanner = FakeCodeScanner(ScanResult.Scanned("https://parcheu-dev.web.app/i/SEBA-1290"))
        vm.onScan(scanner)
        assertEquals(1, scanner.scans)
        assertEquals("SEBA-1290", vm.state.value.codeInput)
        assertFalse(vm.state.value.message!!.isError)
    }

    @Test
    fun scanningAForeignQrSaysItIsNotFromParcheU() {
        val vm = vm()
        vm.onCodeChange("SEBA-1290")
        vm.onScan(FakeCodeScanner(ScanResult.Scanned("https://www.nequi.com.co/pagar/123")))
        assertEquals(
            InviteMessage("Este código no es de ParcheU", isError = true),
            vm.state.value.message
        )
        // El código que ya estaba escrito no se pierde.
        assertEquals("SEBA-1290", vm.state.value.codeInput)

        vm.onScan(FakeCodeScanner(ScanResult.Scanned("https://parcheu-prod.web.app/i/SEBA-1290")))
        assertEquals("Este código no es de ParcheU", vm.state.value.message?.text)
    }

    @Test
    fun cancellingTheScannerChangesNothingAndUnavailableExplains() {
        val vm = vm()
        vm.onScan(FakeCodeScanner(ScanResult.Cancelled))
        assertNull(vm.state.value.message)
        vm.onScan(FakeCodeScanner(ScanResult.Unavailable))
        assertTrue(vm.state.value.message!!.isError)
    }

    @Test
    fun addRedeemsTheCodeInTheChosenMode() {
        val repo = FakeInvitesRepository()
        val vm = vm(repo)
        vm.onCodeChange("seba-1290 ")
        vm.onMode(Mode.ESTUDIO)
        vm.onAdd()
        assertEquals(listOf("SEBA-1290" to Mode.ESTUDIO), repo.redeemed)
        val state = vm.state.value
        assertEquals("", state.codeInput)
        assertFalse(state.message!!.isError)
        assertTrue(state.message!!.text.contains("Sebastián"))
        assertTrue(state.message!!.text.contains("Estudio"))
        // No promete un aviso: si el dueño no tiene el modo, el servidor no le avisa.
        assertFalse(state.message!!.text.contains("avisamos"))
    }

    @Test
    fun addValidatesLocallyBeforeCallingTheApi() {
        val repo = FakeInvitesRepository()
        val vm = vm(repo)
        vm.onCodeChange("SEBA12")
        vm.onAdd()
        assertTrue(vm.state.value.message!!.isError)
        vm.onCodeChange("MARI-4827")
        vm.onAdd()
        assertTrue(vm.state.value.message!!.text.contains("propio código"))
        assertTrue(repo.redeemed.isEmpty())
    }

    @Test
    fun redeemErrorsAreExplained() {
        val repo = FakeInvitesRepository(redeemResult = ApiResult.Failure(AppError.Blocked))
        val vm = vm(repo)
        vm.onCodeChange("SEBA-1290")
        vm.onAdd()
        assertEquals("No puedes agregar a esta persona.", vm.state.value.message?.text)
        assertEquals("SEBA-1290", vm.state.value.codeInput)

        repo.redeemResult = ApiResult.Failure(AppError.NotFound)
        vm.onAdd()
        assertTrue(vm.state.value.message!!.text.startsWith("Ese código no existe"))

        repo.redeemResult =
            ApiResult.Failure(
                AppError.Validation("Activa el modo Pareja en tu perfil para conectar en ese modo")
            )
        vm.onAdd()
        assertEquals(
            "Activa el modo Pareja en tu perfil para conectar en ese modo",
            vm.state.value.message?.text
        )
    }

    @Test
    fun alreadyConnectedIsReported() {
        val repo = FakeInvitesRepository(
            redeemResult = ApiResult.Success(
                RedeemResult(
                    RedeemStatus.CONNECTED,
                    PersonRef(id = "u", label = "Sebastián", initials = "S")
                )
            )
        )
        val vm = vm(repo)
        vm.onCodeChange("SEBA-1290")
        vm.onAdd()
        assertTrue(vm.state.value.message!!.text.startsWith("Ya estás conectado con Sebastián"))
    }

    @Test
    fun acceptingAPendingInviteRemovesIt() {
        val pending = FakeInvitesRepository.pendingLaura
        val repo =
            FakeInvitesRepository(
                invite = ApiResult.Success(FakeInvitesRepository.sampleInvite(listOf(pending)))
            )
        val vm = vm(repo)
        assertEquals(listOf(pending), vm.state.value.pending)
        vm.onAccept(pending)
        assertEquals(listOf("a_laura:ESTUDIO" to Mode.ESTUDIO), repo.accepted)
        assertTrue(vm.state.value.pending.isEmpty())
        assertNull(vm.state.value.accepting)
        assertTrue(vm.state.value.message!!.text.contains("Laura Pérez"))
    }

    @Test
    fun acceptingOneModeKeepsTheOtherModesOfThatPersonPending() {
        val estudio = FakeInvitesRepository.pendingLaura
        val pareja = FakeInvitesRepository.pendingLauraPareja
        val repo = FakeInvitesRepository(
            invite = ApiResult.Success(FakeInvitesRepository.sampleInvite(listOf(pareja, estudio)))
        )
        val vm = vm(repo)
        vm.onAccept(estudio)
        // Solo se pide aceptar Estudio, y Pareja sigue esperando su propio "Aceptar".
        assertEquals(listOf(estudio.id to Mode.ESTUDIO), repo.accepted)
        assertEquals(listOf(pareja), vm.state.value.pending)
        assertTrue(vm.state.value.message!!.text.endsWith("en Estudio."))

        vm.onAccept(pareja)
        assertEquals(pareja.id to Mode.PAREJA, repo.accepted.last())
        assertTrue(vm.state.value.pending.isEmpty())
    }

    @Test
    fun theDailyRedeemLimitIsExplained() {
        val repo = FakeInvitesRepository(redeemResult = ApiResult.Failure(AppError.Forbidden))
        val vm = vm(repo)
        vm.onCodeChange("SEBA-1290")
        vm.onAdd()
        assertEquals(
            "Llegaste al límite de códigos por hoy. Inténtalo de nuevo mañana.",
            vm.state.value.message?.text
        )
    }

    @Test
    fun aVanishedPendingInviteIsDropped() {
        val pending = FakeInvitesRepository.pendingLaura
        val repo = FakeInvitesRepository(
            invite = ApiResult.Success(FakeInvitesRepository.sampleInvite(listOf(pending))),
            acceptResult = ApiResult.Failure(AppError.NotFound)
        )
        val vm = vm(repo)
        vm.onAccept(pending)
        assertTrue(vm.state.value.pending.isEmpty())
        assertTrue(vm.state.value.message!!.isError)
    }

    @Test
    fun theShareTextCarriesCodeAndLink() {
        val text = shareText("MARI-4827", "https://parcheu-dev.web.app/i/MARI-4827")
        assertTrue(text.contains("MARI-4827"))
        assertTrue(text.contains("https://parcheu-dev.web.app/i/MARI-4827"))
    }
}
