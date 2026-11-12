package com.example.hyundaiapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// Bento style: white page, flat light-gray tiles, white inner cards, black + blue accents.
internal object RhythmColors {
    val Background = Color(0xFFFFFFFF)
    val Panel = Color(0xFFF3F4F6)
    val Inset = Color(0xFFFFFFFF)
    val Selected = Color(0xFFE3EDFF)
    val Ink = Color(0xFF0B0B0F)
    val Muted = Color(0xFF6B7280)
    val Blue = Color(0xFF1568F5)
    val BlueLight = Color(0xFF6CA4FF)
    val Positive = Color(0xFF0E9F6E)
    val Warning = Color(0xFFE5484D)
    val Line = Color(0xFFE3E5E9)
    val StageInk = Color(0xFFDFE9FF)
}

internal val PanelShape = RoundedCornerShape(24.dp)
internal val ButtonShape = RoundedCornerShape(16.dp)

private val RhythmScheme = lightColorScheme(
    primary = RhythmColors.Blue,
    onPrimary = Color.White,
    primaryContainer = RhythmColors.Selected,
    onPrimaryContainer = RhythmColors.Blue,
    secondary = RhythmColors.Muted,
    secondaryContainer = RhythmColors.Inset,
    onSecondaryContainer = RhythmColors.Ink,
    background = RhythmColors.Background,
    onBackground = RhythmColors.Ink,
    surface = RhythmColors.Panel,
    onSurface = RhythmColors.Ink,
    surfaceVariant = RhythmColors.Inset,
    onSurfaceVariant = RhythmColors.Muted,
    outline = RhythmColors.Muted,
    outlineVariant = RhythmColors.Line,
    inverseSurface = RhythmColors.Ink,
    surfaceContainerHighest = RhythmColors.Line,
    inverseOnSurface = Color.White,
    error = RhythmColors.Warning
)

private val RhythmTypography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontWeight = FontWeight.Bold),
        headlineLarge = it.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Medium),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
internal fun RhythmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RhythmScheme, typography = RhythmTypography, content = content)
}

/** Flat tile. [overStage] is kept for call sites; tiles are flat everywhere. */
@Suppress("UNUSED_PARAMETER")
internal fun Modifier.raisedSurface(
    shape: Shape = PanelShape,
    color: Color = RhythmColors.Panel,
    overStage: Boolean = false
): Modifier = background(color, shape)

/** Inner card: white with a soft drop shadow, sits on a tile. */
internal fun Modifier.insetSurface(shape: Shape, color: Color = RhythmColors.Inset): Modifier =
    dropShadow(shape, Shadow(radius = 6.dp, color = Color(0x14000000), offset = DpOffset(0.dp, 2.dp)))
        .background(color, shape)

@Composable
internal fun Modifier.focusRing(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) RhythmColors.Blue else Color.Transparent, shape)
}

@Composable
internal fun RhythmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
    color: Color = if (primary) RhythmColors.Ink else RhythmColors.Inset,
    textColor: Color = if (primary) Color.White else RhythmColors.Ink,
    @Suppress("UNUSED_PARAMETER") overStage: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(160), label = "Button press")
    // Black buttons flood blue while held; white and tinted ones just darken.
    val fill by animateColorAsState(
        when {
            !enabled -> RhythmColors.Line
            !pressed -> color
            color == RhythmColors.Ink -> RhythmColors.Blue
            else -> lerp(color, Color.Black, .12f)
        },
        tween(180), label = "Button fill"
    )
    val surface = if (enabled && color == RhythmColors.Inset)
        Modifier.insetSurface(ButtonShape, fill).border(1.dp, RhythmColors.Line, ButtonShape)
    else Modifier.background(fill, ButtonShape)
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.then(surface).focusRing(ButtonShape),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = textColor,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = RhythmColors.Muted
        ),
        interactionSource = interactions,
        contentPadding = contentPadding,
        content = content
    )
}
