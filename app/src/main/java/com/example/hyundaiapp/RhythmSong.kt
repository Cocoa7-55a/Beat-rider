package com.example.hyundaiapp

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sign

internal const val COUNT_IN_BEATS = 4
private const val TWINKLE_FIRST_BEAT = 3
private const val PHRASE_BEATS = 8

data class SongCue(val beat: Int, val semitones: Int? = null)

enum class ChartDifficulty(val label: String, val description: String) {
    EASY("쉬움", "주요 박자에 맞춰 여유 있게 연주해요."),
    NORMAL("보통", "곡의 기본 박자와 멜로디를 따라 연주해요."),
    HARD("어려움", "기본 노트 사이에 반박 노트가 추가돼요.")
}

enum class RhythmSong(
    val title: String,
    val bpm: Int,
    val cues: List<SongCue>,
    internal val musicDurationMs: Long,
    internal val recordingAsset: String? = null,
    private val beatOffsetMs: Long = 0
) {
    FAMILY_BEAT("가족 비트", 120, List(32) { SongCue(it) }, 17_000),
    TWINKLE("반짝반짝 작은 별", 100, twinkleCues(), 108_931, "twinkle_twinkle.mp3"),
    JINGLE_BELLS("징글벨", 118, jingleBellsCues(), 71_236, "jingle_bells.mp3", beatOffsetMs = 75);

    val beatMs = 60_000L / bpm
    val countInMs = COUNT_IN_BEATS * beatMs
    val durationMs = countInMs + musicDurationMs
    private val cuesByBeat = cues.associateBy { it.beat }
    private val firstBeat = cues.first().beat

    private fun cueAt(songBeat: Int): SongCue? = cuesByBeat[songBeat]

    fun chart(difficulty: ChartDifficulty): List<Long> = steps(difficulty).map { timeOf(it) }

    internal fun playNotes(difficulty: ChartDifficulty, lanes: List<Int>): List<BeatNote> {
        val laneCount = lanes.size
        return steps(difficulty).map { step ->
            val beat = step.cue.beat
            val base = baseLane(step.cue, laneCount)
            val melodyBeat = beat - firstBeat
            val phrase = melodyBeat / PHRASE_BEATS
            val direction = if (phrase % 2 == 0) 1 else -1
            val pitch = step.cue.semitones
            val repeatedPitch = pitch != null && melodyBeat % PHRASE_BEATS != 0 && cueAt(beat - 1)?.semitones == pitch
            val onsetLane = if (laneCount > 1 && repeatedPitch) neighboringLane(base, direction, laneCount) else base
            val lane = if (step.offbeat && laneCount > 1) {
                val next = cueAt(beat + 1)?.let { baseLane(it, laneCount) } ?: onsetLane
                val stepDirection = if (next == onsetLane) direction else (next - onsetLane).sign
                neighboringLane(onsetLane, stepDirection, laneCount)
            } else {
                onsetLane
            }
            BeatNote(lanes[lane], timeOf(step))
        }
    }

    private data class Step(val cue: SongCue, val offbeat: Boolean = false)

    private fun timeOf(step: Step): Long {
        val beat = step.cue.beat + if (step.offbeat) 0.5 else 0.0
        return countInMs + beatOffsetMs + (beat * 60_000 / bpm).roundToLong()
    }

    private fun steps(difficulty: ChartDifficulty): List<Step> = when (difficulty) {
        ChartDifficulty.EASY -> cues.filter { (it.beat - firstBeat) % 2 == 0 }.map { Step(it) }
        ChartDifficulty.NORMAL -> cues.map { Step(it) }
        ChartDifficulty.HARD -> cues.flatMap { cue ->
            val addOffbeat = when (this) {
                FAMILY_BEAT -> cue.beat % 2 == 0
                JINGLE_BELLS -> cue.beat in jingleBellsOffbeats
                TWINKLE -> {
                    val beat = cue.beat - firstBeat
                    val phrase = beat / PHRASE_BEATS
                    val beatInPhrase = beat % PHRASE_BEATS
                    // Offbeat percussion enters at 21.0 s and returns at 69.0 s.
                    (phrase in 4..11 || phrase in 14..17) &&
                        (beatInPhrase == 2 || beatInPhrase == 4 || beatInPhrase == 6)
                }
            }
            if (addOffbeat) listOf(Step(cue), Step(cue, offbeat = true)) else listOf(Step(cue))
        }
    }

    private fun baseLane(cue: SongCue, laneCount: Int): Int {
        val pitch = cue.semitones
        val position = if (pitch != null) {
            // Spread the recording's pitch range across the lanes.
            pitch / if (this == JINGLE_BELLS) 12f else 9f
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

// NastelBom recording: the beat grid starts at 75 ms. Skip the short pickup and ending decay.
private fun jingleBellsCues(): List<SongCue> {
    val pitches = listOf(
        "4140", "2130", "5241", "4410", "2104", "4130", "5144", "6344",
        "0020", "2420", "5532", "2114", "0022", "7414", "5542", "2410",
        "4140", "0133", "5161", "4327", "2000", "2173", "5122", "5344",
        "2040", "2410", "3322", "2144", "2000", "0700", "3322", "440"
    ).joinToString("")
    val semitones = listOf(0, 2, 4, 5, 7, 9, 11, 12)
    return pitches.mapIndexed { index, pitch -> SongCue(index + 2, semitones[pitch.digitToInt()]) }
}

// Strong half-beat attacks measured in the recording, after the numbered beats.
private val jingleBellsOffbeats = setOf(
    2, 6, 7, 10, 14, 19, 21, 22, 23, 26, 27, 28, 29, 30, 34, 36, 38, 42,
    44, 45, 50, 52, 54, 58, 61, 62, 66, 69, 70, 71, 74, 78, 82, 85, 86, 90,
    92, 93, 94, 98, 99, 100, 102, 106, 108, 111, 114, 116, 118, 122, 124, 126
)

private fun twinkleCues(): List<SongCue> {
    // White_Records recording: first melody at 1.8 s, then three verses and an opening/answer outro.
    val opening = listOf(0, 0, 7, 7, 9, 9, 7)
    val answer = listOf(5, 5, 4, 4, 2, 2, 0)
    val middle = listOf(7, 7, 5, 5, 4, 4, 2)
    val verse = listOf(opening, answer, middle, middle, opening, answer)
    return (verse + verse + verse + listOf(opening, answer)).flatMapIndexed { phrase, pitches ->
        pitches.mapIndexed { beat, semitones ->
            SongCue(TWINKLE_FIRST_BEAT + phrase * PHRASE_BEATS + beat, semitones)
        }
    }
}
