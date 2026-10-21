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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class TrainingLabelsTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()

    private val packTerms = listOf(
        "Quads", "Glutes", "Lats", "Trapezius", "Obliquus externus abdominis", "Serratus anterior", "Soleus", "Brachialis",
        "Biceps", "Triceps", "Chest", "Abs", "Calves", "Hamstrings", "Shoulders",
        "Dumbbell", "Cable machine", "Barbell", "Bench", "Pull-up bar", "Resistance band", "Incline bench", "SZ-Bar", "Kettlebell",
        "Gym mat", "Swiss Ball", "none (bodyweight exercise)",
        "Legs", "Back", "Arms", "Shoulders", "Abs", "Chest", "Cardio", "Calves",
    )

    @After fun tearDown() {
        TrainingPacks.invalidate()
        TrainingStore.reset()
    }

    @Test fun everyTermOfThePackHasAResource() {
        for (t in packTerms) assertNotNull("no label for $t", TrainingLabels.resFor(t))
        assertNull(TrainingLabels.resFor("my own muscle"))
    }

    @Test fun englishLabelsAreReadable() {
        assertEquals("quads, glutes", TrainingLabels.labels(ctx, "quads, glutes"))
        assertEquals("external obliques", TrainingLabels.label(ctx, "obliquus externus abdominis"))
        assertEquals("bodyweight", TrainingLabels.label(ctx, "bodyweight"))
    }

    @Test @Config(qualifiers = "es") fun spanishLabels() {
        assertEquals("cuádriceps, glúteos", TrainingLabels.labels(ctx, "quads, glutes"))
        assertEquals("oblicuos externos", TrainingLabels.label(ctx, "Obliquus externus abdominis"))
        assertEquals("peso corporal", TrainingLabels.label(ctx, "none (bodyweight exercise)"))
        assertEquals("barra Z", TrainingLabels.label(ctx, "SZ-Bar"))
        assertEquals("piernas", TrainingLabels.label(ctx, "Legs"))
    }

    @Test fun unknownTextIsShownAsTyped() {
        assertEquals("my cable, thing", TrainingLabels.labels(ctx, "my cable, thing"))
        assertEquals("Zottman Machine", TrainingLabels.label(ctx, " Zottman Machine "))
    }

    @Test fun searchTextHasBothLanguages() {
        val text = TrainingLabels.searchText(ctx)("quads, cable machine")
        assertTrue(text.contains("quads") && text.contains("cuádriceps"))
        assertTrue(text.contains("cable machine") && text.contains("polea"))
    }

    @Test fun catalogSearchMatchesEnglishAndSpanishLabelsWithoutAccents() {
        TrainingPacks.invalidate()
        TrainingPacks.rootDir(ctx).deleteRecursively()
        TrainingPacks.install(ctx, PackFixture.zip().inputStream())
        val catalog = TrainingCatalog.create(ctx, TrainingStore(ctx, name = null))
        fun ids(q: String) = catalog.search(q, Locale.ENGLISH).map { it.id }
        // The fixture exercises train quads and glutes with a barbell and are in the pack
        assertTrue("uuid-squat" in ids("quads"))
        assertTrue("uuid-squat" in ids("cuadriceps"))
        assertTrue("uuid-squat" in ids("CUÁDRICEPS"))
        assertTrue("uuid-squat" in ids("gluteos"))
        assertTrue("uuid-squat" in ids("barra"))
        assertTrue("uuid-squat" in ids("barbell"))
        assertTrue("uuid-squat" in ids("gemelos"))
        assertTrue("uuid-squat" in ids("piernas"))
        assertTrue(ids("zzzz").isEmpty())
    }
}
