package com.example.hyundaiapp

import androidx.compose.runtime.*
import kotlin.math.abs

internal const val RESUME_COUNTDOWN_MS = 3000L
internal const val RESUME_LIFT_MS = 250L
internal const val MAX_LIFE = 1000

internal enum class Judgment(val points: Int, val lifeCost: Int, val keepsCombo: Boolean) {
    PERFECT(100, 0, true),
    GREAT(80, 0, true),
    GOOD(50, 0, false),
    BAD(20, 50, false),
    MISS(0, 100, false)
}

data class BeatNote(val seat: Int, val time: Long, var judged: Boolean = false)
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
    var feedback by mutableStateOf("함께 박자를 맞춰요")
        private set
    var practice by mutableStateOf(false)
    var noteSpeed by mutableFloatStateOf(1f)
    var resumeRemainingMs by mutableLongStateOf(0L)
        private set
    private var resumeAt = 0L
    val active = mutableStateListOf(true, true, true, true)
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
        val seats = active.indices.filter { active[it] }
        if (seats.isEmpty()) return
        notes = song.playNotes(difficulty, seats)
        pause()
        elapsed = 0; score = 0; combo = 0; best = 0; hits = 0
        life = MAX_LIFE
        lastBeat = -1; lastFrame = now; segmentStart = now; running = true; screen = "play"
        feedback = "준비! 4박자 후 시작"
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
        val frameDelta = (now - lastFrame).coerceAtLeast(0)
        elapsed = when {
            song.recordingAsset == null -> elapsed + frameDelta
            recordingPositionMs != null -> maxOf(elapsed, song.countInMs + recordingPositionMs).coerceAtMost(duration)
            elapsed < song.countInMs -> (elapsed + frameDelta).coerceAtMost(song.countInMs)
            else -> elapsed
        }
        lastFrame = now
        val current = elapsed / song.beatMs
        if (current != lastBeat) { lastBeat = current; beat() }
        val latestInputOffset = maxOf(0L, timingOffsetMs, timingOffsetMs + offsetMs)
        for (note in notes) {
            if (note.judged || elapsed <= note.time + window + latestInputOffset) continue
            judge(note, Judgment.MISS)
            if (failed) return
        }
        if (elapsed >= duration) finish()
    }
    fun hit(seat: Int, now: Long, vehicle: Boolean = false) {
        if (!running || seat !in active.indices || !active[seat] || now < segmentStart) return
        val at = elapsed + (now - lastFrame) - timingOffsetMs - if (vehicle) offsetMs else 0L
        val note = notes.filter { !it.judged && it.seat == seat }.minByOrNull { abs(it.time - at) } ?: return
        val error = abs(note.time - at)
        if (error > window) {
            feedback = "박자를 기다려요"
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
        log("HIT seat=$seat source=${if (vehicle) "light" else "touch"} errorMs=$error judgment=$judgment score=$score")
    }

    private fun judge(note: BeatNote, judgment: Judgment) {
        note.judged = true
        if (judgment != Judgment.MISS) hits++
        score += judgment.points
        combo = if (judgment.keepsCombo) combo + 1 else 0
        best = maxOf(best, combo)
        life = (life - judgment.lifeCost).coerceAtLeast(0)
        feedback = judgment.name
        if (failed) finish()
    }

    private fun finish() {
        pause()
        screen = "result"
    }
}
