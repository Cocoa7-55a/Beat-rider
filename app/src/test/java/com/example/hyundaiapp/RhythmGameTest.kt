package com.example.hyundaiapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class RhythmGameTest {
    private fun readyGame(seats: List<Int> = (0..3).toList()) = RhythmGame().apply {
        seats.forEach { setSeatReady(it, true) }
    }

    private fun RhythmGame.tickAtSongTime(time: Long, now: Long = time) {
        tick(now, recordingPositionMs = time - song.countInMs) {}
    }

    @Test fun touchUsesTheVisiblePerspectiveLaneBoundaries() {
        for (count in 1..4) {
            val track = PerspectiveTrack(Size(1000f, 600f), count, 108f, if (count == 1) .66f else 1f)
            for (y in listOf(track.judgmentY, 540f, 599f)) {
                assertNull(track.inputColumnAt(track.point(0f, y) - Offset(1f, 0f)))
                assertNull(track.inputColumnAt(track.point(count.toFloat(), y) + Offset(1f, 0f)))
                for (lane in 0 until count) {
                    assertEquals(lane, track.inputColumnAt(track.point(lane.toFloat(), y) + Offset(1f, 0f)))
                    assertEquals(lane, track.inputColumnAt(track.point(lane + 1f, y) - Offset(1f, 0f)))
                }
            }
            assertNull(track.inputColumnAt(track.point(.5f, track.judgmentY - 1f)))
        }
    }

    @Test fun laneIntroEndsBeforeNotesEnterAtEverySupportedSpeed() {
        val game = readyGame(listOf(0))
        for (song in RhythmSong.entries) {
            for (speed in listOf(.5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f)) {
                game.song = song
                game.noteSpeed = speed
                game.start(0, laneIntroMs = LANE_INTRO_MS)
                assertEquals(LANE_INTRO_MS, game.laneIntroEndsAt - game.elapsed)
                assertTrue(game.notes.first().time - game.laneIntroEndsAt >= game.noteTravelMs)

                game.tick(LANE_INTRO_MS - 1) { fail("The lead-in must be silent") }
                game.hit(game.notes.first().lane, LANE_INTRO_MS - 1)
                assertEquals("", game.feedback)
                assertEquals(game.laneIntroEndsAt - 1, game.elapsed)
                game.tick(LANE_INTRO_MS) {}
                assertEquals(game.laneIntroEndsAt, game.elapsed)
            }
        }
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
        game.tickAtSongTime(firstNote.time)
        game.hit(firstNote.lane, firstNote.time)
        assertEquals(100, game.score)
    }

    @Test fun allSeatsShareScoreAndDuplicateCannotScoreTwice() {
        val game = readyGame()
        game.start(1000, vehicleInputs = (0..3).map { TimingTarget(it, TimingInput.READING_LIGHT) }.toSet())
        game.notes.forEachIndexed { index, note ->
            val time = 1000L + note.time
            game.tickAtSongTime(note.time, now = time)
            game.hit(note.lane, time, if (index % 2 == 0) TimingInput.READING_LIGHT else TimingInput.TOUCH)
            game.hit(note.lane, time)
        }
        game.tickAtSongTime(game.duration, now = 1000L + game.duration)
        assertEquals(14000, game.maxScore)
        assertEquals(game.maxScore, game.score)
        assertEquals(140, game.best)
        assertEquals("result", game.screen)
        assertEquals(listOf(140, 0, 0, 0, 0), game.judgments.toList())
    }
    @Test fun countdownFreezesSongAndRejectsInputsUntilResumed() {
        val game = readyGame()
        game.start(0)
        game.tickAtSongTime(4100)
        game.pause()
        game.resume(10000)

        game.tick(10250) { fail("Resume preparation must not play a beat") }
        assertEquals(3000L, game.resumeRemainingMs)
        game.tick(13249) { fail("Countdown must not play a beat") }
        game.hit(0, 13249, source = TimingInput.READING_LIGHT)
        assertEquals(0, game.hits)
        assertEquals(4100L, game.elapsed)
        assertFalse(game.running)
        assertEquals(1L, game.resumeRemainingMs)

        game.tick(13250) {}
        assertTrue(game.running)
        assertEquals(0L, game.resumeRemainingMs)
        assertEquals(4100L, game.elapsed)
        game.hit(0, 13249, source = TimingInput.READING_LIGHT)
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
        val game = readyGame()
        game.start(0, vehicleInputs = setOf(TimingTarget(0, TimingInput.READING_LIGHT)))
        game.tickAtSongTime(4300)
        game.hit(0, 4200, TimingInput.READING_LIGHT)
        assertEquals(100, game.score)
    }
    @Test fun positiveLightOffsetDoesNotExpireNoteBeforeDelayedInput() {
        val game = readyGame()
        game.setSeatOffset(0, TimingInput.READING_LIGHT, 300)
        game.start(0, vehicleInputs = setOf(TimingTarget(0, TimingInput.READING_LIGHT)))
        game.tickAtSongTime(4500); game.hit(0, 4500, TimingInput.READING_LIGHT)
        assertEquals(100, game.score)
    }
    @Test fun practiceHasWiderWindowAndMissResetsSharedCombo() {
        val normal = readyGame(); normal.start(0); normal.hit(0, 3900)
        assertEquals(0, normal.hits)
        val practice = readyGame(); practice.practice = true; practice.start(0)
        practice.hit(0, 3900); assertEquals(1, practice.hits)
        practice.tickAtSongTime(5600); assertEquals(0, practice.combo)
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
                    game.tickAtSongTime(note.time)
                    game.hit(note.lane, note.time, source = TimingInput.READING_LIGHT)
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

    @Test fun jingleBellsChartTracksTheRecordingWithoutBeatRoundingDrift() {
        val song = RhythmSong.JINGLE_BELLS
        val easy = song.chart(ChartDifficulty.EASY)
        val normal = song.chart(ChartDifficulty.NORMAL)
        val hard = song.chart(ChartDifficulty.HARD)

        assertEquals(listOf(64, 127, 179), listOf(easy.size, normal.size, hard.size))
        assertTrue(normal.containsAll(easy))
        assertTrue(hard.containsAll(normal))
        assertEquals(hard.distinct().sorted(), hard)
        assertEquals(3124L, normal.first())
        assertEquals(67192L, normal.last())
        assertEquals(3378L, hard[1])
        assertEquals(73268L, song.durationMs)

        val game = readyGame(listOf(0))
        game.song = song
        game.practice = true
        game.start(0)
        game.tick(4000, recordingPositionMs = normal.first() - song.countInMs) {}
        game.hit(game.notes.first().lane, 4000)
        assertEquals(100, game.score)
        game.tick(74999, recordingPositionMs = song.musicDurationMs - 1) {}
        assertEquals("play", game.screen)
        game.tick(75000, recordingPositionMs = song.musicDurationMs) {}
        assertEquals("result", game.screen)
    }

    @Test fun difficultyAddsNotesOnMeasuredPercussionInTheRecordedTrack() {
        val song = RhythmSong.TWINKLE
        assertEquals(listOf(80, 140, 176), ChartDifficulty.entries.map { song.chart(it).size })

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
        game.start(0, vehicleInputs = (0..3).map { TimingTarget(it, TimingInput.READING_LIGHT) }.toSet())
        val errors = listOf(100L, 140L, 180L, 220L)
        val expected = listOf(Judgment.PERFECT, Judgment.GREAT, Judgment.GOOD, Judgment.BAD)
        game.notes.take(errors.size).forEachIndexed { index, note ->
            val time = note.time + errors[index]
            game.tickAtSongTime(time)
            game.hit(note.lane, time, if (index % 2 == 0) TimingInput.READING_LIGHT else TimingInput.TOUCH)
            assertEquals(expected[index].name, game.feedback)
            assertEquals(listOf(1, 2, 0, 0)[index], game.combo)
            assertEquals(listOf(1000, 1000, 1000, 950)[index], game.life)
            assertEquals(listOf(100, 180, 230, 250)[index], game.score)
        }
        game.tickAtSongTime(game.notes[4].time + 221L)
        assertEquals("MISS", game.feedback)
        assertEquals(850, game.life)
        assertEquals(2, game.best)
    }

    @Test fun emptyLifeEndsStandardPlayAndResetsOnNewGame() {
        val game = readyGame()
        game.start(0)
        game.tickAtSongTime(game.notes[9].time + 221L)
        assertEquals(0, game.life)
        assertTrue(game.failed)
        assertFalse(game.running)
        assertEquals("result", game.screen)

        game.practice = true
        game.start(0)
        assertEquals(MAX_LIFE, game.life)
        game.tickAtSongTime(game.notes[9].time + 351L)
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

    @Test fun windowMovementFiltersInitialValuesDuplicatesAndContinuousTravel() {
        val movement = WindowMovement()
        assertFalse(movement.accept(0, 0))
        assertFalse(movement.accept(0, 100))
        assertTrue(movement.accept(2, 200))
        assertFalse(movement.accept(7, 260))
        assertFalse(movement.accept(9, 450))
        assertFalse(movement.accept(9, 750))
        assertTrue(movement.accept(6, 900))
        assertFalse(movement.accept(null, 1000))
        assertFalse(movement.accept(4, 1200))
        assertTrue(movement.accept(3, 1300))
    }

    @Test fun windowUsesItsOwnCalibrationAndOnlyEntersVerifiedLanes() {
        val game = readyGame(listOf(0, 1))
        val window = TimingTarget(1, TimingInput.WINDOW)
        game.setSeatOffset(1, TimingInput.WINDOW, 300)
        game.setSeatOffset(1, TimingInput.READING_LIGHT, 20)
        game.start(0, vehicleInputs = setOf(window))
        val first = game.notes.first { it.lane == 1 }
        game.tickAtSongTime(first.time + 300)
        game.hit(1, first.time + 300, TimingInput.READING_LIGHT)
        assertFalse(first.judged)
        game.hit(1, first.time + 300, TimingInput.WINDOW)
        assertTrue(first.judged)
        assertEquals(Judgment.PERFECT, game.laneJudge[1]?.judgment)

        game.start(0)
        game.tickAtSongTime(first.time)
        game.hit(1, first.time, TimingInput.WINDOW)
        assertEquals(0, game.score)
    }

    @Test fun groupCalibrationSamplesFourSeatsOnTheSameBeatsAndKeepsOtherOffsets() {
        val start = 10000L
        val touch = (0..3).map { TimingTarget(it, TimingInput.TOUCH) }
        val light = TimingTarget(1, TimingInput.READING_LIGHT)
        val window = TimingTarget(2, TimingInput.WINDOW)
        val unsupported = TimingTarget(3, TimingInput.WINDOW)
        val group = GroupTimingTrial(touch + listOf(light, window), start, audioOffsetMs = 30)

        repeat(5) { index ->
            val beat = start + group.clock.beatTime(index)
            touch.forEachIndexed { seat, target ->
                group.record(target, beat + seat * 50L)
                group.record(target, beat + seat * 50L + 4)
            }
            group.record(light, beat + 50)
            group.record(unsupported, beat)
            if (index < 4) group.record(window, beat + 100)
        }

        val expected = touch.mapIndexed { seat, target -> target to seat * 50L - 30 }.toMap()
        assertEquals(expected + (light to 20L), group.offsets { true })
        assertEquals(expected, group.offsets { it.source == TimingInput.TOUCH })
        assertEquals(listOf(5, 5, 5, 5), touch.map(group::sampleCount))
        assertEquals(4, group.sampleCount(window))
        assertNull(group.sampleCount(unsupported))

        val emptyRun = GroupTimingTrial(touch, start, audioOffsetMs = 30)
        assertTrue(emptyRun.offsets { true }.isEmpty())
    }

    @Test fun gameplayUsesEachSeatsOwnOffsetAndTheDriverOffsetForAllSoloLanes() {
        val game = readyGame()
        game.setSeatOffset(0, TimingInput.TOUCH, 280)
        game.setSeatOffset(1, TimingInput.READING_LIGHT, 300)
        game.setSeatOffset(3, TimingInput.TOUCH, 500)
        game.start(0, vehicleInputs = setOf(TimingTarget(1, TimingInput.READING_LIGHT)))
        val first = game.notes[0]
        val second = game.notes[1]
        game.tickAtSongTime(first.time + 280)
        game.hit(first.lane, first.time + 280)
        assertEquals(Judgment.PERFECT, game.laneJudge[0]?.judgment)
        game.tickAtSongTime(second.time + 300)
        game.hit(second.lane, second.time + 300, source = TimingInput.READING_LIGHT)
        assertEquals(Judgment.PERFECT, game.laneJudge[1]?.judgment)

        val third = game.notes[2]
        assertEquals(2, third.lane)
        game.tickAtSongTime(third.time + game.window + 1)
        assertEquals(Judgment.MISS, game.laneJudge[2]?.judgment)

        game.resetReadiness()
        game.setSeatReady(0, true)
        game.start(0)
        val soloNote = game.notes.first { it.lane == 3 }
        game.tickAtSongTime(soloNote.time + 280)
        game.hit(soloNote.lane, soloNote.time + 280)
        assertEquals(Judgment.PERFECT, game.laneJudge[3]?.judgment)
    }

    @Test fun timingOffsetSyncsAudioTimelineAndVehicleCompensatesInput() {
        val game = readyGame()
        game.song = RhythmSong.TWINKLE
        game.timingOffsetMs = 150
        game.setSeatOffset(1, TimingInput.READING_LIGHT, 200)
        game.start(0, vehicleInputs = setOf(TimingTarget(1, TimingInput.READING_LIGHT)))

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
        game.hit(second.lane, 7300, source = TimingInput.READING_LIGHT)
        assertEquals(200, game.score)
        assertEquals(2, game.hits)
    }

    @Test fun recordedSongsPlayFourCountInBeatsAtBothOffsetLimits() {
        for (song in RhythmSong.entries) {
            for (offset in listOf(-500L, 0L, 500L)) {
                val game = readyGame()
                game.song = song
                game.timingOffsetMs = offset
                game.start(0, laneIntroMs = LANE_INTRO_MS)
                val beats = mutableListOf<Int>()
                val start = game.elapsed
                val recordAt = song.countInMs - offset
                for (time in start..recordAt + 100 step 10) {
                    val position = (time - recordAt).takeIf { it >= 0 }
                    game.tick(time - start, position) { beats.add(it) }
                }
                assertEquals(listOf(0, 1, 2, 3), beats)
            }
        }
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
        game.tickAtSongTime(note.time)
        game.hit(note.lane, note.time)
        assertEquals(Judgment.PERFECT, game.laneJudge[note.lane]?.judgment)
        game.hit(other, note.time)
        assertNull(game.laneJudge[other]!!.judgment)
        game.tickAtSongTime(game.notes[1].time + 221L)
        assertEquals(Judgment.MISS, game.laneJudge[game.notes[1].lane]?.judgment)
        game.start(0)
        assertTrue(game.laneJudge.all { it == null })
    }
}
