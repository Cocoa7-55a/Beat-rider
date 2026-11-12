package com.example.hyundaiapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HomeAccent = Color(0xFFFF82AA)
private val HomeOnAccent = Color(0xFF301125)
private val HomePanel = Color(0xFF1D2034)

@Composable
internal fun GameHome(onPlay: () -> Unit, onTiming: () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 1000.dp).fillMaxWidth().padding(vertical = 24.dp)) {
            if (maxWidth >= 640.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeActions(onPlay, onTiming, Modifier.weight(1f))
                    LanePreview(Modifier.weight(1f).height(340.dp))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HomeActions(onPlay, onTiming, Modifier.fillMaxWidth())
                    LanePreview(Modifier.fillMaxWidth().height(230.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeActions(onPlay: () -> Unit, onTiming: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StaggeredHeading(
            "리듬타", "함께 만드는 우리 가족의 리듬",
            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black)
        )
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HomeAccent, contentColor = HomeOnAccent)
        ) { Text("곡 선택", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onTiming,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(10.dp)
        ) { Text("타이밍 보정", fontSize = 17.sp) }
    }
}

@Composable
private fun LanePreview(modifier: Modifier) {
    Canvas(modifier.background(HomePanel, RoundedCornerShape(16.dp))) {
        val line = size.height * .78f
        val width = size.width / 4
        val positions = listOf(.53f, .28f, .46f, .18f)
        seatColors.forEachIndexed { index, color ->
            val left = width * index
            drawLine(color.copy(alpha = .20f), Offset(left + 1, 0f), Offset(left + 1, size.height), 1.dp.toPx())
            drawLine(color, Offset(left + width * .12f, line), Offset(left + width * .88f, line), 3.dp.toPx())
            drawRoundRect(
                color, Offset(left + width * .19f, size.height * positions[index]),
                Size(width * .62f, 16.dp.toPx()), CornerRadius(6.dp.toPx())
            )
        }
    }
}
