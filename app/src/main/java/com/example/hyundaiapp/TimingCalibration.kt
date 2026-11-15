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
private const val MIN_TIMING_SAMPLES = 5
private const val FirstBeatMs = 1500L
private const val BeatIntervalMs = 1100L
private const val SampleWindowMs = MAX_TIMING_OFFSET_MS
private const val NoteTravelMs = 800L
private const val NoteExitMs = 250L

internal enum class TimingInput(val label: String, val preferenceKey: String) {
    TOUCH("화면 터치", "touch"),
    READING_LIGHT("독서등", "reading_light"),
    WINDOW("창문 움직임", "window")
}

internal data class TimingTarget(val seat: Int, val source: TimingInput) {
    val preferenceKey get() = "${source.preferenceKey}_seat_${seat}_ms"
}

internal class GroupTimingTrial(
    targets: List<TimingTarget>,
    startedAt: Long,
    private val audioOffsetMs: Long
) {
    private val trials = targets.associateWith { TimingTrial(startedAt) }
    val clock = trials.values.first()

    fun sampleCount(target: TimingTarget): Int? = trials[target]?.sampleCount

    fun record(target: TimingTarget, at: Long) {
        trials[target]?.record(at)
    }

    // Gameplay audio already leads by the shared offset, so only the remaining delay needs correction.
    fun offsets(stillSupported: (TimingTarget) -> Boolean): Map<TimingTarget, Long> = buildMap {
        for ((target, trial) in trials) {
            if (!stillSupported(target)) continue
            val measured = trial.offset() ?: continue
            put(target, (measured - audioOffsetMs).coerceIn(-MAX_TIMING_OFFSET_MS, MAX_TIMING_OFFSET_MS))
        }
    }
}

internal class TimingTrial(private val startedAt: Long) {
    private val samples = mutableStateMapOf<Int, Long>()
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
    inputSupported: (TimingTarget) -> Boolean,
    trial: GroupTimingTrial?,
    onStart: () -> Boolean,
    onFinish: (GroupTimingTrial) -> Map<TimingTarget, Long>,
    onCancel: () -> Unit,
    onTouch: (Int, Long) -> Unit,
    playCue: () -> Unit,
    onBack: () -> Unit
) {
    var selectedSeat by remember { mutableIntStateOf(DRIVER_SEAT) }
    var selectedInput by remember { mutableStateOf(TimingInput.TOUCH) }
    val target = TimingTarget(selectedSeat, selectedInput)
    val offsetMs = seatOffset(target.seat, target.source)
    var elapsed by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<String?>(null) }
    var updatedTargets by remember { mutableStateOf<Set<TimingTarget>?>(null) }
    val measuring = trial != null

    LaunchedEffect(trial, canMeasure) {
        val active = trial ?: return@LaunchedEffect
        if (!canMeasure) {
            result = "측정이 중단됐어요. 이전 값을 유지했어요."
            onCancel()
            return@LaunchedEffect
        }
        val current = active.clock
        var nextCue = 0
        while (elapsed < current.durationMs) {
            elapsed = current.elapsedAt(SystemClock.elapsedRealtime())
            if (nextCue < CalibrationBeats && elapsed >= current.beatTime(nextCue)) {
                playCue()
                nextCue++
            }
            delay(16)
        }
        val updated = onFinish(active)
        updatedTargets = updated.keys
        result = if (updated.isEmpty()) "유효 입력이 부족해 기존 값을 유지했어요."
        else "${updated.keys.map { it.seat }.distinct().size}자리 보정 완료"
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
                "타이밍 보정", "네 자리가 같은 8박에 맞춰 각자 입력해요.",
                MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wideLayout = maxWidth >= 720.dp
                val manual: @Composable (Modifier) -> Unit = { modifier ->
                    Box(modifier.raisedSurface()) {
                        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text("수동 보정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                seatNames.forEachIndexed { seat, name ->
                                    FilterChip(
                                        selected = target.seat == seat,
                                        onClick = { selectedSeat = seat },
                                        label = { Text("${seatIcons[seat]} $name") },
                                        enabled = !measuring
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimingInput.entries.forEach { source ->
                                    FilterChip(
                                        selected = target.source == source,
                                        onClick = { selectedInput = source },
                                        label = { Text(source.label) },
                                        enabled = !measuring
                                    )
                                }
                            }
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
                            Text("소리에 맞춰 자기 좌석 버튼을 누르거나 지원되는 독서등·창문을 움직여 주세요. 입력 방식마다 ${MIN_TIMING_SAMPLES}회 이상 필요해요.")
                            CalibrationLane(trial?.clock, elapsed, if (wideLayout) 165.dp else 125.dp)
                            CalibrationSeats(trial, inputSupported, seatOffset, updatedTargets, canMeasure, onTouch)
                            result?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                            if (measuring) {
                                TextButton(onClick = {
                                    onCancel()
                                    result = "측정을 취소했어요. 이전 값을 유지했어요."
                                }) { Text("측정 취소") }
                            } else {
                                if (!canMeasure) Text("주차가 확인되면 측정을 시작할 수 있어요.")
                                RhythmButton(
                                    onClick = {
                                        elapsed = 0L
                                        updatedTargets = null
                                        result = if (onStart()) null else "측정을 시작할 수 없어요. 잠시 뒤 다시 시도하세요."
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
                        automatic(Modifier.fillMaxWidth())
                        manual(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationSeats(
    trial: GroupTimingTrial?,
    inputSupported: (TimingTarget) -> Boolean,
    seatOffset: (Int, TimingInput) -> Long,
    updatedTargets: Set<TimingTarget>?,
    canMeasure: Boolean,
    onTouch: (Int, Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in seatNames.indices.chunked(2)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (seat in row) {
                    Column(
                        Modifier.weight(1f).insetSurface(ButtonShape).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        RhythmButton(
                            onClick = {},
                            onPress = { at -> onTouch(seat, at) },
                            enabled = trial != null && canMeasure,
                            color = seatColors[seat],
                            textColor = RhythmColors.Ink,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text("${seatIcons[seat]} ${seatNames[seat]}") }
                        for (source in TimingInput.entries) {
                            val target = TimingTarget(seat, source)
                            if (!inputSupported(target)) continue
                            val count = trial?.sampleCount(target)
                            val detail = if (count != null) "$count/$CalibrationBeats" else signedMs(seatOffset(seat, source))
                            val status = if (updatedTargets == null) "" else if (target in updatedTargets) " · 적용" else " · 유지"
                            Text("${source.label} $detail$status", style = MaterialTheme.typography.bodySmall)
                        }
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
