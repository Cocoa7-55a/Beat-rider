package com.example.hyundaiapp

import org.junit.Assert.*
import org.junit.Test

class RhythmGameTest {
    private fun readyGame(seats: List<Int> = (0..3).toList()) = RhythmGame().apply {
        seats.forEach { setSeatReady(it, true) }
    }

    @Test fun emptyReadinessBlocksStartAndTheLastSeatCanCancel() {
        val game = RhythmGame()
        game.screen = "home"
        assertEquals(listOf(false, false, false, false), game.readySeats)
        game.start(0)
        assertEquals("home", game.screen)
        assertTrue(game.notes.isEmpty())

        game.setSeatReady(2, true)
        assertTrue(game.hasReadySeats)
        game.setSeatReady(2, false)
        assertFalse(game.hasReadySeats)
        game.start(0)
        assertFalse(game.running)
    }

    @Test fun startingSnapshotsTheReadySeatsForTheWholeRound() {
        val game = readyGame(listOf(1, 3))
        game.start(0)
        game.resetReadiness()

        assertFalse(game.hasReadySeats)
        assertEquals(listOf(1, 3), game.playingLanes)
        assertEquals(setOf(1, 3), game.notes.map { it.lane }.toSet())
        val firstNote = game.notes.first()
        game.tick(firstNote.time) {}
        game.hit(firstNote.lane, firstNote.time)
        assertEquals(100, game.score)
    }

    @Test fun allSeatsShareScoreAndDuplicateCannotScoreTwice() {
        val game = readyGame(); game.start(1000)
        game.notes.forEachIndexed { index, note ->
            val time = 1000L + note.time
            game.tick(time) {}
            game.hit(note.lane, time, vehicle = index % 2 == 0)
            game.hit(note.lane, time)
        }
        game.tick(20000) {}
        assertEquals(3200, game.maxScore); assertEquals(game.maxScore, game.score); assertEquals(32, game.best)
        assertEquals("result", game.screen)
        assertEquals(listOf(32, 0, 0, 0, 0), game.judgments.toList())
    }
    @Test fun countdownFreezesSongAndRejectsInputsUntilResumed() {
        val game = readyGame()
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
        val game = readyGame()
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
        val game = readyGame(); game.start(0); game.tick(2100) {}
        game.hit(0, 2000, true)
        assertEquals(100, game.score)
    }
    @Test fun positiveLightOffsetDoesNotExpireNoteBeforeDelayedInput() {
        val game = readyGame(); game.offsetMs = 300; game.start(0)
        game.tick(2300) {}; game.hit(0, 2300, true)
        assertEquals(100, game.score)
    }
    @Test fun practiceHasWiderWindowAndMissResetsSharedCombo() {
        val normal = readyGame(); normal.start(0); normal.hit(0, 1700)
        assertEquals(0, normal.hits)
        val practice = readyGame(); practice.practice = true; practice.start(0)
        practice.hit(0, 1700); assertEquals(1, practice.hits)
        practice.tick(3000) {}; assertEquals(0, practice.combo)
    }
    @Test fun oneNonDriverPlayerGetsAllNotesInTheirOwnLane() {
        val game = readyGame(listOf(3)); game.start(0)
        assertFalse(game.isSolo)
        assertEquals(listOf(3), game.playingLanes)
        assertTrue(game.notes.all { it.lane == 3 })
    }

    @Test fun driverOnlyGetsFourTouchLanesForEveryChart() {
        val lanes = listOf(0, 1, 2, 3)
        val game = readyGame(listOf(0))
        assertTrue(game.soloReady)
        for (song in RhythmSong.entries) {
            for (difficulty in ChartDifficulty.entries) {
                game.song = song
                game.difficulty = difficulty
                game.start(0)
                assertTrue(game.isSolo)
                assertEquals(lanes, game.playingLanes)
                assertEquals(lanes.toSet(), game.notes.map { it.lane }.toSet())
                assertEquals(song.chart(difficulty), game.notes.map { it.time })

                for (note in game.notes) {
                    val position = if (song.recordingAsset != null) note.time - song.countInMs else null
                    game.tick(note.time, recordingPositionMs = position) {}
                    game.hit(note.lane, note.time, vehicle = true)
                    assertFalse(note.judged)
                    game.hit(note.lane, note.time)
                }
                assertEquals(game.maxScore, game.score)
            }
        }

        game.setSeatReady(3, true)
        assertTrue(game.isSolo)
        game.start(0)
        assertFalse(game.isSolo)
        assertEquals(listOf(0, 3), game.playingLanes)
    }
    @Test fun twinkleChartFollowsRecordedMelodyAndPlaybackClock() {
        val song = RhythmSong.TWINKLE
        val game = readyGame()
        game.song = song
        game.practice = true
        game.start(0)

        assertEquals(100, song.bpm)
        assertEquals(140, game.notes.size)
        assertEquals(listOf(0, 0, 7, 7, 9, 9, 7), song.cues.take(7).map { it.semitones })
        assertEquals(listOf(4200L, 4800L, 5400L, 6000L, 6600L, 7200L, 7800L, 9000L), game.notes.take(8).map { it.time })
        assertEquals(listOf(0, 1, 2, 3, 3, 2, 2, 2), game.notes.take(8).map { it.lane })
        assertTrue(listOf(33000L, 61800L, 90600L, 99000L).all { time -> game.notes.any { it.time == time } })

        game.tick(4000) {}
        assertEquals(2400L, game.elapsed)
        game.tick(6500, recordingPositionMs = 1800) {}
        assertEquals(4200L, game.elapsed)
        game.hit(game.notes.first().lane, 6500)
        assertEquals(100, game.score)
        game.pause()
        game.resume(7000)
        game.tick(10_249, recordingPositionMs = 1800) {}
        assertEquals(4200L, game.elapsed)
        game.tick(10_250) {}
        game.tick(10_300, recordingPositionMs = 1840) {}
        assertEquals(4240L, game.elapsed)

        game.tick(120_000, recordingPositionMs = song.musicDurationMs - 1) {}
        assertEquals("play", game.screen)
        game.tick(120_020, recordingPositionMs = song.musicDurationMs) {}
        assertEquals(111_331L, game.duration)
        assertEquals("result", game.screen)
    }

    @Test fun difficultyAddsNotesOnMeasuredPercussionInTheRecordedTrack() {
        val song = RhythmSong.TWINKLE
        assertEquals(listOf(80, 140, 176), ChartDifficulty.entries.map { song.chart(it).size })
        assertEquals(listOf(16, 32, 48), ChartDifficulty.entries.map { RhythmSong.FAMILY_BEAT.chart(it).size })

        val easy = song.chart(ChartDifficulty.EASY)
        val normal = song.chart(ChartDifficulty.NORMAL)
        val hard = song.chart(ChartDifficulty.HARD)
        assertTrue(normal.containsAll(easy))
        assertTrue(hard.containsAll(normal))
        assertEquals(hard.distinct().sorted(), hard)
        val accents = hard - normal.toSet()
        assertTrue(24900L in accents && 72900L in accents)
        assertTrue(accents.all { it in 23400L..61800L || it in 71400L..90600L })
        assertTrue(hard.last() < song.durationMs)
    }

    @Test fun gradesShareComboScoreAndLifeAcrossTouchAndVehicle() {
        val game = readyGame()
        game.start(0)
        val errors = listOf(100L, 140L, 180L, 220L)
        val expected = listOf(Judgment.PERFECT, Judgment.GREAT, Judgment.GOOD, Judgment.BAD)
        game.notes.take(errors.size).forEachIndexed { index, note ->
            val time = note.time + errors[index]
            game.tick(time) {}
            game.hit(note.lane, time, vehicle = index % 2 == 0)
            assertEquals(expected[index].name, game.feedback)
            assertEquals(listOf(1, 2, 0, 0)[index], game.combo)
            assertEquals(listOf(1000, 1000, 1000, 950)[index], game.life)
            assertEquals(listOf(100, 180, 230, 250)[index], game.score)
        }
        game.tick(game.notes[4].time + 221L) {}
        assertEquals("MISS", game.feedback)
        assertEquals(850, game.life)
        assertEquals(2, game.best)
    }

    @Test fun emptyLifeEndsStandardPlayAndResetsOnNewGame() {
        val game = readyGame()
        game.start(0)
        game.tick(game.notes[9].time + 221L) {}
        assertEquals(0, game.life)
        assertTrue(game.failed)
        assertFalse(game.running)
        assertEquals("result", game.screen)

        game.practice = true
        game.start(0)
        assertEquals(MAX_LIFE, game.life)
        game.tick(game.notes[9].time + 351L) {}
        assertEquals(0, game.life)
        assertTrue(game.running)
        assertEquals("play", game.screen)
    }

    @Test fun musicalPatternsUseOnlyParticipatingSeatsForEveryChart() {
        val seatSets = listOf(listOf(3), listOf(0, 3), listOf(0, 2, 3), listOf(0, 1, 2, 3))
        RhythmSong.entries.forEach { song ->
            ChartDifficulty.entries.forEach { difficulty ->
                seatSets.forEach { seats ->
                    val notes = song.playNotes(difficulty, seats)
                    assertEquals(song.chart(difficulty), notes.map { it.time })
                    assertEquals(seats.toSet(), notes.map { it.lane }.toSet())
                }
            }
        }
        val beat = RhythmSong.FAMILY_BEAT.playNotes(ChartDifficulty.NORMAL, seatSets.last())
        assertEquals(listOf(0, 2, 1, 0, 3, 1, 2, 3), beat.take(8).map { it.lane })
        val twinkle = RhythmSong.TWINKLE.playNotes(ChartDifficulty.HARD, seatSets.last())
        assertEquals(3, twinkle.single { it.time == 24900L }.lane)
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

    @Test fun timingOffsetSyncsAudioTimelineAndVehicleCompensatesInput() {
        val game = readyGame()
        game.song = RhythmSong.TWINKLE
        game.timingOffsetMs = 150
        game.offsetMs = 200
        game.start(0)

        // Recorded song audio clock is shifted by timingOffsetMs (countInMs = 2400):
        // 2400 + 1950 - 150 = 4200ms
        game.tick(6500, recordingPositionMs = 1950) {}
        assertEquals(4200L, game.elapsed)

        val first = game.notes.first()
        assertEquals(4200L, first.time)
        // Direct touch hits on the synced audio-visual line
        game.hit(first.lane, 6500)
        assertEquals(100, game.score)
        assertEquals(1, game.hits)

        // Vehicle reading light with 200ms delay arrives late, compensated by offsetMs = 200
        val second = game.notes[1]
        assertEquals(4800L, second.time)
        game.tick(7100, recordingPositionMs = 2550) {} // elapsed = 2400 + 2550 - 150 = 4800
        assertEquals(4800L, game.elapsed)
        game.hit(second.lane, 7300, vehicle = true)
        assertEquals(200, game.score)
        assertEquals(2, game.hits)
    }

    @Test fun timingOffsetShiftsSynthesizedBeatCuesEarly() {
        val game = readyGame()
        game.song = RhythmSong.FAMILY_BEAT // beatMs = 500
        game.timingOffsetMs = 100
        game.start(0)
        var beats = 0
        // At elapsed = 400ms, (400 + 100) / 500 = 1, so the beat fires 100ms early to reach the ear on beat
        game.tick(400) { beats++ }
        assertEquals(1, beats)
    }

    @Test fun familyBeatHitsOnLineRegardlessOfTimingOffset() {
        val game = readyGame()
        game.song = RhythmSong.FAMILY_BEAT
        game.timingOffsetMs = 120
        game.start(0)

        // First playable note is at 2000ms
        val note = game.notes.first()
        assertEquals(2000L, note.time)

        // Player sees the note on the line at elapsed = 2000ms and hits:
        game.tick(2000) {}
        assertEquals(2000L, game.elapsed)
        game.hit(note.lane, 2000)

        // Must be PERFECT without artificial judgment delay:
        assertEquals(100, game.score)
        assertEquals(1, game.hits)
        assertEquals(Judgment.PERFECT, game.laneJudge[note.lane]?.judgment)
    }

    @Test fun baselineDuplicatesAndReconnectAreExcluded() {
        val edges = SignalEdges<String>()
        assertFalse(edges.accept("OFF")); assertFalse(edges.accept("OFF"))
        assertTrue(edges.accept("ON")); assertFalse(edges.accept("ON"))
        assertTrue(edges.accept("OFF")); edges.reset()
        assertFalse(edges.accept("ON")); assertFalse(edges.accept(null))
        assertFalse(edges.accept("OFF"))
    }

    @Test fun laneJudgementIsRecordedPerLaneAndClearedOnStart() {
        val game = readyGame(); game.start(0)
        val note = game.notes.first()
        val other = (0..3).first { it != note.lane }
        game.tick(note.time) {}
        game.hit(note.lane, note.time)
        assertEquals(Judgment.PERFECT, game.laneJudge[note.lane]?.judgment)
        game.hit(other, note.time)
        assertNull(game.laneJudge[other]!!.judgment)
        game.tick(game.notes[1].time + 221L) {}
        assertEquals(Judgment.MISS, game.laneJudge[game.notes[1].lane]?.judgment)
        game.start(0)
        assertTrue(game.laneJudge.all { it == null })
    }
}
