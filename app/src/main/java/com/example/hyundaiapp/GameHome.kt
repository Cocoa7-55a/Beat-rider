package com.example.hyundaiapp

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        titleColor = RhythmColors.Ink,
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

// Weekday-bar tile: one bar is highlighted blue, and the highlight walks on the beat.
@Composable
private fun LanePreview(modifier: Modifier) {
    val levels = listOf(.48f, .72f, .36f, .61f)
    var beat by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(900)
            beat++
        }
    }
    Column(modifier.raisedSurface().padding(30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("오늘의 리듬", color = RhythmColors.Muted, fontSize = 17.sp)
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            levels.forEachIndexed { index, level ->
                val active = beat % levels.size == index
                val height by animateFloatAsState(
                    if (active) 1f else level, spring(dampingRatio = .7f, stiffness = 300f), label = "Bar height"
                )
                val color by animateColorAsState(if (active) RhythmColors.Blue else RhythmColors.Line, label = "Bar color")
                Box(
                    Modifier.weight(1f).fillMaxHeight(height.coerceIn(0f, 1f))
                        .background(color, RoundedCornerShape(14.dp))
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            levels.indices.forEach { index ->
                val active = beat % levels.size == index
                val color by animateColorAsState(if (active) RhythmColors.Ink else RhythmColors.Inset, label = "Pad color")
                Box(Modifier.weight(1f).height(46.dp).insetSurface(RoundedCornerShape(14.dp), color))
            }
        }
    }
}
