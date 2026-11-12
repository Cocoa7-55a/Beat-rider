package com.example.hyundaiapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                "가족 점수", game.score, game.maxScore, Color(0xFFFFC857),
                Modifier.widthIn(max = 460.dp).fillMaxWidth()
            )
        }
        Box(Modifier.weight(.66f), contentAlignment = Alignment.CenterEnd) {
            PlayGauge(
                "라이프", game.life, MAX_LIFE,
                if (game.life <= MAX_LIFE / 4) Color(0xFFFF8DA1) else Color(0xFF9DE5BD),
                Modifier.widthIn(max = 300.dp).fillMaxWidth()
            )
        }
        val iconColor = if (game.running) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        FilledTonalIconButton(
            onClick = onPause,
            enabled = game.running,
            modifier = Modifier.size(56.dp).semantics { contentDescription = "잠깐 쉬기" }
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

@Composable
private fun PlayGauge(label: String, value: Int, maximum: Int, color: Color, modifier: Modifier) {
    val track = Color(0xFF343D55)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        LinearProgressIndicator(
            progress = { value.toFloat() / maximum.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = color,
            trackColor = track,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
        Text("$value / $maximum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
