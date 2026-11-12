package com.example.hyundaiapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal const val MAX_TIMING_OFFSET_MS = 500L
private const val CalibrationBeats = 8
private const val MinimumSamples = 5
private const val FirstBeatMs = 1500L
private const val BeatIntervalMs = 1100L
private const val SampleWindowMs = MAX_TIMING_OFFSET_MS
private const val NoteTravelMs = 800L
private const val NoteExitMs = 250L

internal class TimingTrial(private val startedAt: Long) {
    private val samples = mutableMapOf<Int, Long>()
    val sampleCount get() = samples.size
    val durationMs = FirstBeatMs + (CalibrationBeats - 1) * BeatIntervalMs + SampleWindowMs

    fun elapsedAt(now: Long): Long = (now - startedAt).coerceAtLeast(0)
    fun beatTime(index: Int): Long = FirstBeatMs + index * BeatIntervalMs

    fun record(now: Long): Long? {
        val elapsed = elapsedAt(now)
        val index = ((elapsed - FirstBeatMs).toDouble() / BeatIntervalMs).roundToInt()
        if (index !in 0 until CalibrationBeats || index in samples) return null
        val error = elapsed - beatTime(index)
        if (abs(error) > SampleWindowMs) return null
        samples[index] = error
        return error
    }

    fun offset(): Long? {
        if (samples.size < MinimumSamples) return null
        val ordered = samples.values.sorted()
        val center = (ordered[(ordered.size - 1) / 2] + ordered[ordered.size / 2]) / 2.0
        return center.roundToLong().coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
    }
}


@Composable
internal fun TimingCalibrationPage(
    offsetMs: Long,
    onOffsetChanged: (Long) -> Unit,
    canMeasure: Boolean,
    audioReady: Boolean,
    startAudio: () -> Boolean,
    stopAudio: () -> Unit,
    playCue: () -> Unit,
    onBack: () -> Unit
) {
    var trial by remember { mutableStateOf<TimingTrial?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var tapFeedback by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val measuring = trial != null

    LaunchedEffect(trial, canMeasure, audioReady) {
        val current = trial ?: return@LaunchedEffect
        if (!canMeasure || !audioReady) {
            result = "측정이 중단됐어요. 주차 상태와 소리를 확인하고 다시 시작하세요."
            trial = null
            stopAudio()
            return@LaunchedEffect
        }
        var nextCue = 0
        while (elapsed < current.durationMs) {
            elapsed = current.elapsedAt(SystemClock.elapsedRealtime())
            if (nextCue < CalibrationBeats && elapsed >= current.beatTime(nextCue)) {
                playCue()
                nextCue++
            }
            delay(16)
        }
        val measured = current.offset()
        if (measured != null) {
            onOffsetChanged(measured)
            result = "자동 보정 ${signedMs(measured)} 적용 완료"
        } else {
            result = "유효한 탭이 ${MinimumSamples}개 이상 필요해요. 다시 측정하세요."
        }
        trial = null
        stopAudio()
    }
    DisposableEffect(Unit) { onDispose { stopAudio() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = GameContentMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RhythmButton(primary = false, onClick = {
                trial = null
                stopAudio()
                onBack()
            }, modifier = Modifier.heightIn(min = 56.dp)) { Text("돌아가기", fontSize = 17.sp) }
            StaggeredHeading(
                "타이밍 보정", "화면을 누르는 시각과 노트 판정 사이의 차이를 맞춰요.",
                MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wideLayout = maxWidth >= 720.dp
                val manual: @Composable (Modifier) -> Unit = { modifier ->
                    Box(modifier.raisedSurface()) {
                        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("수동 보정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            RollingNumber(
                                signedMs(offsetMs), color = RhythmColors.Ink,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                listOf(-10L, -1L, 1L, 10L).forEach { step ->
                                    val next = (offsetMs + step).coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
                                    RhythmButton(
                                        primary = false,
                                        onClick = { onOffsetChanged(next) },
                                        enabled = !measuring && next != offsetMs,
                                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) { Text(if (step > 0) "+$step" else step.toString(), fontSize = 18.sp) }
                                }
                            }
                            Text("±${MAX_TIMING_OFFSET_MS}ms · 양수는 늦게 누르는 입력을 앞당겨 판정합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onOffsetChanged(0L) }, enabled = !measuring && offsetMs != 0L) {
                                Text("0 ms로 초기화")
                            }
                        }
                    }
                }
                val automatic: @Composable (Modifier) -> Unit = { modifier ->
                    Box(modifier.raisedSurface()) {
                        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("자동 보정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("노트가 판정선에 닿고 소리가 날 때 한 번씩 눌러 주세요.")
                            CalibrationLane(trial, elapsed, if (wideLayout) 300.dp else 220.dp)
                            if (measuring) {
                                Text("유효한 탭 $sampleCount / $CalibrationBeats")
                                Text(tapFeedback ?: "노트를 기다려 주세요.", color = MaterialTheme.colorScheme.primary)
                                RhythmButton(
                                    onClick = {
                                        val active = trial ?: return@RhythmButton
                                        val error = active.record(SystemClock.elapsedRealtime())
                                        sampleCount = active.sampleCount
                                        if (error != null) tapFeedback = "입력 오차 ${signedMs(error)}"
                                    },
                                    enabled = canMeasure && audioReady,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                ) { Text("박자에 맞춰 누르기", fontSize = 20.sp) }
                                TextButton(onClick = {
                                    trial = null
                                    stopAudio()
                                    result = "측정을 취소했어요."
                                }) { Text("측정 취소") }
                            } else {
                                result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                                if (!canMeasure) Text("주차가 확인되면 측정을 시작할 수 있어요.")
                                RhythmButton(
                                    onClick = {
                                        if (startAudio()) {
                                            elapsed = 0L
                                            sampleCount = 0
                                            tapFeedback = null
                                            result = null
                                            trial = TimingTrial(SystemClock.elapsedRealtime())
                                        } else {
                                            result = "소리를 재생할 수 없어요. 잠시 뒤 다시 시도하세요."
                                        }
                                    },
                                    enabled = canMeasure,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                ) { Text("자동 보정 시작", fontSize = 20.sp) }
                            }
                        }
                    }
                }
                if (wideLayout) {
                    Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                        manual(Modifier.weight(1f))
                        automatic(Modifier.weight(1.25f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        manual(Modifier.fillMaxWidth())
                        automatic(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationLane(trial: TimingTrial?, elapsed: Long, height: Dp) {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(height).insetSurface(RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))) {
        val line = size.height * .79f
        val left = size.width * .23f
        val right = size.width * .77f
        drawLine(accent.copy(alpha = .22f), Offset(left, 0f), Offset(left, size.height), 1.dp.toPx())
        drawLine(accent.copy(alpha = .22f), Offset(right, 0f), Offset(right, size.height), 1.dp.toPx())
        drawLine(accent, Offset(left, line), Offset(right, line), 3.dp.toPx())
        val noteAt = trial?.let { active ->
            (0 until CalibrationBeats).map(active::beatTime)
                .firstOrNull { elapsed in (it - NoteTravelMs)..(it + NoteExitMs) }
        }
        if (noteAt != null) {
            val y = line - (noteAt - elapsed).toFloat() / NoteTravelMs * line
            drawRoundRect(
                accent, Offset(size.width * .33f, y - 11.dp.toPx()),
                Size(size.width * .34f, 22.dp.toPx()), CornerRadius(8.dp.toPx())
            )
        }
    }
}

private fun signedMs(value: Long): String = if (value > 0) "+$value ms" else "$value ms"
