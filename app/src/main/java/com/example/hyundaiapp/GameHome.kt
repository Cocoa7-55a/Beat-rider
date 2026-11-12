package com.example.hyundaiapp

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GameContentMaxWidth = 1280.dp

private val HomeAccent = Color(0xFFFF82AA)
private val HomeOnAccent = Color(0xFF301125)
private val HomePanel = Color(0xFF1D2034)

@Composable
internal fun GameHome(onPlay: () -> Unit, onTiming: () -> Unit, heading: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.widthIn(max = GameContentMaxWidth).fillMaxWidth().padding(vertical = 28.dp)) {
            if (maxWidth >= 720.dp) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeActions(onPlay, onTiming, heading, Modifier.weight(1f))
                    LanePreview(Modifier.weight(1.12f).aspectRatio(1.33f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    HomeActions(onPlay, onTiming, heading, Modifier.fillMaxWidth())
                    LanePreview(Modifier.fillMaxWidth().aspectRatio(1.6f))
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        heading()
        Button(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HomeAccent, contentColor = HomeOnAccent)
        ) { Text("곡 선택", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onTiming,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(10.dp)
        ) { Text("타이밍 보정", fontSize = 20.sp) }
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
        title = "리듬타",
        subtitle = "함께 만드는 우리 가족의 리듬",
        titleStyle = (if (expanded) typography.displayLarge else typography.headlineLarge)
            .copy(fontWeight = FontWeight.Black),
        subtitleStyle = if (expanded) typography.titleLarge else typography.bodyMedium,
        modifier = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "rhythm-title"),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = BoundsTransform { _, _ -> tween(340, easing = LinearOutSlowInEasing) },
            enter = fadeIn(tween(250, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(220, easing = FastOutLinearInEasing)),
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillBounds)
        ),
        animateEntrance = animateEntrance
    )
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
            drawLine(color, Offset(left + width * .12f, line), Offset(left + width * .88f, line), 4.dp.toPx())
            drawRoundRect(
                color, Offset(left + width * .19f, size.height * positions[index]),
                Size(width * .62f, 22.dp.toPx()), CornerRadius(8.dp.toPx())
            )
        }
    }
}
