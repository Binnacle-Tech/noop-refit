package com.noop.analytics

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSportTest {
    @Test fun catalogue_isNonEmpty_andSearchable() {
        assertTrue(WorkoutSport.all.size >= 20)
        val running = WorkoutSport.all.first { it.name == "Running" }
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, running.exerciseType)
    }

    @Test fun running_isDistanceSport_strength_isNot() {
        assertTrue(WorkoutSport.all.first { it.name == "Running" }.isDistanceSport)
        assertTrue(WorkoutSport.all.first { it.name == "Cycling" }.isDistanceSport)
        assertFalse(WorkoutSport.all.first { it.name == "Strength" }.isDistanceSport)
        assertFalse(WorkoutSport.all.first { it.name == "Yoga" }.isDistanceSport)
    }

    @Test fun unknownType_fallsBackToOther() {
        assertEquals("Workout", WorkoutSport.nameFor(Int.MIN_VALUE))
    }

    @Test fun everyDistanceSport_hasValidHcType() {
        WorkoutSport.all.filter { it.isDistanceSport }.forEach {
            assertTrue(it.exerciseType > 0)
        }
    }

    @Test fun default_isOther() {
        assertEquals("Other", WorkoutSport.default.name)
    }

    /** #768: the newly requested presets are present, spelled byte-for-byte the way iOS persists them. */
    @Test fun newPresets_arePresent() {
        val names = WorkoutSport.all.map { it.name }
        listOf(
            "Racquetball", "Volleyball", "Martial arts", "Dancing", "Golf",
            "Climbing", "Stretching", "Skiing", "Snowboarding", "Pickleball",
        ).forEach { assertTrue("$it must be in the catalogue", names.contains(it)) }
    }

    /** Snow sports cover ground, so GPS defaults on; racket/court sports have no route. */
    @Test fun snowSports_areDistance_racketSports_areNot() {
        assertTrue(WorkoutSport.all.first { it.name == "Skiing" }.isDistanceSport)
        assertTrue(WorkoutSport.all.first { it.name == "Snowboarding" }.isDistanceSport)
        assertFalse(WorkoutSport.all.first { it.name == "Racquetball" }.isDistanceSport)
        assertFalse(WorkoutSport.all.first { it.name == "Volleyball" }.isDistanceSport)
    }

    /** Pickleball is an EXTRA (no HC type) → rides on "Other" for writeback but keeps its own label. */
    @Test fun pickleball_isExtra_fallsBackToOther() {
        val pickle = WorkoutSport.all.first { it.name == "Pickleball" }
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, pickle.exerciseType)
    }

    /** Extras (Padel, Pickleball, ...) sit before the generic "Other" catch-all. */
    @Test fun extras_precedeOther() {
        val names = WorkoutSport.all.map { it.name }
        assertTrue(names.indexOf("Pickleball") < names.indexOf("Other"))
        assertEquals("Other", names.last())
    }

    /** Bowling (D#850) is an EXTRA (no HC type) → rides on "Other" for writeback but keeps its own
     *  label, has no route (GPS off), and sits before the generic "Other" catch-all. */
    @Test fun bowling_isExtra_fallsBackToOther() {
        val bowling = WorkoutSport.all.first { it.name == "Bowling" }
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, bowling.exerciseType)
        assertFalse(bowling.isDistanceSport)
        val names = WorkoutSport.all.map { it.name }
        assertTrue(names.indexOf("Bowling") < names.indexOf("Other"))
    }

    // --- exerciseTypeForName: the reverse (label -> HC type) lookup the workout writeback uses ---

    @Test fun exerciseTypeForName_mapsCatalogueLabel() {
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, WorkoutSport.exerciseTypeForName("Running"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, WorkoutSport.exerciseTypeForName("Cycling"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, WorkoutSport.exerciseTypeForName("Strength"))
    }

    @Test fun exerciseTypeForName_isCaseAndWhitespaceInsensitive() {
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, WorkoutSport.exerciseTypeForName("  rUnNiNg  "))
    }

    @Test fun exerciseTypeForName_extrasRideTheirFallbackType() {
        // EXTRA sports HC has no dedicated type for resolve to the type they ride on (#714/#768/#152).
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, WorkoutSport.exerciseTypeForName("Padel"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, WorkoutSport.exerciseTypeForName("Bodybuilding"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_WALKING, WorkoutSport.exerciseTypeForName("Treadmill walk"))
    }

    @Test fun exerciseTypeForName_unknownOrFreeTypedFallsBackToOther() {
        // A detected bout / foreign import with a non-catalogue label still exports (as Other), never dropped.
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, WorkoutSport.exerciseTypeForName("Zumba-ish"))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, WorkoutSport.exerciseTypeForName(""))
    }

    @Test fun exerciseTypeForName_everyCatalogueSportRoundTrips() {
        for (s in WorkoutSport.all) {
            assertEquals("round-trip for '${s.name}'", s.exerciseType, WorkoutSport.exerciseTypeForName(s.name))
        }
    }
}
