package me.santiagoreina.parcheu.designsystem.components.base

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Área táctil mínima de Material y de WCAG: todo elemento interactivo mide al menos 48 x 48 dp. */
internal val MinTouchTarget = 48.dp

/**
 * Garantiza el área táctil de 48dp. Se aplica ANTES del `clickable`/`selectable` para que el nodo de
 * semántica mida 48dp aunque lo que se dibuja sea más pequeño (chips de 32dp, botones de 36dp).
 */
internal fun Modifier.touchTarget(): Modifier = defaultMinSize(MinTouchTarget, MinTouchTarget)

/** Opacidad del contenido deshabilitado (M3: 38 %). */
internal const val DISABLED_ALPHA = 0.38f

internal fun Color.disabled(): Color = copy(alpha = alpha * DISABLED_ALPHA)
