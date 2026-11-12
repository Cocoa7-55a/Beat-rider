package com.example.hyundaiapp

import org.junit.Assert.*
import org.junit.Test

class RhythmGameTest {
    @Test fun allSeatsShareScoreAndDuplicateCannotScoreTwice() {
        val game = RhythmGame(); game.start(1000)
        game.notes.forEachIndexed { index, note ->
            val time = 1000L + note.time
            game.tick(time) {}
            game.hit(note.seat, time, vehicle = index % 2 == 0)
            game.hit(note.seat, time)
        }
        game.tick(20000) {}
        assertEquals(3200, game.score); assertEquals(32, game.best)
        assertEquals("result", game.screen)
    }
    @Test fun countdownFreezesSongAndRejectsInputsUntilResumed() {
        val game = RhythmGame()
        game.start(0)
        game.tick(1900) {}
        game.pause()
        game.resume(10000)

        game.tick(10250) { fail("Resume preparation must not play a beat") }
        assertEquals(3000L, game.resumeRemainingMs)
        game.tick(13249) { fail("Countdown must not play a beat") }
        game.hit(0, 13249, vehicle = true)
        assertEquals(0, game.hits)
        assertEquals(1900L, game.elapsed)
        assertFalse(game.running)
        assertEquals(1L, game.resumeRemainingMs)

        game.tick(13250) {}
        assertTrue(game.running)
        assertEquals(0L, game.resumeRemainingMs)
        assertEquals(1900L, game.elapsed)
        game.hit(0, 13249, vehicle = true)
        assertEquals(0, game.hits)
        game.hit(0, 13350)
        assertEquals(1, game.hits)
    }

    @Test fun pausingAgainCancelsPendingResume() {
        val game = RhythmGame()
        game.start(0)
        game.pause()
        game.resume(1000)
        game.tick(2000) {}
        game.pause()
        game.tick(4000) {}

        assertEquals(0L, game.resumeRemainingMs)
        assertFalse(game.running)
    }
    @Test fun callbackReceiptTimeIsUsedEvenAfterNextFrame() {
        val game = RhythmGame(); game.start(0); game.tick(2100) {}
        game.hit(0, 2000, true)
        assertEquals(100, game.score)
    }
    @Test fun positiveLightOffsetDoesNotExpireNoteBeforeDelayedInput() {
        val game = RhythmGame(); game.offsetMs = 300; game.start(0)
        game.tick(2300) {}; game.hit(0, 2300, true)
        assertEquals(100, game.score)
    }
    @Test fun practiceHasWiderWindowAndMissResetsSharedCombo() {
        val normal = RhythmGame(); normal.start(0); normal.hit(0, 1700)
        assertEquals(0, normal.hits)
        val practice = RhythmGame(); practice.practice = true; practice.start(0)
        practice.hit(0, 1700); assertEquals(1, practice.hits)
        practice.tick(3000) {}; assertEquals(0, practice.combo)
    }
    @Test fun onePlayerGetsAllNotes() {
        val game = RhythmGame(); repeat(3) { game.active[it] = false }; game.start(0)
        assertTrue(game.notes.all { it.seat == 3 })
    }
    @Test fun twinkleChartsOnlyMelodyOnsetsAndUsesItsOwnEndTime() {
        val song = RhythmSong.TWINKLE
        val game = RhythmGame()
        game.song = song
        game.start(0)

        assertEquals(42, game.notes.size)
        assertEquals(listOf(0, 0, 7, 7, 9, 9, 7), song.cues.take(7).map { it.semitones })
        assertEquals(listOf(2000L, 2500L, 3000L, 3500L, 4000L, 4500L, 5000L, 6000L), game.notes.take(8).map { it.time })
        assertNull(song.cueAt(7))
        assertEquals(listOf(0, 1, 2, 3, 3, 2, 2, 2), game.notes.take(8).map { it.seat })
        game.tick(19000) {}
        assertEquals("play", game.screen)
        game.tick(27000) {}
        assertEquals("result", game.screen)
    }

    @Test fun difficultyChangesChartDensityWithoutChangingMelodyOrDuration() {
        val song = RhythmSong.TWINKLE
        assertEquals(listOf(24, 42, 60), ChartDifficulty.entries.map { song.chart(it).size })
        assertEquals(listOf(16, 32, 48), ChartDifficulty.entries.map { RhythmSong.FAMILY_BEAT.chart(it).size })

        val easy = song.chart(ChartDifficulty.EASY)
        val normal = song.chart(ChartDifficulty.NORMAL)
        val hard = song.chart(ChartDifficulty.HARD)
        assertTrue(normal.containsAll(easy))
        assertTrue(hard.containsAll(normal))
        assertEquals(hard.distinct().sorted(), hard)
        assertTrue(2750L in hard && 2750L !in normal)
        assertTrue(hard.none { it in 5001L..5999L })

        val game = RhythmGame()
        game.song = song
        game.difficulty = ChartDifficulty.HARD
        game.start(0)
        assertEquals(hard, game.notes.map { it.time })
        assertEquals(27000L, game.duration)
        assertEquals(0, song.cueAt(0)?.semitones)
    }

    @Test fun musicalPatternsUseOnlyParticipatingSeatsForEveryChart() {
        val seatSets = listOf(listOf(3), listOf(0, 3), listOf(0, 2, 3), listOf(0, 1, 2, 3))
        RhythmSong.entries.forEach { song ->
            ChartDifficulty.entries.forEach { difficulty ->
                seatSets.forEach { seats ->
                    val notes = song.playNotes(difficulty, seats)
                    assertEquals(song.chart(difficulty), notes.map { it.time })
                    assertEquals(seats.toSet(), notes.map { it.seat }.toSet())
                }
            }
        }
        val beat = RhythmSong.FAMILY_BEAT.playNotes(ChartDifficulty.NORMAL, seatSets.last())
        assertEquals(listOf(0, 2, 1, 0, 3, 1, 2, 3), beat.take(8).map { it.seat })
        val twinkle = RhythmSong.TWINKLE.playNotes(ChartDifficulty.HARD, seatSets.last())
        assertEquals(2, twinkle.single { it.time == 2750L }.seat)
    }

    @Test fun automaticTimingUsesMedianOfDistinctBeatsAndRequiresFiveTaps() {
        val start = 10000L
        val trial = TimingTrial(start)
        assertNull(trial.record(start))
        val errors = listOf(65L, 78L, 80L, 82L, 220L)
        errors.forEachIndexed { index, error ->
            val target = start + trial.beatTime(index)
            assertEquals(error, trial.record(target + error))
            assertNull(trial.record(target + error + 1))
            if (index < 4) assertNull(trial.offset())
        }
        assertEquals(5, trial.sampleCount)
        assertEquals(80L, trial.offset())
    }

    @Test fun automaticTimingAcceptsOffsetsAtBothFiveHundredMillisecondLimits() {
        val start = 10000L
        for (offset in listOf(-500L, 500L)) {
            val trial = TimingTrial(start)
            repeat(5) { index ->
                val time = start + trial.beatTime(index) + offset
                assertEquals(offset, trial.record(time))
            }
            assertEquals(offset, trial.offset())
        }
        val outside = TimingTrial(start)
        val firstBeat = start + outside.beatTime(0)
        assertNull(outside.record(firstBeat - 501))
        assertNull(outside.record(firstBeat + 501))
    }

    @Test fun storedTimingOffsetCombinesWithVehicleDelay() {
        val game = RhythmGame()
        game.timingOffsetMs = 160
        game.offsetMs = 200
        game.start(0)
        val first = game.notes.first()
        val second = game.notes[1]
        game.tick(first.time + 160) {}
        game.hit(first.seat, first.time + 160)
        game.tick(second.time + 360) {}
        game.hit(second.seat, second.time + 360, vehicle = true)
        assertEquals(200, game.score)
        assertEquals(2, game.hits)
    }

    @Test fun baselineDuplicatesAndReconnectAreExcluded() {
        val edges = SignalEdges<String>()
        assertFalse(edges.accept("OFF")); assertFalse(edges.accept("OFF"))
        assertTrue(edges.accept("ON")); assertFalse(edges.accept("ON"))
        assertTrue(edges.accept("OFF")); edges.reset()
        assertFalse(edges.accept("ON")); assertFalse(edges.accept(null))
        assertFalse(edges.accept("OFF"))
    }
}
