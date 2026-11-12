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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GameContentMaxWidth = 1280.dp

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
        RhythmButton(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
        ) { Text("곡 선택", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        RhythmButton(
            onClick = onTiming,
            primary = false,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
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
        titleStyle = if (expanded) typography.displayLarge.copy(fontSize = 80.sp, lineHeight = 88.sp)
            else typography.headlineLarge,
        titleColor = RhythmColors.Blue,
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
    Column(
        modifier.raisedSurface().padding(30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        val heights = listOf(.48f, .72f, .36f, .61f)
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            heights.forEachIndexed { index, level ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().insetSurface(CircleShape).clip(CircleShape),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        val width = size.width * .68f
                        val height = size.height * level
                        val inset = minOf((size.width - width) / 2, size.height * .15f)
                        drawRoundRect(
                            color = if (index % 2 == 0) RhythmColors.BlueLight else RhythmColors.Blue,
                            topLeft = Offset(inset, size.height - height - inset),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(width / 2)
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            heights.indices.forEach { index ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(46.dp).raisedSurface(CircleShape).padding(10.dp)
                            .background(if (index % 2 == 0) RhythmColors.BlueLight else RhythmColors.Blue, CircleShape)
                    )
                }
            }
        }
    }
}
