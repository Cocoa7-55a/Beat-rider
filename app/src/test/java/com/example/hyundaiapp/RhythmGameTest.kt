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
    @Test fun pausedTimeAndQueuedOldInputDoNotScore() {
        val game = RhythmGame(); game.start(0); game.tick(1000) {}
        game.pause(); game.hit(0, 2000); game.tick(9000) {}
        assertEquals(0, game.hits); assertEquals(1000L, game.elapsed)
        game.resume(10000); game.hit(0, 2000)
        game.hit(0, 11000); assertEquals(1, game.hits)
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
    @Test fun baselineDuplicatesAndReconnectAreExcluded() {
        val edges = SignalEdges<String>()
        assertFalse(edges.accept("OFF")); assertFalse(edges.accept("OFF"))
        assertTrue(edges.accept("ON")); assertFalse(edges.accept("ON"))
        assertTrue(edges.accept("OFF")); edges.reset()
        assertFalse(edges.accept("ON")); assertFalse(edges.accept(null))
        assertFalse(edges.accept("OFF"))
    }
}
