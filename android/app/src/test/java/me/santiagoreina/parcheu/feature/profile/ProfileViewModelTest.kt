package me.santiagoreina.parcheu.feature.profile

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import me.santiagoreina.parcheu.contract.TwinPoint
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.core.network.ApiResult
import me.santiagoreina.parcheu.feature.MainDispatcherRule
import me.santiagoreina.parcheu.feature.basicprofile.BasicProfileSamples
import me.santiagoreina.parcheu.navigation.AddFriends
import me.santiagoreina.parcheu.navigation.BasicProfile
import me.santiagoreina.parcheu.navigation.ManageSubscription
import me.santiagoreina.parcheu.navigation.PrivacySettings
import me.santiagoreina.parcheu.navigation.TrainTwin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun loaded(vm: ProfileViewModel) = vm.state.value as ProfileUiState.Loaded

    @Test
    fun interestSlugsAreMappedToCatalogLabels() = runTest {
        val vm = ProfileViewModel(FakeProfileRepository())
        vm.load()
        assertEquals(listOf("Música", "Cine", "Café"), loaded(vm).interestLabels)
    }

    @Test
    fun interestsFallBackToSlugWhenCatalogFailsOrSlugIsUnknown() = runTest {
        val failing =
            ProfileViewModel(
                FakeProfileRepository(
                    catalogResult = ApiResult.Failure(AppError.Network)
                )
            )
        failing.load()
        assertEquals(listOf("musica", "cine", "cafe"), loaded(failing).interestLabels)
        assertEquals(
            listOf("Música", "dibujo"),
            interestLabels(listOf("musica", "dibujo"), BasicProfileSamples.catalog.interests)
        )
    }

    @Test
    fun lineLabelsUseMonthsOfFirstAndLastPointWithData() {
        val points =
            listOf(
                TwinPoint("2026-08", 40, 2),
                TwinPoint("2026-08", null, 2),
                TwinPoint("2026-11", 61, 3)
            )
        val line = chartOf(points) as TwinChart.Line
        assertEquals("ago 2026", line.startLabel)
        assertEquals("nov 2026", line.endLabel)
        assertEquals("Primer registro", monthLabel("raro", "Primer registro"))
    }

    @Test
    fun zeroPointsShowEmptyState() = runTest {
        val vm = ProfileViewModel(FakeProfileRepository())
        vm.load()
        assertEquals(TwinChart.Empty, loaded(vm).chart)
    }

    @Test
    fun onePointShowsSingleValueWithoutLine() = runTest {
        val vm =
            ProfileViewModel(
                FakeProfileRepository(evolutionResult = ApiResult.Success(ProfileSamples.one))
            )
        vm.load()
        assertEquals(TwinChart.Single(42), loaded(vm).chart)
    }

    @Test
    fun threePointsShowLine() = runTest {
        val vm =
            ProfileViewModel(
                FakeProfileRepository(evolutionResult = ApiResult.Success(ProfileSamples.three))
            )
        vm.load()
        assertEquals(TwinChart.Line(listOf(40, 52, 61), "ene 2026", "ene 2026"), loaded(vm).chart)
    }

    @Test
    fun pointsWithoutAccuracyAreIgnored() {
        val points = ProfileSamples.three.map { it.copy(accuracy = null) }
        assertEquals(TwinChart.Empty, chartOf(points))
    }

    @Test
    fun evolutionFailureIsNotPresentedAsLearning() = runTest {
        val vm =
            ProfileViewModel(
                FakeProfileRepository(evolutionResult = ApiResult.Failure(AppError.Network))
            )
        vm.load()
        assertEquals(TwinChart.Unavailable, loaded(vm).chart)
    }

    @Test
    fun resolvesUniversityName() = runTest {
        val vm = ProfileViewModel(FakeProfileRepository())
        vm.load()
        assertEquals("Universidad Industrial de Santander", loaded(vm).universityName)
    }

    @Test
    fun missingTwinFailsTheScreenAndRetryRecovers() = runTest {
        val repo = FakeProfileRepository(twinResult = ApiResult.Failure(AppError.Network))
        val vm = ProfileViewModel(repo)
        vm.load()
        assertEquals(ProfileUiState.Error(AppError.Network), vm.state.value)
        repo.twinResult = ApiResult.Success(ProfileSamples.twin)
        vm.load()
        assertTrue(vm.state.value is ProfileUiState.Loaded)
    }

    @Test
    fun refreshFailureKeepsLoadedContent() = runTest {
        val repo = FakeProfileRepository()
        val vm = ProfileViewModel(repo)
        vm.load()
        repo.meResult = ApiResult.Failure(AppError.Network)
        vm.load()
        assertTrue(vm.state.value is ProfileUiState.Loaded)
    }

    @Test
    fun entriesNavigateToTheirScreens() = runTest {
        val vm = ProfileViewModel(FakeProfileRepository())
        vm.onEditProfile()
        vm.onTrainTwin()
        vm.onSettings()
        vm.onSubscription()
        vm.onAddFriends()
        val routes =
            List(5) { (vm.effects.first() as ProfileEffect.Navigate).route }
        // 'Editar perfil' reutiliza B0 en modo edición (fecha bloqueada y PATCH /v1/me en B0).
        assertEquals(
            listOf(
                BasicProfile(edit = true),
                TrainTwin,
                PrivacySettings,
                ManageSubscription,
                AddFriends()
            ),
            routes
        )
    }
}
