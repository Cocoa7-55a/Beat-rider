package com.example.hyundaiapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.withTimeoutOrNull

private const val EnterMs = 280
private const val ExitMs = 220
// Strong ease-out for both directions: exits must start fast too, never ease-in.
internal val UiEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

internal fun uiFade() = ContentTransform(
    targetContentEnter = fadeIn(tween(EnterMs, easing = UiEase)),
    initialContentExit = fadeOut(tween(ExitMs, easing = UiEase)),
    sizeTransform = null
)

internal fun uiInstant() = ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)

@Composable
internal fun StaggeredHeading(
    title: String,
    subtitle: String,
    titleStyle: TextStyle,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = true,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    key(title) {
        var visible by remember { mutableStateOf(!animateEntrance) }
        val titleAlpha by animateFloatAsState(
            if (visible) 1f else 0f, tween(EnterMs, easing = UiEase), label = "Title entrance"
        )
        val subtitleAlpha by animateFloatAsState(
            if (visible) 1f else 0f, tween(EnterMs, delayMillis = 70, easing = UiEase),
            label = "Subtitle entrance"
        )
        LaunchedEffect(Unit) { visible = true }
        val alignment = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start
        Column(modifier, horizontalAlignment = horizontalAlignment, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, Modifier.graphicsLayer {
                alpha = titleAlpha
                translationY = (1f - titleAlpha) * 8.dp.toPx()
            }, style = titleStyle, color = titleColor, textAlign = alignment)
            Text(subtitle, Modifier.graphicsLayer {
                alpha = subtitleAlpha
                translationY = (1f - subtitleAlpha) * 8.dp.toPx()
            }, style = subtitleStyle, color = RhythmColors.Muted, textAlign = alignment)
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
        Modifier.size(54.dp).toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(9.dp)
        Canvas(Modifier.size(26.dp).background(RhythmColors.Inset, shape)) {
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
    val color = LocalContentColor.current
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
                            enter = fadeIn(tween(EnterMs, easing = UiEase)),
                            exit = fadeOut(tween(ExitMs, easing = UiEase))
                        )
                        .background(Color.Black.copy(alpha = .58f))
                )
                val enter = if (fromBottom) slideInVertically(tween(EnterMs, easing = UiEase)) { it }
                    else scaleIn(tween(EnterMs, easing = UiEase), initialScale = .9f) + fadeIn(tween(EnterMs, easing = UiEase))
                val exit = if (fromBottom) slideOutVertically(tween(ExitMs, easing = UiEase)) { it }
                    else scaleOut(tween(ExitMs, easing = UiEase), targetScale = .9f) + fadeOut(tween(ExitMs, easing = UiEase))
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
            (slideInVertically(tween(EnterMs, easing = UiEase)) { it } togetherWith
                slideOutVertically(tween(ExitMs, easing = UiEase)) { it }).using(null)
        },
        label = "Snackbar slide"
    ) { item ->
        if (item != null) Snackbar(
            modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) { Text(item.message) }
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

/** Odometer text: each changed character rolls vertically. */
@Composable
internal fun RollingNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified
) {
    Row(modifier.clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { index, char ->
            val fromEnd = text.length - 1 - index
            key(fromEnd) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val slide = tween<IntOffset>(260, easing = UiEase)
                        val fade = tween<Float>(200)
                        val up = targetState > initialState
                        (slideInVertically(slide) { if (up) it else -it } + fadeIn(fade)) togetherWith
                            (slideOutVertically(slide) { if (up) -it else it } + fadeOut(fade))
                    },
                    label = "Rolling digit"
                ) { Text(it.toString(), style = style, color = color, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

/** Segmented control: gray track with a white pill that slides to the selection. */
@Composable
internal fun RhythmSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val position by animateFloatAsState(
        selected.toFloat(), spring(dampingRatio = .8f, stiffness = 500f), label = "Segment pill"
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.background(RhythmColors.Panel, shape).drawBehind {
            val pad = 4.dp.toPx()
            val segment = size.width / options.size
            val corner = CornerRadius(12.dp.toPx())
            val pill = Size(segment - pad * 2, size.height - pad * 2)
            drawRoundRect(Color(0x14000000), Offset(segment * position + pad, pad + 1.dp.toPx()), pill, corner)
            drawRoundRect(Color.White, Offset(segment * position + pad, pad), pill, corner)
        }.selectableGroup(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val ink by animateColorAsState(
                if (index == selected) RhythmColors.Ink else RhythmColors.Muted, label = "Segment ink"
            )
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).focusRing(shape).clip(shape)
                    .selectable(index == selected, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}
