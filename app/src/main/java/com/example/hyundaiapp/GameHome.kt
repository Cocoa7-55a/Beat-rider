package com.example.hyundaiapp

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GameContentMaxWidth = 1280.dp

private object HomeLayout {
    val WideBreakpoint = 720.dp
    val VerticalPadding = 28.dp
    val WidePaneGap = 48.dp
    val StackedPaneGap = 24.dp
    val ActionGap = 20.dp
    val PlayButtonHeight = 72.dp
    val TimingButtonHeight = 64.dp
    val PlayLabelSize = 24.sp
    val TimingLabelSize = 20.sp
    val ExpandedTitleSize = 80.sp
    val ExpandedTitleLineHeight = 88.sp
    const val WidePreviewWeight = 1.12f
    const val WidePreviewRatio = 1.33f
    const val StackedPreviewRatio = 1.6f
}

private object PreviewLayout {
    val CornerRadius = 14.dp
    val Padding = 30.dp
    val LaneGap = 16.dp
    val TargetGap = 20.dp
    val TargetHeight = 46.dp
    val NoteMaxHeight = 21.dp
}

@Composable
internal fun GameHome(
    onPlay: () -> Unit,
    onTiming: () -> Unit,
    noteSpeed: Float,
    heading: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = GameContentMaxWidth).fillMaxWidth().padding(vertical = HomeLayout.VerticalPadding)) {
            if (maxWidth >= HomeLayout.WideBreakpoint) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HomeLayout.WidePaneGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeActions(onPlay, onTiming, heading, Modifier.weight(1f))
                    LanePreview(noteSpeed, Modifier.weight(HomeLayout.WidePreviewWeight).aspectRatio(HomeLayout.WidePreviewRatio))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(HomeLayout.StackedPaneGap)) {
                    HomeActions(onPlay, onTiming, heading, Modifier.fillMaxWidth())
                    LanePreview(noteSpeed, Modifier.fillMaxWidth().aspectRatio(HomeLayout.StackedPreviewRatio))
                }
            }
        }
    }
}

@Composable
private fun HomeActions(
    onPlay: () -> Unit,
    onTiming: () -> Unit,
    heading: @Composable () -> Unit,
    modifier: Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HomeLayout.ActionGap)) {
        heading()
        RhythmButton(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().heightIn(min = HomeLayout.PlayButtonHeight)
        ) { Text("곡 선택", fontSize = HomeLayout.PlayLabelSize, fontWeight = FontWeight.Bold) }
        RhythmButton(
            onClick = onTiming,
            primary = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = HomeLayout.TimingButtonHeight)
        ) { Text("타이밍 보정", fontSize = HomeLayout.TimingLabelSize) }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.BrandHeading(
    visibilityScope: AnimatedVisibilityScope,
    expanded: Boolean,
    animateEntrance: Boolean = false
) {
    val typography = MaterialTheme.typography
    StaggeredHeading(
        title = "박자 탈래",
        subtitle = "함께 만드는 우리 가족의 리듬",
        titleStyle = if (expanded) typography.displayLarge.copy(
            fontSize = HomeLayout.ExpandedTitleSize,
            lineHeight = HomeLayout.ExpandedTitleLineHeight
        )
            else typography.headlineLarge,
        titleColor = RhythmColors.Ink,
        subtitleStyle = if (expanded) typography.titleLarge else typography.bodyMedium,
        modifier = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "rhythm-title"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = BoundsTransform { _, _ -> tween(340, easing = LinearOutSlowInEasing) },
            enter = fadeIn(tween(250, easing = UiEase)),
            exit = fadeOut(tween(220, easing = UiEase)),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillBounds)
        ),
        animateEntrance = animateEntrance
    )
}

@Composable
private fun LanePreview(noteSpeed: Float, modifier: Modifier) {
    val song = RhythmSong.TWINKLE
    val lanes = seatColors.indices
    val chart = remember { song.playNotes(ChartDifficulty.NORMAL, lanes.toList()) }
    val startMs = chart.first().time
    val loopMs = chart.last().time - startMs + song.beatMs
    val hitTimesByLane = remember(chart) { chart.groupBy({ it.lane }, { it.time - startMs }) }
    val phase = remember { mutableLongStateOf(song.beatMs / 2) }
    LaunchedEffect(loopMs) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                phase.longValue = ((frameNanos - startNanos) / 1_000_000L + song.beatMs / 2) % loopMs
            }
        }
    }

    val travelMs = BASE_NOTE_TRAVEL_MS / noteSpeed
    val shape = RoundedCornerShape(PreviewLayout.CornerRadius)
    Row(
        modifier.raisedSurface().padding(PreviewLayout.Padding),
        horizontalArrangement = Arrangement.spacedBy(PreviewLayout.LaneGap)
    ) {
        for (lane in lanes) {
            val hitTimes = hitTimesByLane.getValue(lane)
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(PreviewLayout.TargetGap)
            ) {
                Canvas(
                    Modifier.weight(1f).fillMaxWidth().clip(shape).background(RhythmColors.Line)
                ) {
                    val elapsed = phase.longValue
                    val horizonY = size.height * .06f
                    val judgmentY = size.height * .90f
                    val flash = previewHitFlash(hitTimes, elapsed, loopMs)
                    val lineInset = size.width * .12f
                    drawLine(
                        RhythmColors.Blue.copy(alpha = .34f + .66f * flash),
                        Offset(lineInset, judgmentY),
                        Offset(size.width - lineInset, judgmentY),
                        (2 + 2 * flash).dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    for (hitTime in hitTimes) {
                        val timeUntilHit = (hitTime - elapsed + loopMs) % loopMs
                        if (timeUntilHit > travelMs) continue
                        val depth = projectNoteDepth(1f - timeUntilHit / travelMs)
                        val scale = noteScaleAtDepth(depth)
                        val y = horizonY + (judgmentY - horizonY) * depth
                        val width = size.width * .76f * scale
                        val height = minOf(PreviewLayout.NoteMaxHeight.toPx(), size.height * .12f) * scale
                        drawRoundRect(
                            RhythmColors.Blue.copy(alpha = .62f + .38f * depth),
                            Offset((size.width - width) / 2, y - height / 2),
                            Size(width, height),
                            CornerRadius(height / 2)
                        )
                    }
                }
                Box(
                    Modifier.fillMaxWidth().height(PreviewLayout.TargetHeight)
                        .insetSurface(shape).clip(shape)
                        .drawBehind {
                            val flash = previewHitFlash(hitTimes, phase.longValue, loopMs)
                            if (flash > 0f) drawRect(RhythmColors.Ink.copy(alpha = flash))
                        }
                )
            }
        }
    }
}

private fun previewHitFlash(hitTimes: List<Long>, elapsed: Long, loopMs: Long): Float {
    val previousHit = hitTimes.lastOrNull { it <= elapsed } ?: (hitTimes.last() - loopMs)
    return (1f - (elapsed - previousHit) / JUDGE_FLASH_MS.toFloat()).coerceIn(0f, 1f)
}
