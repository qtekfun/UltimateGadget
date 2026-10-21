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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class TrainingPacksV2Test {
    @get:Rule val tmp = TemporaryFolder()

    private fun root() = File(tmp.root, "packs")
    private val en = Locale.ENGLISH
    private val es = Locale("es")

    private fun load(bytes: ByteArray): List<Exercise> {
        TrainingPacks.install(root(), bytes.inputStream())
        return TrainingPacks.load(root())[0].exercises
    }

    @Test fun schemaOnePackStillLoadsWithoutTheNewFields() {
        val m = TrainingPacks.install(root(), PackFixture.zip().inputStream())
        assertEquals(1, m.schema)
        val squat = TrainingPacks.load(root())[0].exercises.first { it.id == "uuid-squat" }
        assertTrue(squat.summaries.isEmpty())
        assertTrue(squat.steps(en).isEmpty())
        assertTrue(squat.tips(es).isEmpty())
        assertEquals("", squat.summary(en))
        assertEquals("About Goblet Squat", squat.cleanDescription(en))
        assertTrue(squat.pack!!.media.none { it.animated || it.generated })
    }

    @Test fun schemaTwoPackParsesSummaryStepsTipsAndMediaFlags() {
        val m = TrainingPacks.install(root(), PackFixture.zipV2().inputStream())
        assertEquals(2, m.schema)
        val squat = TrainingPacks.load(root())[0].exercises.first { it.id == "uuid-squat" }
        assertEquals("A squat holding a weight at the chest.", squat.summary(en))
        assertEquals("Una sentadilla con peso al pecho.", squat.summary(es))
        assertEquals(listOf("Hold the weight.", "Sit down.", "Stand up."), squat.steps(en))
        assertEquals(listOf("Sujeta el peso.", "Baja.", "Sube."), squat.steps(es))
        assertEquals(listOf("Pecho arriba."), squat.tips(es))
        assertEquals("Legs", squat.category)
        val anim = squat.pack!!.media.first()
        assertEquals("media/uuid-squat.anim.webp", anim.file)
        assertTrue(anim.animated && anim.generated)
        assertEquals("CC0-1.0", anim.license)
        assertTrue(anim.authors.isEmpty())
        val photo = squat.pack!!.media[1]
        assertFalse(photo.animated || photo.generated)
        assertNotNull(TrainingPacks.load(root())[0].mediaFile("media/uuid-squat.anim.webp"))
    }

    @Test fun missingLanguageFallsBackToEnglishAndBlankStepsAreDropped() {
        val lunge = load(PackFixture.zipV2()).first { it.id == "uuid-lunge" }
        assertEquals(listOf("Step forward.", "Return."), lunge.steps(es))
        assertEquals("", lunge.summary(es))
        assertTrue(lunge.tips(en).isEmpty())
    }

    @Test fun unsupportedSchemaIsStillRejected() {
        assertThrows(PackException::class.java) {
            TrainingPacks.install(root(), PackFixture.zip(manifest = PackFixture.manifest(schema = 3)).inputStream())
        }
        assertThrows(PackException::class.java) {
            TrainingPacks.install(root(), PackFixture.zip(exercises = PackFixture.exercisesV2().replace("\"schema\":2", "\"schema\":3")).inputStream())
        }
    }

    @Test fun animatedWebpFilesAreExtracted() {
        TrainingPacks.install(root(), PackFixture.zipV2().inputStream())
        assertTrue(File(root(), "testpack/media/uuid-lunge.anim.webp").isFile)
    }

    @Test fun descriptionOfSchemaOneTextIsCleaned() {
        val plank = load(PackFixture.zipV2()).first { it.id == "uuid-plank" }
        assertEquals("Line one wrapped line\n\nSecond paragraph.", plank.cleanDescription(en))
    }
}
