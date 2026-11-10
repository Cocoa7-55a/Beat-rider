package com.example.hyundaiapp

internal const val COUNT_IN_BEATS = 4

data class SongCue(val beat: Int, val semitones: Int? = null)

enum class RhythmSong(val title: String, val bpm: Int, musicBeats: Int, val cues: List<SongCue>) {
    FAMILY_BEAT("가족 비트", 120, 32, List(32) { SongCue(it) }),
    TWINKLE("반짝반짝 작은 별", 120, 48, twinkleCues());

    val beatMs = 60_000L / bpm
    val durationMs = (COUNT_IN_BEATS + musicBeats + 2) * beatMs
    private val cuesByBeat = cues.associateBy { it.beat }

    fun cueAt(songBeat: Int): SongCue? = cuesByBeat[songBeat]
    fun cueTime(cue: SongCue): Long = (COUNT_IN_BEATS + cue.beat) * beatMs
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
