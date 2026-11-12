package com.example.hyundaiapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object RhythmColors {
    val Background = Color(0xFFE7ECEF)
    val Panel = Color(0xFFEDF1F7)
    val Inset = Color(0xFFDEE6F2)
    val Selected = Color(0xFFD9E7FD)
    val Ink = Color(0xFF202D45)
    val Muted = Color(0xFF52627A)
    val Blue = Color(0xFF2862C9)
    val BlueLight = Color(0xFF528BF2)
    val Positive = Color(0xFF18684C)
    val Warning = Color(0xFF963D4B)
    val Line = Color(0xFFBAC8DC)
    val Highlight = Color(0xDCFFFFFF)
    val Shadow = Color(0x7597A9C3)
    val StageInk = Color(0xFFDFE9FF)
}

internal val PanelShape = RoundedCornerShape(32.dp)

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
    inverseSurface = RhythmColors.Blue,
    inverseOnSurface = Color.White,
    error = RhythmColors.Warning
)

private val RhythmTypography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontWeight = FontWeight.Black),
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

internal fun Modifier.raisedSurface(
    shape: Shape = PanelShape,
    color: Color = RhythmColors.Panel,
    overStage: Boolean = false
): Modifier = dropShadow(
    shape,
    Shadow(radius = 7.dp, color = if (overStage) Color(0x38C5DBFF) else RhythmColors.Highlight, offset = DpOffset(-3.dp, -3.dp))
).dropShadow(
    shape,
    Shadow(radius = 7.dp, color = if (overStage) Color(0xA0061025) else RhythmColors.Shadow, offset = DpOffset(3.dp, 5.dp))
).background(color, shape)

internal fun Modifier.insetSurface(shape: Shape, color: Color = RhythmColors.Inset): Modifier =
    background(color, shape)
        .innerShadow(shape, Shadow(radius = 5.dp, color = RhythmColors.Shadow, offset = DpOffset(2.dp, 3.dp)))
        .innerShadow(shape, Shadow(radius = 4.dp, color = RhythmColors.Highlight, offset = DpOffset(-2.dp, -2.dp)))

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
    color: Color = if (primary) RhythmColors.Blue else RhythmColors.Panel,
    textColor: Color = if (primary) Color.White else RhythmColors.Blue,
    overStage: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .983f else 1f, tween(200), label = "Button press")
    Button(
        onClick = onClick,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.then(
            when {
                !enabled -> Modifier.insetSurface(CircleShape)
                pressed -> Modifier.insetSurface(CircleShape, color)
                else -> Modifier.raisedSurface(CircleShape, color, overStage)
            }
        ).focusRing(CircleShape),
        enabled = enabled,
        shape = CircleShape,
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

@Composable
internal fun RhythmTab(selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 52.dp)
            .then(if (selected) Modifier.insetSurface(CircleShape, RhythmColors.Selected) else Modifier.raisedSurface(CircleShape))
            .focusRing(CircleShape)
            .clip(CircleShape)
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) RhythmColors.Blue else RhythmColors.Muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
