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
internal const val MIN_TIMING_SAMPLES = 5
private const val FirstBeatMs = 1500L
private const val BeatIntervalMs = 1100L
private const val SampleWindowMs = MAX_TIMING_OFFSET_MS
private const val NoteTravelMs = 800L
private const val NoteExitMs = 250L

internal enum class TimingInput(val label: String, val preferenceKey: String) {
    TOUCH("화면 터치", "touch"),
    READING_LIGHT("독서등", "reading_light")
}

internal data class TimingTarget(val seat: Int, val source: TimingInput) {
    val preferenceKey get() = "${source.preferenceKey}_seat_${seat}_ms"
}

internal class SeatTimingTrial(val target: TimingTarget, startedAt: Long, private val audioOffsetMs: Long) {
    val timing = TimingTrial(startedAt)
    var sampleCount by mutableIntStateOf(0)
        private set
    var lastError by mutableStateOf<Long?>(null)
        private set

    fun record(input: TimingTarget, at: Long) {
        if (input != target) return
        val error = timing.record(at) ?: return
        sampleCount = timing.sampleCount
        lastError = error
    }

    // Gameplay audio already leads by the shared offset, so only the remaining delay needs correction.
    fun offset(): Long? = timing.offset()?.let { measured ->
        (measured - audioOffsetMs).coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
    }
}

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
        if (samples.size < MIN_TIMING_SAMPLES) return null
        val ordered = samples.values.sorted()
        val center = (ordered[(ordered.size - 1) / 2] + ordered[ordered.size / 2]) / 2.0
        return center.roundToLong().coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS)
    }
}


@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun TimingCalibrationPage(
    seatOffset: (Int, TimingInput) -> Long,
    onOffsetChanged: (TimingTarget, Long) -> Unit,
    canMeasure: Boolean,
    lightSupported: (Int) -> Boolean,
    trial: SeatTimingTrial?,
    onStart: (TimingTarget) -> Boolean,
    onFinish: (SeatTimingTrial) -> String,
    onCancel: () -> Unit,
    onTouch: (Int, Long) -> Unit,
    playCue: () -> Unit,
    onBack: () -> Unit
) {
    var selectedSeat by remember { mutableIntStateOf(DRIVER_SEAT) }
    var selectedInput by remember { mutableStateOf(TimingInput.TOUCH) }
    val target = trial?.target ?: TimingTarget(selectedSeat, selectedInput)
    val offsetMs = seatOffset(target.seat, target.source)
    var elapsed by remember { mutableLongStateOf(0L) }
    var result by remember(target) { mutableStateOf<String?>(null) }
    val measuring = trial != null
    val supported = target.source == TimingInput.TOUCH || lightSupported(target.seat)

    LaunchedEffect(trial, canMeasure, supported) {
        val active = trial ?: return@LaunchedEffect
        if (!canMeasure || !supported) {
            result = "측정이 중단됐어요. 이전 값을 유지했어요."
            onCancel()
            return@LaunchedEffect
        }
        val current = active.timing
        var nextCue = 0
        while (elapsed < current.durationMs) {
            elapsed = current.elapsedAt(SystemClock.elapsedRealtime())
            if (nextCue < CalibrationBeats && elapsed >= current.beatTime(nextCue)) {
                playCue()
                nextCue++
            }
            delay(16)
        }
        result = onFinish(active)
    }
    DisposableEffect(Unit) { onDispose { onCancel() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = GameContentMaxWidth).fillMaxWidth().verticalScroll(rememberScrollState()).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RhythmButton(primary = false, onClick = {
                onCancel()
                onBack()
            }, modifier = Modifier.heightIn(min = 56.dp)) { Text("돌아가기", fontSize = 17.sp) }
            StaggeredHeading(
                "타이밍 보정", "좌석과 입력 방식을 골라 판정 타이밍을 맞춰요.",
                MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                seatNames.forEachIndexed { seat, name ->
                    FilterChip(
                        selected = target.seat == seat,
                        onClick = { selectedSeat = seat },
                        label = { Text("${seatIcons[seat]} $name") },
                        enabled = !measuring
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimingInput.entries.forEach { source ->
                    FilterChip(
                        selected = target.source == source,
                        onClick = { selectedInput = source },
                        label = { Text(source.label) },
                        enabled = !measuring
                    )
                }
            }
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
                                        onClick = { onOffsetChanged(target, next) },
                                        enabled = !measuring && next != offsetMs,
                                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) { Text(if (step > 0) "+$step" else step.toString(), fontSize = 18.sp) }
                                }
                            }
                            Text("±${MAX_TIMING_OFFSET_MS}ms · 양수는 늦게 들어오는 입력을 앞선 시각으로 판정해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { onOffsetChanged(target, 0L) }, enabled = !measuring && offsetMs != 0L) {
                                Text("0 ms로 초기화")
                            }
                        }
                    }
                }
                val automatic: @Composable (Modifier) -> Unit = { modifier ->
                    Box(modifier.raisedSurface()) {
                        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("자동 보정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (target.source == TimingInput.TOUCH) "소리에 맞춰 화면 버튼을 한 번씩 눌러 주세요."
                                else "소리에 맞춰 ${seatNames[target.seat]} 독서등을 한 번씩 켜거나 꺼 주세요."
                            )
                            CalibrationLane(trial?.timing, elapsed, if (wideLayout) 250.dp else 200.dp)
                            if (trial != null) {
                                Text("유효한 입력 ${trial.sampleCount} / $CalibrationBeats")
                                Text(
                                    trial.lastError?.let { "입력 오차 ${signedMs(it)}" } ?: "첫 박자를 기다려 주세요.",
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (target.source == TimingInput.TOUCH) {
                                    RhythmButton(
                                        onClick = {},
                                        onPress = { at -> onTouch(target.seat, at) },
                                        enabled = canMeasure,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
                                    ) { Text("박자에 맞춰 누르기", fontSize = 20.sp) }
                                }
                                TextButton(onClick = {
                                    onCancel()
                                    result = "측정을 취소했어요. 이전 값을 유지했어요."
                                }) { Text("측정 취소") }
                            } else {
                                result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                                if (!canMeasure) Text("주차가 확인되면 측정을 시작할 수 있어요.")
                                if (!supported) Text("이 자리의 독서등을 읽을 수 없어요. 화면 터치를 선택해 주세요.")
                                RhythmButton(
                                    onClick = {
                                        elapsed = 0L
                                        result = if (onStart(target)) null else "측정을 시작할 수 없어요. 잠시 뒤 다시 시도하세요."
                                    },
                                    enabled = canMeasure && supported,
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

internal fun signedMs(value: Long): String = if (value > 0) "+$value ms" else "$value ms"
