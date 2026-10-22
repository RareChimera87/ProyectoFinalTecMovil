package me.santiagoreina.parcheu.feature.basicprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.contract.University
import me.santiagoreina.parcheu.core.error.AppError
import me.santiagoreina.parcheu.designsystem.components.base.InterestChip
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUChildTopBar
import me.santiagoreina.parcheu.designsystem.components.base.ParcheURadioRow
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUTextField
import me.santiagoreina.parcheu.designsystem.components.base.PrimaryButton
import me.santiagoreina.parcheu.designsystem.components.compound.ErrorState
import me.santiagoreina.parcheu.designsystem.components.compound.InfoNote
import me.santiagoreina.parcheu.designsystem.components.compound.LoadingDots
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Etiquetas de prueba de B0. */
object BasicProfileTags {
    const val SCREEN = "basicprofile"
    const val NAME = "basicprofile:name"
    const val BIRTH_DATE = "basicprofile:birthdate"
    const val UNIVERSITY = "basicprofile:university"
    const val CAREER = "basicprofile:career"
    const val INTERESTS = "basicprofile:interests"
    const val SUBMIT = "basicprofile:submit"
    const val ERROR = "basicprofile:error"
    const val LOADING = "basicprofile:loading"

    fun university(id: Int) = "basicprofile:university:$id"

    fun interest(slug: String) = "basicprofile:interest:$slug"
}

/** Mensaje de un fallo al guardar o cargar B0. */
fun basicProfileErrorMessage(error: AppError): String = when (error) {
    AppError.Network -> "No hay conexión. Revisa tu internet e inténtalo de nuevo."
    AppError.Conflict -> "Tu fecha de nacimiento ya estaba registrada y no se puede cambiar."
    is AppError.Validation -> "Revisa los datos: ${error.message}"
    AppError.Unauthorized -> "Tu sesión expiró. Vuelve a iniciar sesión."
    AppError.TermsRequired -> "Debes aceptar los Términos vigentes para continuar."
    else -> "Algo salió mal. Inténtalo de nuevo."
}

/**
 * B0 'Tu perfil' (E2.6): pantalla nueva sin mockup, hecha con el sistema de diseño (ADR-0031). Los diálogos
 * de fecha y universidad los abre [BasicProfileRoute] con [onPickBirthDate] y [onPickUniversity]; esta
 * función solo dibuja el estado, así las capturas son deterministas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BasicProfileScreen(
    state: BasicProfileUiState,
    onPickBirthDate: () -> Unit,
    onPickUniversity: () -> Unit,
    onCareerChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onInterestToggle: (String) -> Unit,
    onSubmit: () -> Unit,
    onRetryLoad: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    animateLoading: Boolean = true,
) {
    val colors = ParcheUTheme.colors
    Column(modifier.fillMaxSize().background(colors.bg).testTag(BasicProfileTags.SCREEN)) {
        val edit = (state as? BasicProfileUiState.Form)?.edit == true
        if (edit) {
            ParcheUChildTopBar(
                title = "Editar perfil",
                navigationIcon = rememberVectorPainter(ArrowBack),
                navigationContentDescription = "Volver",
                onNavigationClick = onBack,
            )
        }
        when (state) {
            BasicProfileUiState.Loading ->
                Box(Modifier.fillMaxSize().testTag(BasicProfileTags.LOADING), contentAlignment = Alignment.Center) {
                    LoadingDots(animated = animateLoading)
                }
            is BasicProfileUiState.LoadError ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        title = "No pudimos cargar tu perfil",
                        message = basicProfileErrorMessage(state.error),
                        retryLabel = "Reintentar",
                        onRetryClick = onRetryLoad,
                    )
                }
            is BasicProfileUiState.Form ->
                FormContent(
                    state = state,
                    onPickBirthDate = onPickBirthDate,
                    onPickUniversity = onPickUniversity,
                    onCareerChange = onCareerChange,
                    onDisplayNameChange = onDisplayNameChange,
                    onInterestToggle = onInterestToggle,
                    onSubmit = onSubmit,
                )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormContent(
    state: BasicProfileUiState.Form,
    onPickBirthDate: () -> Unit,
    onPickUniversity: () -> Unit,
    onCareerChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onInterestToggle: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = ParcheUTheme.colors
    val spacing = ParcheUTheme.spacing
    val form = state.form
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutterWide),
            verticalArrangement = Arrangement.spacedBy(spacing.blockGap),
        ) {
            if (!state.edit) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Tu perfil",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.tx,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "En un match al azar la otra persona solo ve tu rango de edad, tu universidad y tus " +
                        "intereses. Nunca tu nombre ni tu fecha exacta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mu,
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            if (form.askName) {
                ParcheUTextField(
                    value = form.displayName,
                    onValueChange = onDisplayNameChange,
                    label = "Tu nombre",
                    supportingText = "Así te verán tus amigos en ParcheU.",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.testTag(BasicProfileTags.NAME),
                )
            }

            PickerField(
                label = "Fecha de nacimiento",
                value = form.birthDate?.let(BirthDates::display).orEmpty(),
                placeholder = "Elige tu fecha",
                supportingText = if (form.birthDateLocked) {
                    "Ya está verificada y no se puede cambiar."
                } else {
                    "ParcheU es solo para mayores de 18. No podrás cambiarla después."
                },
                enabled = !form.birthDateLocked,
                onClick = onPickBirthDate,
                modifier = Modifier.testTag(BasicProfileTags.BIRTH_DATE),
            )

            val university = state.catalog.universities.firstOrNull { it.id == form.universityId }
            PickerField(
                label = "Universidad",
                value = university?.shortName.orEmpty(),
                placeholder = "Elige tu universidad",
                supportingText = university?.name?.takeIf { it != university.shortName },
                enabled = true,
                onClick = onPickUniversity,
                modifier = Modifier.testTag(BasicProfileTags.UNIVERSITY),
            )

            ParcheUTextField(
                value = form.career,
                onValueChange = onCareerChange,
                label = "Carrera (opcional)",
                placeholder = "Ej.: Ingeniería Industrial",
                supportingText = if (form.careerTooLong) {
                    "Máximo ${BasicProfileLimits.MAX_CAREER} caracteres (${form.career.trim().length})"
                } else {
                    "${form.career.trim().length}/${BasicProfileLimits.MAX_CAREER}"
                },
                isError = form.careerTooLong,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.testTag(BasicProfileTags.CAREER),
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Intereses",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.tx,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Text(
                    text = "${form.interests.size}/${BasicProfileLimits.MAX_INTERESTS}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mu,
                )
            }
            Text(
                text = "Elige de ${BasicProfileLimits.MIN_INTERESTS} a ${BasicProfileLimits.MAX_INTERESTS}.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mu,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag(BasicProfileTags.INTERESTS),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                val full = form.interests.size >= BasicProfileLimits.MAX_INTERESTS
                state.catalog.interests.forEach { interest ->
                    val selected = interest.slug in form.interests
                    InterestChip(
                        label = interest.label,
                        selected = selected,
                        onSelectedChange = { onInterestToggle(interest.slug) },
                        enabled = selected || !full,
                        modifier = Modifier.testTag(BasicProfileTags.interest(interest.slug)),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(ParcheUTheme.spacing.footer),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let { error ->
                Text(
                    text = basicProfileErrorMessage(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BasicProfileTags.ERROR)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (!state.edit && !form.birthDateLocked) {
                InfoNote(text = "Tu fecha de nacimiento se verifica una sola vez y no la ve nadie más.")
            }
            PrimaryButton(
                text = when {
                    state.saving -> "Guardando…"
                    state.edit -> "Guardar"
                    else -> "Continuar"
                },
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth().testTag(BasicProfileTags.SUBMIT),
            )
        }
    }
}

/** Campo de solo lectura que abre un selector (fecha o universidad) al tocarlo. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    placeholder: String,
    supportingText: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        ParcheUTextField(
            value = value,
            onValueChange = {},
            label = label,
            placeholder = placeholder,
            supportingText = supportingText,
            enabled = enabled,
        )
        // Capa encima del campo: toma el toque (sin teclado) y lo anuncia como botón.
        Box(
            Modifier
                .matchParentSize()
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        )
    }
}

/** Lista cerrada de universidades para la hoja de selección. */
@Composable
fun UniversityOptions(
    universities: List<University>,
    selectedId: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            text = "Tu universidad",
            style = MaterialTheme.typography.titleLarge,
            color = ParcheUTheme.colors.tx,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        universities.forEach { university ->
            ParcheURadioRow(
                label = university.shortName,
                supportingText = university.name.takeIf { it != university.shortName },
                selected = university.id == selectedId,
                onClick = { onSelect(university.id) },
                modifier = Modifier.testTag(BasicProfileTags.university(university.id)),
            )
        }
    }
}

/** Flecha atrás de Material Symbols (Apache 2.0): el set de iconos de E1.2 aún no está en dev. */
internal val ArrowBack: ImageVector by lazy {
    ImageVector.Builder("ArrowBack", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(20f, 11f)
            horizontalLineTo(7.83f)
            lineToRelative(5.59f, -5.59f)
            lineTo(12f, 4f)
            lineToRelative(-8f, 8f)
            lineToRelative(8f, 8f)
            lineToRelative(1.41f, -1.41f)
            lineTo(7.83f, 13f)
            horizontalLineTo(20f)
            close()
        }
    }.build()
}
