package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.theme.ParcheURadius
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/** Variantes de campo del inventario: fecha (56, radio 14), código (52 → 56 por área táctil, radio 16) y área de texto (96). */
enum class ParcheUTextFieldStyle(val minHeight: Dp, val radius: Dp, val singleLine: Boolean) {
    Default(56.dp, ParcheURadius.Field, true),
    Code(56.dp, ParcheURadius.FieldLarge, true),
    TextArea(96.dp, ParcheURadius.FieldLarge, false),
}

/**
 * Campo de texto con borde. El foco, el cursor y la etiqueta enfocada usan `prt`, no `primary`: el violeta
 * puro no llega a AA como texto.
 */
@Composable
fun ParcheUTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    style: ParcheUTextFieldStyle = ParcheUTextFieldStyle.Default,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = style.minHeight),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = style.singleLine,
        minLines = if (style.singleLine) 1 else 3,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(style.radius),
        colors = parcheUTextFieldColors(),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
    )
}

@Composable
private fun parcheUTextFieldColors(): TextFieldColors {
    val colors = ParcheUTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.tx,
        unfocusedTextColor = colors.tx,
        disabledTextColor = colors.tx.disabled(),
        errorTextColor = colors.tx,
        focusedContainerColor = colors.s2,
        unfocusedContainerColor = colors.s2,
        disabledContainerColor = colors.s2,
        errorContainerColor = colors.s2,
        cursorColor = colors.prt,
        errorCursorColor = colors.error,
        focusedBorderColor = colors.prt,
        unfocusedBorderColor = colors.ln,
        disabledBorderColor = colors.ln.copy(alpha = 0.5f),
        errorBorderColor = colors.error,
        focusedLabelColor = colors.prt,
        unfocusedLabelColor = colors.mu,
        disabledLabelColor = colors.mu.disabled(),
        errorLabelColor = colors.error,
        focusedPlaceholderColor = colors.mu,
        unfocusedPlaceholderColor = colors.mu,
        disabledPlaceholderColor = colors.mu.disabled(),
        errorPlaceholderColor = colors.mu,
        focusedSupportingTextColor = colors.mu,
        unfocusedSupportingTextColor = colors.mu,
        disabledSupportingTextColor = colors.mu.disabled(),
        errorSupportingTextColor = colors.error,
    )
}
