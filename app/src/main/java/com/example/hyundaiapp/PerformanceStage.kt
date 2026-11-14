package com.example.hyundaiapp

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val HorizonFadeFraction = .22f
internal const val LANE_INTRO_MS = 1300L
private const val LaneLabelFadeMs = 240f
private const val SingleLaneWidth = .66f
private const val TrackHorizon = .085f

// Both renderers reach depth 1 at the note's scheduled hit time.
internal fun projectNoteDepth(progress: Float): Float =
    if (progress <= 1f) progress / (1.6f - .6f * progress)
    else 1f + (progress - 1f) * 1.6f

internal fun noteScaleAtDepth(depth: Float): Float = .30f + .70f * depth

internal fun judgmentColor(judgment: Judgment?) = when (judgment) {
    Judgment.PERFECT -> Color(0xFFFFC857)
    Judgment.GREAT -> Color(0xFF72DDF7)
    Judgment.GOOD -> Color(0xFFB79CFF)
    Judgment.BAD, Judgment.MISS -> Color(0xFFFF8DA1)
    null -> Color(0xFFACB8D4)
}

/** 판정 후 0..1로 흐르는 진행도. 표시 중이 아니면 null. */
private fun flashProgress(game: RhythmGame, judge: LaneJudge?): Float? {
    if (judge == null || !game.running) return null
    val age = game.elapsed - judge.atMs
    return if (age in 0L until JUDGE_FLASH_MS) age / JUDGE_FLASH_MS.toFloat() else null
}

@Composable
internal fun PerformanceBackdrop(modifier: Modifier = Modifier) {
    Spacer(
        modifier.background(
            Brush.verticalGradient(listOf(Color(0xFF111722), Color(0xFF1E2737)))
        )
    )
}

@Composable
internal fun PerformanceStage(
    game: RhythmGame,
    canTap: Boolean,
    onLanePress: (Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val lanes = game.playingLanes
    val columnForLane = remember(lanes) { IntArray(seatColors.size) { lanes.indexOf(it) } }
    val widthFraction = if (lanes.size == 1) SingleLaneWidth else 1f
    BoxWithConstraints(modifier.clipToBounds()) {
        val compact = maxWidth < 720.dp
        val inputHeight = (maxHeight * .18f).coerceIn(
            minOf(64.dp, maxHeight * .35f), maxHeight * .35f
        )
        val judgmentDepth = (1f - inputHeight / maxHeight.coerceAtLeast(1.dp) - TrackHorizon).coerceAtLeast(.01f)
        val judgmentWidth = widthFraction / noteScaleAtDepth((1f - TrackHorizon) / judgmentDepth)
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val track = PerspectiveTrack(size, lanes.size, inputHeight.toPx(), widthFraction)
                val floor = track.surface(0f, lanes.size.toFloat())
                val floorShade = Brush.verticalGradient(
                    0f to Color(0x00061022),
                    HorizonFadeFraction to Color(0xA6061022),
                    1f to Color(0xD9091024),
                    startY = track.horizonY,
                    endY = track.endY
                )
                val laneFills = lanes.mapIndexed { column, lane ->
                    val tint = seatColors[lane]
                    val brush = Brush.verticalGradient(
                        listOf(tint.copy(alpha = 0f), tint.copy(alpha = .075f)),
                        startY = track.horizonY,
                        endY = track.fadeEndY
                    )
                    track.surface(column.toFloat(), column + 1f) to brush
                }
                val railInk = Brush.verticalGradient(
                    0f to Color(0x007D94B8),
                    HorizonFadeFraction to Color(0x427D94B8),
                    1f to Color(0xA6BACCE6),
                    startY = track.horizonY,
                    endY = track.endY
                )
                onDrawBehind {
                    drawPath(floor, floorShade)
                    laneFills.forEach { (path, brush) -> drawPath(path, brush) }
                    val elapsed = visibleSongTime(game)
                    val travelMs = game.noteTravelMs
                    drawBeatLines(track, elapsed, travelMs, game.song.beatMs)
                    for (edge in 0..lanes.size) {
                        val boundary = edge.toFloat()
                        drawLine(
                            railInk,
                            track.point(boundary, track.horizonY),
                            track.point(boundary, track.endY),
                            if (edge == 0 || edge == lanes.size) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }
                    lanes.forEachIndexed { column, lane ->
                        drawTarget(track, column, seatColors[lane])
                        val judge = game.laneJudge[lane]
                        flashProgress(game, judge)?.let { drawLaneFlash(track, column, judgmentColor(judge?.judgment), 1f - it, judge?.judgment == null) }
                    }
                    for (note in game.notes) {
                        if (note.judged) continue
                        val progress = 1f - (note.time - elapsed) / travelMs
                        if (progress < 0f) continue
                        val y = track.projectY(progress)
                        if (y > track.endY) continue
                        val column = columnForLane[note.lane]
                        if (column >= 0) drawStageNote(track, column, y, seatColors[note.lane])
                    }
                }
            }
        )
        Box(Modifier.matchParentSize().padding(bottom = inputHeight)) {
            Row(Modifier.fillMaxWidth(judgmentWidth).fillMaxHeight().align(Alignment.TopCenter)) {
                lanes.forEach { lane ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        FloatingJudgment(game, game.laneJudge[lane])
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(widthFraction).height(inputHeight).align(Alignment.BottomCenter)) {
            lanes.forEach { lane ->
                val label = if (game.isSolo) "${lane + 1} 레인" else seatNames[lane]
                RhythmButton(
                    onClick = {},
                    onPress = { at -> onLanePress(lane, at) },
                    enabled = canTap,
                    color = Color.Transparent,
                    textColor = RhythmColors.StageInk,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = label }
                ) {
                    LaneIntroLabel(game, lane, label, compact)
                }
            }
        }
        val countdown = game.resumeRemainingMs
        if (countdown in 1..RESUME_COUNTDOWN_MS) {
            Text(
                text = ((countdown + 999) / 1000).toString(),
                modifier = Modifier.align(Alignment.Center)
                    .raisedSurface(RoundedCornerShape(28.dp), RhythmColors.Ink)
                    .padding(horizontal = 36.dp, vertical = 10.dp),
                color = androidx.compose.ui.graphics.Color.White,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LaneIntroLabel(game: RhythmGame, lane: Int, label: String, compact: Boolean) {
    val visible by remember(game) { derivedStateOf { game.elapsed < game.laneIntroEndsAt } }
    if (!visible) return

    Text(
        text = if (game.isSolo) label else "${seatIcons[lane]} $label",
        color = seatColors[lane],
        fontSize = if (compact) 15.sp else 22.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
            val remaining = game.laneIntroEndsAt - game.elapsed
            val entered = ((LANE_INTRO_MS - remaining) / LaneLabelFadeMs).coerceIn(0f, 1f)
            val exiting = (1f - remaining / LaneLabelFadeMs).coerceIn(0f, 1f)
            alpha = UiEase.transform(entered) * (1f - FastOutLinearInEasing.transform(exiting))
        }
    )
}

@Composable
private fun FloatingJudgment(game: RhythmGame, judge: LaneJudge?) {
    val judgment = judge?.judgment ?: return
    val visible by remember(game, judge) {
        derivedStateOf { flashProgress(game, judge) != null }
    }
    if (!visible) return

    Text(
        judgment.name,
        color = judgmentColor(judgment),
        fontSize = 20.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.graphicsLayer {
            val progress = flashProgress(game, judge) ?: 1f
            translationY = -(26.dp.toPx() + 30.dp.toPx() * progress)
            alpha = 1f - progress
        }
    )
}

private fun visibleSongTime(game: RhythmGame): Float {
    val remainingMs = game.resumeRemainingMs
    val rewindMs = if (remainingMs > RESUME_COUNTDOWN_MS) {
        val liftProgress = 1f - (remainingMs - RESUME_COUNTDOWN_MS) / RESUME_LIFT_MS.toFloat()
        RESUME_COUNTDOWN_MS * FastOutSlowInEasing.transform(liftProgress)
    } else {
        remainingMs.toFloat()
    }
    // The countdown and live play share the same note projection and time scale.
    return game.elapsed - rewindMs
}

private class PerspectiveTrack(size: Size, val laneCount: Int, inputHeight: Float, widthFraction: Float) {
    val horizonY = size.height * TrackHorizon
    val endY = size.height
    val judgmentY = endY - inputHeight
    val fadeEndY = horizonY + (endY - horizonY) * HorizonFadeFraction
    private val centerX = size.width / 2
    private val approachHeight = (judgmentY - horizonY).coerceAtLeast(1f)
    private val nearWidth = size.width * widthFraction / scaleAt(endY)

    fun projectY(progress: Float): Float = horizonY + approachHeight * projectNoteDepth(progress)

    fun horizonAlphaAt(y: Float): Float = ((y - horizonY) / (fadeEndY - horizonY)).coerceIn(0f, 1f)

    fun scaleAt(y: Float): Float = noteScaleAtDepth((y - horizonY) / approachHeight)

    fun point(lanePosition: Float, y: Float): Offset {
        val across = lanePosition / laneCount - .5f
        return Offset(centerX + across * nearWidth * scaleAt(y), y)
    }

    fun surface(left: Float, right: Float): Path {
        val topLeft = point(left, horizonY)
        val topRight = point(right, horizonY)
        val bottomRight = point(right, endY)
        val bottomLeft = point(left, endY)
        return Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
    }
}

private fun DrawScope.drawBeatLines(track: PerspectiveTrack, elapsed: Float, travelMs: Float, beatMs: Long) {
    val firstBeat = (elapsed / beatMs).toLong().coerceAtLeast(0)
    val lastBeat = ((elapsed + travelMs) / beatMs).toLong()
    for (beat in firstBeat..lastBeat) {
        val progress = 1f - (beat * beatMs - elapsed) / travelMs
        if (progress !in 0f..1f) continue
        val y = track.projectY(progress)
        val downbeat = beat % 4 == 0L
        val opacity = if (downbeat) .24f else .12f
        drawLine(
            Color(0xFFD8E6FF).copy(alpha = opacity * track.scaleAt(y) * track.horizonAlphaAt(y)),
            track.point(0f, y),
            track.point(track.laneCount.toFloat(), y),
            if (downbeat) 1.5.dp.toPx() else 1.dp.toPx()
        )
    }
}

private fun DrawScope.drawTarget(track: PerspectiveTrack, lane: Int, color: Color) {
    val y = track.judgmentY
    val start = track.point(lane + .045f, y)
    val end = track.point(lane + .955f, y)
    val center = track.point(lane + .5f, y)
    drawLine(color.copy(alpha = .20f), start, end, 11.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color, start, end, 3.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(Color(0xFF101629), 8.dp.toPx(), center)
    drawCircle(color, 8.dp.toPx(), center, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawLaneFlash(track: PerspectiveTrack, lane: Int, color: Color, strength: Float, stray: Boolean) {
    val s = if (stray) strength * .5f else strength
    val top = track.judgmentY - (track.judgmentY - track.horizonY) * .45f
    val path = Path().apply {
        val tl = track.point(lane.toFloat(), top); val tr = track.point(lane + 1f, top)
        val br = track.point(lane + 1f, track.judgmentY); val bl = track.point(lane.toFloat(), track.judgmentY)
        moveTo(tl.x, tl.y); lineTo(tr.x, tr.y); lineTo(br.x, br.y); lineTo(bl.x, bl.y); close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = .45f * s)), startY = top, endY = track.judgmentY))
    drawLine(
        color.copy(alpha = .95f * s),
        track.point(lane + .045f, track.judgmentY), track.point(lane + .955f, track.judgmentY),
        (3 + 11 * s).dp.toPx(), cap = StrokeCap.Round
    )
}

private fun DrawScope.drawStageNote(track: PerspectiveTrack, lane: Int, y: Float, color: Color) {
    val scale = track.scaleAt(y)
    val width = (track.point(lane + .83f, y).x - track.point(lane + .17f, y).x)
        .coerceAtMost(240.dp.toPx() * scale)
    val height = 25.dp.toPx() * scale
    val center = track.point(lane + .5f, y)
    val topLeft = center - Offset(width / 2, height / 2)
    val radius = CornerRadius(height / 2)
    val glow = 3.dp.toPx() * scale
    drawRoundRect(
        color.copy(alpha = .28f),
        topLeft - Offset(glow, glow),
        Size(width + glow * 2, height + glow * 2),
        CornerRadius(height / 2 + glow)
    )
    drawRoundRect(color, topLeft, Size(width, height), radius)
    drawRoundRect(
        Color.White.copy(alpha = .63f),
        topLeft,
        Size(width, height),
        radius,
        style = Stroke(1.35.dp.toPx() * scale)
    )
}
