package me.santiagoreina.parcheu.feature.basicprofile

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.santiagoreina.parcheu.designsystem.components.compound.ParcheUBottomSheet
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme
import me.santiagoreina.parcheu.navigation.AppNavigator
import me.santiagoreina.parcheu.navigation.BasicProfile
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * B0 'Tu perfil' (E2.6). [BasicProfile.edit] = false en el onboarding (A4 → B0 → B1) y true cuando C3 lo
 * reutiliza para editar (E9.1). Aquí viven los diálogos: selector de fecha de Material 3 y hoja de
 * universidades.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BasicProfileRoute(
    route: BasicProfile,
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
    viewModel: BasicProfileViewModel = koinViewModel(key = "basicProfile:${route.edit}") { parametersOf(route.edit) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    var universitySheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BasicProfileEffect.ReplaceAll -> navigator.replaceAll(effect.route)
                BasicProfileEffect.Back -> navigator.back()
            }
        }
    }

    BasicProfileScreen(
        state = state,
        onPickBirthDate = { datePickerOpen = true },
        onPickUniversity = { universitySheetOpen = true },
        onCareerChange = viewModel::onCareerChange,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onInterestToggle = viewModel::onInterestToggle,
        onSubmit = viewModel::onSubmit,
        onRetryLoad = viewModel::onRetryLoad,
        onBack = { navigator.back() },
        modifier = modifier,
    )

    val form = (state as? BasicProfileUiState.Form)
    if (datePickerOpen && form != null && !form.form.birthDateLocked) {
        BirthDatePickerDialog(
            initialMillis = form.form.birthDate?.let(BirthDates::toUtcMillis),
            onConfirm = { millis ->
                datePickerOpen = false
                if (millis != null) viewModel.onBirthDateSelected(BirthDates.fromUtcMillis(millis))
            },
            onDismiss = { datePickerOpen = false },
        )
    }
    if (universitySheetOpen && form != null) {
        ParcheUBottomSheet(onDismissRequest = { universitySheetOpen = false }) {
            UniversityOptions(
                universities = form.catalog.universities,
                selectedId = form.form.universityId,
                onSelect = {
                    viewModel.onUniversitySelected(it)
                    universitySheetOpen = false
                },
            )
        }
    }
}

/** Selector de fecha: no deja elegir días futuros y abre en un año de alguien de 18 a 20 años. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePickerDialog(initialMillis: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val pickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayedMonthMillis = initialMillis ?: BirthDates.yearsAgoUtcMillis(now, years = 19),
            yearRange = 1900..BirthDates.fromUtcMillis(now).year,
            selectableDates =
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= now
            },
        )
    val colors = ParcheUTheme.colors
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.selectedDateMillis) }) { Text("Aceptar", color = colors.prt) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.prt) }
        },
    ) {
        DatePicker(state = pickerState, title = { Text("Fecha de nacimiento", modifier = Modifier) })
    }
}
