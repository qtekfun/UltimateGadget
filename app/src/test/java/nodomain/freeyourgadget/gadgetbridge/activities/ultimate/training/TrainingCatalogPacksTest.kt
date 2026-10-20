/*  Copyright (C) 2026 UltimateGadget contributors

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TrainingCatalogPacksTest {
    private lateinit var store: TrainingStore
    private lateinit var catalog: TrainingCatalog
    private val en = Locale.ENGLISH
    private val es = Locale("es")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        TrainingPacks.invalidate()
        TrainingPacks.rootDir(ctx).deleteRecursively()
        TrainingPacks.install(ctx, PackFixture.zip().inputStream())
        store = TrainingStore(ctx, name = null)
        catalog = TrainingCatalog.create(ctx, store)
    }

    @After fun tearDown() {
        TrainingPacks.invalidate()
        store.close()
    }

    @Test fun catalogHoldsBuiltinPackAndCustomExercises() {
        store.saveExercise(Exercise("custom-1", mapOf("en" to "My move"), custom = true))
        catalog.invalidate()
        val ids = catalog.all().map { it.id }.toSet()
        assertTrue("builtin:squat" in ids)
        assertTrue("uuid-squat" in ids && "uuid-lunge" in ids && "uuid-plank" in ids)
        assertTrue("custom-1" in ids)
        assertNotNull(catalog.find("uuid-squat"))
        assertNull(catalog.find("nope"))
    }

    @Test fun namesAndDescriptionsFollowTheLanguageWithEnglishFallback() {
        val squat = catalog.find("uuid-squat")!!
        assertEquals("Goblet Squat", squat.displayName(en))
        assertEquals("Sentadilla goblet", squat.displayName(es))
        assertEquals("About Goblet Squat", squat.description(en))
        assertEquals("Sobre Sentadilla goblet", squat.description(es))
        val plank = catalog.find("uuid-plank")!!
        assertEquals("Plank", plank.displayName(es))
        assertEquals("About Plank", plank.description(es))
        assertEquals("Plank", plank.displayName(Locale.FRENCH))
    }

    @Test fun searchMatchesLocalizedNamesAccentsAndMuscles() {
        assertEquals(listOf("uuid-squat"), catalog.search("goblet", en).filter { it.pack != null }.map { it.id })
        assertTrue("uuid-squat" in catalog.search("sentadilla", en).map { it.id })
        assertTrue("uuid-lunge" in catalog.search("ZANCADA", en).map { it.id })
        assertTrue("uuid-lunge" in catalog.search("zancáda", es).map { it.id })
        assertTrue(catalog.search("glutes", en).map { it.id }.containsAll(listOf("uuid-squat", "uuid-plank")))
        assertTrue(catalog.search("zzzz", en).isEmpty())
        val names = catalog.search("", en).map { TrainingCatalog.fold(it.displayName(en)) }
        assertEquals(names.sorted(), names)
    }

    @Test fun searchStaysFastWithAThousandExercises() {
        val many = List(1000) { Exercise("p$it", mapOf("en" to "Exercise number $it", "es" to "Ejercicio $it"), muscle = "quads") }
        val big = TrainingCatalog(store) { many }
        big.search("")
        val t = System.nanoTime()
        repeat(20) { assertEquals(1, big.search("number 999", en).size) }
        val ms = (System.nanoTime() - t) / 1_000_000
        assertTrue("20 searches took $ms ms", ms < 2000)
    }

    @Test fun packExercisesAreNotExportedButSessionsKeepTheirSnapshot() {
        val squat = catalog.find("uuid-squat")!!
        val session = Session(
            id = "s", title = "t", startMs = 1, status = SessionStatus.DONE,
            exercises = listOf(SessionExercise(squat.id, squat.names, squat.measure, sets = listOf(SessionSet(reps = 5, weightKg = 60.0, done = true)))),
        )
        store.saveSession(session)
        val doc = store.exportDocument()
        assertTrue(doc.exercises.none { it.id == "uuid-squat" })
        val text = TrainingJson.export(doc)
        assertFalse(text.contains("Sobre"))
        // The pack may be gone on the importing side: the session is still readable by its stored names.
        val restored = TrainingJson.import(text).sessions.single()
        assertEquals("Goblet Squat", restored.exercises[0].displayName(en))
        assertEquals("Sentadilla goblet", restored.exercises[0].displayName(es))
    }

    @Test fun oldFilesStillImportAndCustomExercisesRoundTrip() {
        val old = """{"format":"ultimategadget-training","schema":1,"exercises":[{"id":"c1","names":{"en":"Old"},"measure":"REPS","muscle":"abs","equipment":"","notes":"n"}],"routines":[],"sessions":[]}"""
        val doc = TrainingJson.import(old)
        val e = doc.exercises.single()
        assertEquals(Exercise("c1", mapOf("en" to "Old"), Measure.REPS, "abs", "", custom = true, notes = "n"), e)
        assertEquals("n", e.description(en))
        assertEquals(doc.exercises, TrainingJson.import(TrainingJson.export(doc)).exercises)
    }
}
