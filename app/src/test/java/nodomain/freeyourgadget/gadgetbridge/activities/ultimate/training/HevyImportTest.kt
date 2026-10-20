package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDateTime

private const val HEADER = "\"title\",\"start_time\",\"end_time\",\"description\",\"exercise_title\",\"superset_id\",\"exercise_notes\",\"set_index\",\"set_type\",\"weight_kg\",\"reps\",\"distance_km\",\"duration_seconds\",\"rpe\""

/** Synthetic export: two workouts, a superset, every set type, a cardio and a timed exercise. */
private val FIXTURE = listOf(
    HEADER,
    "\"Push, heavy\",\"5 Aug 2024, 18:30\",\"5 Aug 2024, 19:40\",\"Felt \"\"good\"\"\",\"Bench Press (Barbell)\",\"\",\"\",0,\"warmup\",40,10,,,",
    "\"Push, heavy\",\"5 Aug 2024, 18:30\",\"5 Aug 2024, 19:40\",\"Felt \"\"good\"\"\",\"Bench Press (Barbell)\",\"\",\"\",1,\"normal\",80,5,,,8",
    "\"Push, heavy\",\"5 Aug 2024, 18:30\",\"5 Aug 2024, 19:40\",\"Felt \"\"good\"\"\",\"Bench Press (Barbell)\",\"\",\"\",2,\"failure\",80,3,,,10",
    "\"Push, heavy\",\"5 Aug 2024, 18:30\",\"5 Aug 2024, 19:40\",\"Felt \"\"good\"\"\",\"Lateral Raises\",\"1\",\"slow\",0,\"normal\",10,12,,,",
    "\"Push, heavy\",\"5 Aug 2024, 18:30\",\"5 Aug 2024, 19:40\",\"Felt \"\"good\"\"\",\"Face Pull\",\"1\",\"\",0,\"dropset\",20,15,,,",
    "\"Push, heavy\",\"5 Aug 2024, 18:30\",\"5 Aug 2024, 19:40\",\"Felt \"\"good\"\"\",\"Zercher Squat\",\"\",\"\",0,\"normal\",60,8,,,",
    "\"Cardio\",\"7 Aug 2024, 07:00\",\"7 Aug 2024, 07:45\",\"\",\"Treadmill Intervals\",\"\",\"\",0,\"normal\",,,5.0,1800,",
    "\"Cardio\",\"7 Aug 2024, 07:00\",\"7 Aug 2024, 07:45\",\"\",\"Dead Hang\",\"\",\"\",0,\"normal\",,,,45,",
).joinToString("\r\n") + "\r\n"

class HevyCsvTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime.of(y, mo, d, h, mi).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun csvQuotesCommasNewlinesAndEscapes() {
        val rows = CsvParser.parse("a,\"b,c\",\"d\"\"e\"\r\n\"multi\nline\",,x\n\nlast,1,")
        assertEquals(listOf("a", "b,c", "d\"e"), rows[0])
        assertEquals(listOf("multi\nline", "", "x"), rows[1])
        assertEquals(listOf("last", "1", ""), rows[2])
        assertEquals(3, rows.size)
    }

    @Test fun csvBomAndNoTrailingNewline() {
        val rows = CsvParser.parse("﻿title,x\nA,B")
        assertEquals(listOf("title", "x"), rows[0])
        assertEquals(listOf("A", "B"), rows[1])
    }

    @Test fun semicolonDelimiterIsDetected() {
        val p = HevyCsv.parse("title;start_time;exercise_title;weight_kg;reps\nW;2024-08-05 18:30;Squat;100;5", utc)
        assertEquals(1, p.workouts.size)
        assertEquals(100.0, p.workouts[0].exercises[0].sets[0].weight!!, 0.0)
    }

    @Test fun datesInSeveralFormats() {
        val want = ms(2024, 8, 5, 18, 30)
        assertEquals(want, HevyCsv.parseDate("5 Aug 2024, 18:30", utc))
        assertEquals(want, HevyCsv.parseDate("05 August 2024, 6:30 PM", utc))
        assertEquals(want, HevyCsv.parseDate("2024-08-05 18:30:00", utc))
        assertEquals(want, HevyCsv.parseDate("2024-08-05T18:30:00Z", utc))
        assertEquals(want, HevyCsv.parseDate("2024-08-05T20:30:00+02:00", utc))
        assertEquals(ms(2024, 8, 5, 0, 5), HevyCsv.parseDate("5 Aug 2024, 12:05 AM", utc))
        assertNull(HevyCsv.parseDate("garbage", utc))
        assertNull(HevyCsv.parseDate("", utc))
    }

    @Test fun groupsWorkoutsExercisesAndSets() {
        val p = HevyCsv.parse(FIXTURE, utc)
        assertEquals(2, p.workouts.size)
        assertEquals(WeightUnit.KG, p.weightUnit)
        assertEquals(DistanceUnit.KM, p.distanceUnit)
        assertEquals(0, p.skippedRows)
        val push = p.workouts[0]
        assertEquals("Push, heavy", push.title)
        assertEquals("Felt \"good\"", push.description)
        assertEquals(ms(2024, 8, 5, 19, 40), push.endMs)
        assertEquals(listOf("Bench Press (Barbell)", "Lateral Raises", "Face Pull", "Zercher Squat"), push.exercises.map { it.title })
        assertEquals(listOf(SetKind.WARMUP, SetKind.NORMAL, SetKind.FAILURE), push.exercises[0].sets.map { it.kind })
        assertEquals(8.0, push.exercises[0].sets[1].rpe!!, 0.0)
        assertEquals(listOf(null, 1, 1, null), push.exercises.map { it.supersetId })
        assertEquals("slow", push.exercises[1].notes)
        assertEquals(SetKind.DROP, push.exercises[2].sets[0].kind)
        assertEquals(8, p.totalSets)
    }

    @Test fun reorderedColumnsAndLbUnits() {
        val csv = "reps,exercise_title,start_time,weight_lbs,title,distance_miles\n5,Squat,\"2024-08-05 18:30\",225,Legs,\n"
        val p = HevyCsv.parse(csv, utc)
        assertEquals(WeightUnit.LB, p.weightUnit)
        assertEquals(DistanceUnit.MILES, p.distanceUnit)
        assertEquals(225.0, p.workouts[0].exercises[0].sets[0].weight!!, 0.0)
    }

    @Test fun badRowsAreSkippedAndNonHevyFilesRejected() {
        val p = HevyCsv.parse("title,start_time,exercise_title\nA,nonsense,Squat\nA,2024-08-05 18:30,\nA,2024-08-05 18:30,Squat", utc)
        assertEquals(2, p.skippedRows)
        assertEquals(1, p.totalSets)
        try { HevyCsv.parse("foo,bar\n1,2", utc); fail() } catch (_: IllegalArgumentException) {}
        try { HevyCsv.parse("", utc); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun sameExerciseSeparatedByAnotherIsTwoBlocks() {
        val csv = "title,start_time,exercise_title,reps\nW,2024-08-05 18:30,A,1\nW,2024-08-05 18:30,B,1\nW,2024-08-05 18:30,A,1"
        assertEquals(3, HevyCsv.parse(csv, utc).workouts[0].exercises.size)
    }
}

class ExerciseMatcherTest {
    private val catalog = BuiltinExercises.all
    private val matcher = ExerciseMatcher(catalog)
    private fun id(title: String) = matcher.match(title)?.id

    @Test fun normalisedNamesMatchInEnglishAndSpanish() {
        assertEquals("builtin:squat", id("barbell BACK squat"))
        assertEquals("builtin:squat", id("Sentadilla con barra"))
        assertEquals("builtin:romanian_deadlift", id("Peso Muerto Rumano"))
        assertEquals("builtin:push_up", id("Push Up"))
        assertEquals("builtin:pull_up", id("Pull-ups"))
    }

    @Test fun wordOrderParenthesesAndPlurals() {
        assertEquals("builtin:bench_press", id("Bench Press (Barbell)"))
        assertEquals("builtin:lateral_raise", id("Lateral Raises"))
        assertEquals("builtin:biceps_curl", id("Biceps Curl (Dumbbell)"))
        assertEquals("builtin:leg_press", id("leg  presses"))
        assertEquals("builtin:pec_deck", id("Pec Deck"))
    }

    @Test fun accentsAreIgnored() {
        assertEquals("builtin:dip", id("fondos"))
        assertEquals("builtin:rear_delt_fly", id("Pajaros"))
    }

    @Test fun unknownTitlesDoNotMatch() {
        assertNull(id("Zercher Squat"))
        assertNull(id(""))
    }

    @Test fun customExercisesAreMatchedToo() {
        val custom = Exercise("custom:1", mapOf("en" to "Zercher squat"), custom = true)
        assertEquals("custom:1", ExerciseMatcher(catalog + custom).match("Zercher Squat")?.id)
    }
}

class HevyMapperTest {
    private val utc = ZoneId.of("UTC")
    private val parsed = HevyCsv.parse(FIXTURE, utc)
    private val plan = HevyImport.plan(parsed, BuiltinExercises.all)

    @Test fun planCountsMatchedAndUnmatched() {
        assertEquals(2, plan.workouts)
        assertEquals(8, plan.totalSets)
        assertEquals(setOf("Bench Press (Barbell)", "Lateral Raises", "Face Pull"), plan.matched.keys)
        assertEquals(listOf("Dead Hang", "Treadmill Intervals", "Zercher Squat"), plan.unmatched.map { it.title })
        assertEquals(Measure.TIME, plan.unmatched.first { it.title == "Dead Hang" }.measure)
        assertEquals(Measure.DISTANCE_TIME, plan.unmatched.first { it.title == "Treadmill Intervals" }.measure)
        assertEquals(Measure.REPS_WEIGHT, plan.unmatched.first { it.title == "Zercher Squat" }.measure)
        assertTrue(plan.firstMs < plan.lastMs)
    }

    @Test fun mapsSetsSupersetsAndUnits() {
        val built = HevyImport.build(parsed, plan, setOf("Zercher Squat", "Treadmill Intervals", "Dead Hang"))
        assertEquals(2, built.sessions.size)
        val push = built.sessions[0]
        assertEquals(SessionStatus.DONE, push.status)
        assertEquals("Felt \"good\"", push.notes)
        assertEquals(listOf("builtin:bench_press", "builtin:lateral_raise", "builtin:face_pull"), push.exercises.take(3).map { it.exerciseId })
        assertTrue(push.exercises.all { e -> e.sets.all { it.done } })
        assertEquals(listOf(SetKind.WARMUP, SetKind.NORMAL, SetKind.FAILURE), push.exercises[0].sets.map { it.kind })
        assertEquals(2, push.exercises[0].sets[1].rir)
        assertNull(push.exercises[0].sets[0].rir)
        assertEquals(listOf(null, 1, 1, null), push.exercises.map { it.supersetGroup })
        assertEquals("slow", push.exercises[1].notes)
        assertEquals(40.0, push.exercises[0].sets[0].weightKg!!, 0.0)
        val cardio = built.sessions[1]
        assertEquals(5000.0, cardio.exercises[0].sets[0].distanceM!!, 0.001)
        assertEquals(1800, cardio.exercises[0].sets[0].durationS)
        assertNull(cardio.exercises[0].sets[0].weightKg)
        assertEquals(3, built.newExercises.size)
        assertTrue(built.newExercises.all { it.custom })
    }

    @Test fun poundsAndMilesAreConverted() {
        val csv = "title,start_time,exercise_title,weight_lbs,reps,distance_miles\nW,2024-08-05 18:30,Deadlift,225,5,\nW,2024-08-05 18:30,Run,,,3.1"
        val p = HevyCsv.parse(csv, utc)
        val pl = HevyImport.plan(p, BuiltinExercises.all)
        val b = HevyImport.build(p, pl, setOf("Run"))
        assertEquals(102.058, b.sessions[0].exercises[0].sets[0].weightKg!!, 0.01)
        assertEquals(4988.9, b.sessions[0].exercises[1].sets[0].distanceM!!, 1.0)
        // The user can override an unknown unit.
        val unknown = HevyCsv.parse("title,start_time,exercise_title,weight,reps\nW,2024-08-05 18:30,Deadlift,100,5", utc)
        assertEquals(WeightUnit.UNKNOWN, unknown.weightUnit)
        val up = HevyImport.plan(unknown, BuiltinExercises.all)
        assertEquals(100.0, HevyImport.build(unknown, up, emptySet()).sessions[0].exercises[0].sets[0].weightKg!!, 0.0)
        assertEquals(45.359, HevyImport.build(unknown, up, emptySet(), WeightUnit.LB).sessions[0].exercises[0].sets[0].weightKg!!, 0.01)
    }

    @Test fun skippedTitlesAreLeftOutAndEmptyWorkoutsDropped() {
        val built = HevyImport.build(parsed, plan, emptySet())
        assertEquals(1, built.sessions.size)
        assertEquals(3, built.sessions[0].exercises.size)
        assertTrue(built.newExercises.isEmpty())
    }

    @Test fun sessionIdsAreDeterministic() {
        val a = HevyImport.build(parsed, plan, emptySet()).sessions.map { it.id }
        val b = HevyImport.build(HevyCsv.parse(FIXTURE, utc), plan, emptySet()).sessions.map { it.id }
        assertEquals(a, b)
        assertEquals(a.size, a.toSet().size)
        assertNotNull(a.firstOrNull())
        assertEquals(HevyImport.customExerciseId("Zercher Squat"), HevyImport.customExerciseId("zercher  squat"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HevyApplyTest {
    private lateinit var store: TrainingStore
    private val utc = ZoneId.of("UTC")

    @Before fun setUp() {
        TrainingStore.reset()
        store = TrainingStore.get(RuntimeEnvironment.getApplication())
    }

    @After fun tearDown() { TrainingStore.reset() }

    private fun importOnce(): HevyApplied {
        val parsed = HevyCsv.parse(FIXTURE, utc)
        val plan = HevyImport.plan(parsed, TrainingCatalog(store).all())
        val built = HevyImport.build(parsed, plan, plan.unmatched.map { it.title }.toSet())
        return HevyImport.apply(store, built)
    }

    @Test fun importingTwiceAddsNothingTheSecondTime() {
        val first = importOnce()
        assertEquals(2, first.sessionsAdded)
        assertEquals(3, first.exercisesCreated)
        assertEquals(2, store.finishedSessions().size)
        assertEquals(3, store.customExercises().size)

        val second = importOnce()
        assertEquals(0, second.sessionsAdded)
        assertEquals(0, second.exercisesCreated)
        assertEquals(2, store.finishedSessions().size)
        assertEquals(3, store.customExercises().size)
    }

    @Test fun createdExercisesAreMatchedOnTheNextImport() {
        importOnce()
        val parsed = HevyCsv.parse(FIXTURE, utc)
        val plan = HevyImport.plan(parsed, TrainingCatalog(store).all())
        assertTrue(plan.unmatched.isEmpty())
    }

    @Test fun progressIsReported() {
        val parsed = HevyCsv.parse(FIXTURE, utc)
        val plan = HevyImport.plan(parsed, BuiltinExercises.all)
        val built = HevyImport.build(parsed, plan, plan.unmatched.map { it.title }.toSet())
        var last = 0 to 0
        HevyImport.apply(store, built) { d, t -> last = d to t }
        assertEquals(5, last.first)
        assertEquals(5, last.second)
    }
}
