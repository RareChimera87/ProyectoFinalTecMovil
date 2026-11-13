package me.santiagoreina.parcheu.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.santiagoreina.parcheu.designsystem.components.base.ParcheUButtonSize
import me.santiagoreina.parcheu.designsystem.theme.ParcheUTheme

/**
 * Botón de una acción destructiva (borrar la cuenta, bloquear): dirección A, las acciones que no se
 * deshacen van en `error`. Relleno `error` con `onError` encima (par verificado en ContrastTest), mismo
 * alto y radio que `PrimaryButton`; deshabilitado se ve como los demás botones apagados.
 */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = ParcheUTheme.colors
    val shape = RoundedCornerShape(ParcheUButtonSize.Large.radius)
    Box(
        modifier
            .heightIn(min = ParcheUButtonSize.Large.height)
            .clip(shape)
            .background(if (enabled) colors.error else colors.tx.copy(alpha = 0.12f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onError else colors.tx.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
