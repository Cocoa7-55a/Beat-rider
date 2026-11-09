package com.example.hyundaiapp

import androidx.compose.runtime.*
import kotlin.math.abs

internal const val RESUME_COUNTDOWN_MS = 3000L

data class BeatNote(val seat: Int, val time: Long, var judged: Boolean = false)
class RhythmGame(private val log: (String) -> Unit = {}) {
    var screen by mutableStateOf("home")
    var running by mutableStateOf(false)
    var elapsed by mutableLongStateOf(0)
    var score by mutableIntStateOf(0)
    var combo by mutableIntStateOf(0)
    var best by mutableIntStateOf(0)
    var hits by mutableIntStateOf(0)
    var feedback by mutableStateOf("함께 박자를 맞춰요")
    var practice by mutableStateOf(false)
    var noteSpeed by mutableFloatStateOf(1f)
    var resumeRemainingMs by mutableLongStateOf(0L)
        private set
    private var resumeAt = 0L
    val active = mutableStateListOf(true, true, true, true)
    var notes = listOf<BeatNote>()
    var offsetMs by mutableLongStateOf(0)
    private var lastFrame = 0L
    private var segmentStart = 0L
    private var lastBeat = -1L
    val window get() = if (practice) 350L else 220L
    val duration = 19000L
    fun start(now: Long) {
        val seats = active.indices.filter { active[it] }
        if (seats.isEmpty()) return
        notes = (0 until 32).map { BeatNote(seats[it % seats.size], 2000L + it * 500L) }
        pause()
        elapsed = 0; score = 0; combo = 0; best = 0; hits = 0
        lastBeat = -1; lastFrame = now; segmentStart = now; running = true; screen = "play"
        feedback = "준비! 4박자 후 시작"
    }
    fun pause() {
        running = false
        resumeRemainingMs = 0
    }

    fun resume(now: Long) {
        if (screen != "play" || running || resumeRemainingMs > 0) return
        resumeAt = now + RESUME_COUNTDOWN_MS
        resumeRemainingMs = RESUME_COUNTDOWN_MS
    }

    fun tick(now: Long, beat: () -> Unit) {
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
        elapsed += (now - lastFrame).coerceAtLeast(0); lastFrame = now
        val current = elapsed / 500
        if (current != lastBeat) { lastBeat = current; beat() }
        notes.filter { !it.judged && elapsed > it.time + window + offsetMs.coerceAtLeast(0) }.forEach {
            it.judged = true; combo = 0; feedback = "괜찮아요, 다음 박자!"
        }
        if (elapsed >= duration) { running = false; screen = "result" }
    }
    fun hit(seat: Int, now: Long, vehicle: Boolean = false) {
        if (!running || seat !in active.indices || !active[seat] || now < segmentStart) return
        val at = elapsed + (now - lastFrame) - if (vehicle) offsetMs else 0
        val note = notes.filter { !it.judged && it.seat == seat }.minByOrNull { abs(it.time - at) } ?: return
        val error = abs(note.time - at)
        if (error > window) { feedback = "박자를 기다려요"; return }
        note.judged = true; hits++; combo++; best = maxOf(best, combo)
        score += if (error <= 100) 100 else 60
        feedback = if (error <= 100) "PERFECT · 함께 $combo 콤보!" else "GOOD · 함께 $combo 콤보!"
        log("HIT seat=$seat source=${if(vehicle) "light" else "touch"} errorMs=$error score=$score")
    }
}
