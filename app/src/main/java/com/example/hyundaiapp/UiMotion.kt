package com.example.hyundaiapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.withTimeoutOrNull

private const val EnterMs = 280
private const val ExitMs = 220
private val EnterEase = LinearOutSlowInEasing
private val ExitEase = FastOutLinearInEasing

internal fun uiFade() = ContentTransform(
    targetContentEnter = fadeIn(tween(EnterMs, easing = EnterEase)),
    initialContentExit = fadeOut(tween(ExitMs, easing = ExitEase)),
    sizeTransform = null
)

@Composable
internal fun StaggeredHeading(
    title: String,
    subtitle: String,
    titleStyle: TextStyle,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = true
) {
    key(title) {
        var visible by remember { mutableStateOf(!animateEntrance) }
        val titleAlpha by animateFloatAsState(
            if (visible) 1f else 0f, tween(EnterMs, easing = EnterEase), label = "Title entrance"
        )
        val subtitleAlpha by animateFloatAsState(
            if (visible) 1f else 0f, tween(EnterMs, delayMillis = 70, easing = EnterEase),
            label = "Subtitle entrance"
        )
        LaunchedEffect(Unit) { visible = true }
        Column(modifier, horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.graphicsLayer {
                alpha = titleAlpha
                translationY = (1f - titleAlpha) * 8.dp.toPx()
            }, style = titleStyle)
            Text(subtitle, Modifier.graphicsLayer {
                alpha = subtitleAlpha
                translationY = (1f - subtitleAlpha) * 8.dp.toPx()
            }, style = subtitleStyle, color = Color(0xFFACB8D4))
        }
    }
}

@Composable
internal fun selectionScale(selected: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        val justSelected = selected && !previous
        previous = selected
        if (justSelected) {
            scale.snapTo(.94f)
            withTimeoutOrNull(360) {
                scale.animateTo(1f, spring(dampingRatio = .43f, stiffness = 570f))
            }
        }
        scale.snapTo(1f)
    }
    return scale.asState()
}

@Composable
internal fun DrawnCheckbox(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val progress by animateFloatAsState(
        if (checked) 1f else 0f, tween(240, easing = FastOutSlowInEasing), label = "Check stroke"
    )
    val colors = MaterialTheme.colorScheme
    val ink = if (enabled) colors.primary else colors.onSurface.copy(alpha = .38f)
    Box(
        Modifier.size(48.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val outline = 2.dp.toPx()
            val inset = outline / 2
            val corner = CornerRadius(3.dp.toPx())
            drawRoundRect(ink, Offset(inset, inset), Size(size.width - outline, size.height - outline), corner, style = Stroke(outline))
            val start = Offset(size.width * .23f, size.height * .51f)
            val elbow = Offset(size.width * .43f, size.height * .70f)
            val end = Offset(size.width * .78f, size.height * .32f)
            if (progress > 0f) {
                val firstLeg = (progress / .36f).coerceAtMost(1f)
                drawLine(ink, start, start + (elbow - start) * firstLeg, outline, StrokeCap.Round)
            }
            if (progress > .36f) {
                val secondLeg = (progress - .36f) / .64f
                drawLine(ink, elbow, elbow + (end - elbow) * secondLeg, outline, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun LinearSpinner(modifier: Modifier = Modifier) {
    val rotation by rememberInfiniteTransition(label = "Loading").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing)),
        label = "Linear rotation"
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(20.dp).progressSemantics().rotate(rotation)) {
        val stroke = 2.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(color.copy(alpha = .22f), radius = arcSize.width / 2, style = Stroke(stroke))
        drawArc(color, -90f, 105f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
internal fun MotionDialog(
    visible: Boolean,
    fromBottom: Boolean = false,
    onDismissRequest: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val state = remember { MutableTransitionState(false) }
    state.targetState = visible
    if (!state.currentState && !state.targetState && state.isIdle) return
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = fromBottom,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        AnimatedVisibility(visibleState = state, enter = EnterTransition.None, exit = ExitTransition.None) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.matchParentSize()
                        .animateEnterExit(
                            enter = fadeIn(tween(EnterMs, easing = EnterEase)),
                            exit = fadeOut(tween(ExitMs, easing = ExitEase))
                        )
                        .background(Color.Black.copy(alpha = .58f))
                )
                val enter = if (fromBottom) slideInVertically(tween(EnterMs, easing = EnterEase)) { it }
                    else scaleIn(tween(EnterMs, easing = EnterEase), initialScale = .9f) + fadeIn(tween(EnterMs, easing = EnterEase))
                val exit = if (fromBottom) slideOutVertically(tween(ExitMs, easing = ExitEase)) { it }
                    else scaleOut(tween(ExitMs, easing = ExitEase), targetScale = .9f) + fadeOut(tween(ExitMs, easing = ExitEase))
                content(
                    Modifier.align(if (fromBottom) Alignment.BottomCenter else Alignment.Center)
                        .animateEnterExit(enter, exit)
                )
            }
        }
    }
}

internal class UiNotice(val message: String)

@Composable
internal fun SlideSnackbar(notice: UiNotice?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = notice, modifier = modifier, contentAlignment = Alignment.BottomCenter,
        transitionSpec = {
            (slideInVertically(tween(EnterMs, easing = EnterEase)) { it } togetherWith
                slideOutVertically(tween(ExitMs, easing = ExitEase)) { it }).using(null)
        },
        label = "Snackbar slide"
    ) { item ->
        if (item != null) Snackbar(Modifier.widthIn(max = 460.dp).fillMaxWidth()) { Text(item.message) }
    }
}

@Composable
internal fun FadeToast(notice: UiNotice?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = notice, modifier = modifier, contentAlignment = Alignment.BottomCenter,
        transitionSpec = { uiFade() }, label = "Toast fade"
    ) { item ->
        if (item != null) {
            Surface(
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(item.message, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
    }
}
