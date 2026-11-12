package com.example.hyundaiapp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayHud(game: RhythmGame, onPause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            PlayGauge(
                "가족 점수", game.score, game.maxScore, RhythmColors.BlueLight,
                Modifier.widthIn(max = 460.dp).fillMaxWidth()
            )
        }
        Box(Modifier.weight(.66f), contentAlignment = Alignment.CenterEnd) {
            PlayGauge(
                "라이프", game.life, MAX_LIFE,
                if (game.life <= MAX_LIFE / 4) RhythmColors.Warning else RhythmColors.Positive,
                Modifier.widthIn(max = 300.dp).fillMaxWidth()
            )
        }
        val iconColor = if (game.running) Color.White else RhythmColors.Muted
        RhythmButton(
            onClick = onPause,
            enabled = game.running,
            overStage = true,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(58.dp).semantics { contentDescription = "잠깐 쉬기" }
        ) {
            Canvas(Modifier.size(22.dp)) {
                val barWidth = size.width * .24f
                listOf(.20f, .56f).forEach { left ->
                    drawRoundRect(
                        iconColor, Offset(size.width * left, 0f),
                        Size(barWidth, size.height), CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayGauge(label: String, value: Int, maximum: Int, color: Color, modifier: Modifier) {
    // Black hero tile over the stage; the bar eases to each new value.
    val progress by animateFloatAsState(
        value.toFloat() / maximum.coerceAtLeast(1), tween(320), label = "Gauge fill"
    )
    Column(
        modifier.raisedSurface(RoundedCornerShape(20.dp), RhythmColors.Ink).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            Text(
                "$value / $maximum",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color.White,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = Color(0x33FFFFFF),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}
