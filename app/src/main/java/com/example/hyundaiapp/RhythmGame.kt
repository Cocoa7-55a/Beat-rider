package com.example.hyundaiapp

import androidx.compose.runtime.*
import kotlin.math.abs

internal const val RESUME_COUNTDOWN_MS = 3000L
internal const val RESUME_LIFT_MS = 250L
private const val DRIVER_SEAT = 0
internal const val MAX_LIFE = 1000
internal const val JUDGE_FLASH_MS = 450L

internal enum class Judgment(val points: Int, val lifeCost: Int, val keepsCombo: Boolean) {
    PERFECT(100, 0, true),
    GREAT(80, 0, true),
    GOOD(50, 0, false),
    BAD(20, 50, false),
    MISS(0, 100, false)
}

/** 레인에 마지막으로 표시할 판정. [judgment]가 null이면 노트가 없는 곳을 친 것입니다. [atMs]는 곡 시간(elapsed) 기준. */
internal data class LaneJudge(val judgment: Judgment?, val atMs: Long)

data class BeatNote(val lane: Int, val time: Long, var judged: Boolean = false)
class RhythmGame(private val log: (String) -> Unit = {}) {
    var screen by mutableStateOf("landing")
    var running by mutableStateOf(false)
    var elapsed by mutableLongStateOf(0)
    var score by mutableIntStateOf(0)
        private set
    var life by mutableIntStateOf(MAX_LIFE)
        private set
    val maxScore get() = notes.size * Judgment.PERFECT.points
    val failed get() = life == 0 && !practice
    var combo by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)
    var hits by mutableIntStateOf(0)
    var feedback by mutableStateOf("")
        private set
    var practice by mutableStateOf(false)
    var noteSpeed by mutableFloatStateOf(1f)
    var resumeRemainingMs by mutableLongStateOf(0L)
        private set
    private var resumeAt = 0L
    private val readiness = mutableStateListOf(false, false, false, false)
    val readySeats: List<Boolean> get() = readiness
    val hasReadySeats get() = readiness.any { it }
    val soloReady get() = readiness[DRIVER_SEAT] && readiness.count { it } == 1
    var isSolo by mutableStateOf(false)
        private set
    var playingLanes by mutableStateOf(emptyList<Int>())
        private set

    fun setSeatReady(seat: Int, ready: Boolean) {
        readiness[seat] = ready
    }

    fun resetReadiness() {
        readiness.indices.forEach { readiness[it] = false }
    }
    internal val laneJudge = mutableStateListOf<LaneJudge?>(null, null, null, null)
    /** 판정별 노트 수. [Judgment.ordinal]로 색인합니다. */
    val judgments = mutableStateListOf(0, 0, 0, 0, 0)
    var notes = listOf<BeatNote>()
    var offsetMs by mutableLongStateOf(0)
    var timingOffsetMs by mutableLongStateOf(0)
    private var lastFrame = 0L
    private var segmentStart = 0L
    private var lastBeat = -1L
    val window get() = if (practice) 350L else 220L
    var song by mutableStateOf(RhythmSong.FAMILY_BEAT)
    var difficulty by mutableStateOf(ChartDifficulty.NORMAL)
    val duration get() = song.durationMs
    fun start(now: Long) {
        val seats = readiness.indices.filter { readiness[it] }
        if (seats.isEmpty()) return
        isSolo = soloReady
        playingLanes = if (isSolo) readiness.indices.toList() else seats
        notes = song.playNotes(difficulty, playingLanes)
        pause()
        elapsed = 0; score = 0; combo = 0; best = 0; hits = 0
        life = MAX_LIFE
        laneJudge.indices.forEach { laneJudge[it] = null }
        judgments.indices.forEach { judgments[it] = 0 }
        lastBeat = -1; lastFrame = now; segmentStart = now; running = true; screen = "play"
        feedback = ""
    }
    fun pause() {
        running = false
        resumeRemainingMs = 0
    }

    fun resume(now: Long) {
        if (screen != "play" || running || resumeRemainingMs > 0) return
        resumeRemainingMs = RESUME_LIFT_MS + RESUME_COUNTDOWN_MS
        resumeAt = now + resumeRemainingMs
    }

    fun tick(now: Long, recordingPositionMs: Long? = null, beat: () -> Unit) {
        if (resumeRemainingMs > 0) {
            resumeRemainingMs = (resumeAt - now).coerceAtLeast(0)
            if (resumeRemainingMs == 0L) {
                lastFrame = now
                segmentStart = now
                running = true
            }
            return
        }
        if (!running) return
        val synth = song.recordingAsset == null
        val frameDelta = (now - lastFrame).coerceAtLeast(0)
        elapsed = when {
            synth -> elapsed + frameDelta
            recordingPositionMs != null -> {
                if (recordingPositionMs >= song.musicDurationMs) {
                    duration
                } else {
                    val audioElapsed = song.countInMs + recordingPositionMs - timingOffsetMs
                    maxOf(elapsed, audioElapsed).coerceAtMost(duration)
                }
            }
            elapsed < song.countInMs + maxOf(0L, -timingOffsetMs) -> {
                (elapsed + frameDelta).coerceAtMost(song.countInMs + maxOf(0L, -timingOffsetMs))
            }
            else -> elapsed // wait for recording position
        }
        lastFrame = now
        // Synth: fire beat cues early so audio reaches ears on time
        val effectiveElapsed = (elapsed + if (synth) timingOffsetMs else 0L).coerceAtLeast(0L)
        val current = effectiveElapsed / song.beatMs
        if (current != lastBeat) { lastBeat = current; beat() }
        val latestInputOffset = if (isSolo) 0L else maxOf(0L, offsetMs)
        for (note in notes) {
            if (note.judged || elapsed <= note.time + window + latestInputOffset) continue
            judge(note, Judgment.MISS)
            if (failed) return
        }
        if (elapsed >= duration) finish()
    }
    fun hit(lane: Int, now: Long, vehicle: Boolean = false) {
        if (!running || lane !in playingLanes || now < segmentStart || (isSolo && vehicle)) return
        val at = elapsed + (now - lastFrame) - if (vehicle) offsetMs else 0L
        val note = notes.filter { !it.judged && it.lane == lane }.minByOrNull { abs(it.time - at) } ?: return
        val error = abs(note.time - at)
        if (error > window) {
            feedback = "박자를 기다려요"
            laneJudge[lane] = LaneJudge(null, elapsed)
            return
        }
        val scaledError = error * 220
        val judgment = when {
            scaledError <= 100 * window -> Judgment.PERFECT
            scaledError <= 140 * window -> Judgment.GREAT
            scaledError <= 180 * window -> Judgment.GOOD
            else -> Judgment.BAD
        }
        judge(note, judgment)
        log("HIT lane=$lane source=${if (vehicle) "light" else "touch"} errorMs=$error judgment=$judgment score=$score")
    }

    private fun judge(note: BeatNote, judgment: Judgment) {
        note.judged = true
        judgments[judgment.ordinal]++
        if (judgment != Judgment.MISS) hits++
        score += judgment.points
        combo = if (judgment.keepsCombo) combo + 1 else 0
        best = maxOf(best, combo)
        life = (life - judgment.lifeCost).coerceAtLeast(0)
        feedback = judgment.name
        laneJudge[note.lane] = LaneJudge(judgment, elapsed)
        if (failed) finish()
    }

    private fun finish() {
        pause()
        screen = "result"
    }
}
