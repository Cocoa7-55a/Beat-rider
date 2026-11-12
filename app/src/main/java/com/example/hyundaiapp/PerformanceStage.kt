package com.example.hyundaiapp

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PerformanceBackdrop(modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.performance_stage),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.matchParentSize()
        )
        Spacer(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0xDE101629),
                    .22f to Color(0x7A101629),
                    .48f to Color.Transparent,
                    .76f to Color.Transparent,
                    1f to Color(0xBD101629)
                )
            )
        )
    }
}

@Composable
internal fun PerformanceStage(game: RhythmGame, seats: List<Int>, modifier: Modifier = Modifier) {
    val laneForSeat = remember(seats) { IntArray(seatColors.size) { seats.indexOf(it) } }
    Box(modifier.clipToBounds()) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val track = PerspectiveTrack(size, seats.size)
                val floor = track.surface(0f, seats.size.toFloat())
                val floorShade = Brush.verticalGradient(
                    listOf(Color(0xA6061022), Color(0xD9091024)),
                    startY = track.horizonY,
                    endY = track.endY
                )
                val laneFills = seats.mapIndexed { lane, seat ->
                    track.surface(lane.toFloat(), lane + 1f) to seatColors[seat].copy(alpha = .075f)
                }
                val railInk = Brush.verticalGradient(
                    listOf(Color(0x427D94B8), Color(0xA6BACCE6)),
                    startY = track.horizonY,
                    endY = track.endY
                )
                onDrawBehind {
                    drawPath(floor, floorShade)
                    laneFills.forEach { (path, color) -> drawPath(path, color) }
                    val elapsed = visibleSongTime(game)
                    val travelMs = 2000f / game.noteSpeed
                    drawBeatLines(track, elapsed, travelMs, game.song.beatMs)
                    for (edge in 0..seats.size) {
                        val boundary = edge.toFloat()
                        drawLine(
                            railInk,
                            track.point(boundary, track.horizonY),
                            track.point(boundary, track.endY),
                            if (edge == 0 || edge == seats.size) 2.dp.toPx() else 1.dp.toPx()
                        )
                    }
                    seats.forEachIndexed { lane, seat -> drawTarget(track, lane, seatColors[seat]) }
                    for (note in game.notes) {
                        if (note.judged) continue
                        val progress = 1f - (note.time - elapsed) / travelMs
                        if (progress < 0f) continue
                        val y = track.projectY(progress)
                        if (y > track.endY) continue
                        val lane = laneForSeat[note.seat]
                        if (lane >= 0) drawStageNote(track, lane, y, seatColors[note.seat])
                    }
                }
            }
        )
        val countdown = game.resumeRemainingMs
        if (countdown in 1..RESUME_COUNTDOWN_MS) {
            Text(
                text = ((countdown + 999) / 1000).toString(),
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xEB101629), RoundedCornerShape(16.dp))
                    .padding(horizontal = 36.dp, vertical = 10.dp),
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
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

private class PerspectiveTrack(size: Size, val laneCount: Int) {
    val horizonY = size.height * .085f
    val judgmentY = size.height * .85f
    val endY = size.height * .94f
    private val centerX = size.width / 2
    private val nearWidth = size.width * if (laneCount == 1) .58f else .925f
    private val approachHeight = judgmentY - horizonY

    fun projectY(progress: Float): Float {
        // This curve preserves the exact arrival time and quickens the approach near the judgment line.
        val depth = if (progress <= 1f) progress / (1.6f - .6f * progress)
            else 1f + (progress - 1f) * 1.6f
        return horizonY + approachHeight * depth
    }

    fun scaleAt(y: Float): Float = .30f + .70f * ((y - horizonY) / approachHeight)

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
        drawLine(
            Color(0xFFD8E6FF).copy(alpha = track.scaleAt(y) * if (downbeat) .24f else .12f),
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
