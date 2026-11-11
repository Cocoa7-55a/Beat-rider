package com.example.hyundaiapp

import kotlin.math.roundToInt
import kotlin.math.sign

internal const val COUNT_IN_BEATS = 4

data class SongCue(val beat: Int, val semitones: Int? = null)

enum class ChartDifficulty(val label: String, val description: String) {
    EASY("쉬움", "주요 박자에 맞춰 여유 있게 연주해요."),
    NORMAL("보통", "곡의 기본 박자와 멜로디를 따라 연주해요."),
    HARD("어려움", "기본 노트 사이에 반박 노트가 추가돼요.")
}

enum class RhythmSong(val title: String, val bpm: Int, musicBeats: Int, val cues: List<SongCue>) {
    FAMILY_BEAT("가족 비트", 120, 32, List(32) { SongCue(it) }),
    TWINKLE("반짝반짝 작은 별", 120, 48, twinkleCues());

    val beatMs = 60_000L / bpm
    val durationMs = (COUNT_IN_BEATS + musicBeats + 2) * beatMs
    private val cuesByBeat = cues.associateBy { it.beat }

    fun cueAt(songBeat: Int): SongCue? = cuesByBeat[songBeat]
    fun cueTime(cue: SongCue): Long = (COUNT_IN_BEATS + cue.beat) * beatMs

    fun chart(difficulty: ChartDifficulty): List<Long> = steps(difficulty).map { timeOf(it) }

    internal fun playNotes(difficulty: ChartDifficulty, seats: List<Int>): List<BeatNote> {
        val laneCount = seats.size
        return steps(difficulty).map { step ->
            val beat = step.cue.beat
            val base = baseLane(step.cue, laneCount)
            val phrase = beat / 8
            val direction = if (phrase % 2 == 0) 1 else -1
            val pitch = step.cue.semitones
            val repeatedPitch = pitch != null && beat % 8 != 0 && cueAt(beat - 1)?.semitones == pitch
            val onsetLane = if (laneCount > 1 && repeatedPitch) neighboringLane(base, direction, laneCount) else base
            val lane = if (step.offbeat && laneCount > 1) {
                val next = cueAt(beat + 1)?.let { baseLane(it, laneCount) } ?: onsetLane
                val stepDirection = if (next == onsetLane) direction else (next - onsetLane).sign
                neighboringLane(onsetLane, stepDirection, laneCount)
            } else {
                onsetLane
            }
            BeatNote(seats[lane], timeOf(step))
        }
    }

    private data class Step(val cue: SongCue, val offbeat: Boolean = false)

    private fun timeOf(step: Step): Long = cueTime(step.cue) + if (step.offbeat) beatMs / 2 else 0L

    private fun steps(difficulty: ChartDifficulty): List<Step> = when (difficulty) {
        ChartDifficulty.EASY -> cues.filter { it.beat % 2 == 0 }.map { Step(it) }
        ChartDifficulty.NORMAL -> cues.map { Step(it) }
        ChartDifficulty.HARD -> cues.flatMap { cue ->
            val addOffbeat = when (this) {
                FAMILY_BEAT -> cue.beat % 2 == 0
                TWINKLE -> when (cue.beat % 8) {
                    1, 3, 5 -> true
                    else -> false
                }
            }
            if (addOffbeat) listOf(Step(cue), Step(cue, offbeat = true)) else listOf(Step(cue))
        }
    }

    private fun baseLane(cue: SongCue, laneCount: Int): Int {
        val pitch = cue.semitones
        val position = if (pitch != null) {
            // Map the C–A melody range from left to right.
            pitch / 9f
        } else {
            // Alternate mirrored phrases while keeping the strong beats distinct.
            val beatLane = familyBeatLanes[cue.beat % familyBeatLanes.size]
            val lane = if ((cue.beat / familyBeatLanes.size) % 2 == 0) beatLane else 3 - beatLane
            lane / 3f
        }
        return (position * (laneCount - 1)).roundToInt()
    }

    private fun neighboringLane(lane: Int, direction: Int, laneCount: Int): Int {
        val next = lane + direction
        return if (next in 0 until laneCount) next else lane - direction
    }
}

private val familyBeatLanes = listOf(0, 2, 1, 0, 3, 1, 2, 3)

private fun twinkleCues(): List<SongCue> {
    // Six phrases of six quarter notes and one half note, in C major.
    val opening = listOf(0, 0, 7, 7, 9, 9, 7)
    val answer = listOf(5, 5, 4, 4, 2, 2, 0)
    val middle = listOf(7, 7, 5, 5, 4, 4, 2)
    return listOf(opening, answer, middle, middle, opening, answer)
        .flatMapIndexed { phrase, pitches ->
            pitches.mapIndexed { beat, semitones -> SongCue(phrase * 8 + beat, semitones) }
        }
}
