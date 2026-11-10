package com.example.hyundaiapp

import org.junit.Assert.*
import org.junit.Test

class RhythmGameTest {
    @Test fun allSeatsShareScoreAndDuplicateCannotScoreTwice() {
        val game = RhythmGame(); game.start(1000)
        repeat(32) { n ->
            val time = 3000L + n * 500
            game.tick(time) {}
            game.hit(n % 4, time, n % 2 == 0)
            game.hit(n % 4, time)
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
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), game.notes.take(8).map { it.seat })
        game.tick(19000) {}
        assertEquals("play", game.screen)
        game.tick(27000) {}
        assertEquals("result", game.screen)
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
