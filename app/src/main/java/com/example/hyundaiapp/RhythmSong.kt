package com.example.hyundaiapp

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

    fun chart(difficulty: ChartDifficulty): List<Long> = when (difficulty) {
        ChartDifficulty.EASY -> cues.filter { it.beat % 2 == 0 }.map(::cueTime)
        ChartDifficulty.NORMAL -> cues.map(::cueTime)
        ChartDifficulty.HARD -> cues.flatMap { cue ->
            val onset = cueTime(cue)
            val addOffbeat = when (this) {
                FAMILY_BEAT -> cue.beat % 2 == 0
                TWINKLE -> cue.beat % 8 in setOf(1, 3, 5)
            }
            if (addOffbeat) listOf(onset, onset + beatMs / 2) else listOf(onset)
        }
    }
}

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
