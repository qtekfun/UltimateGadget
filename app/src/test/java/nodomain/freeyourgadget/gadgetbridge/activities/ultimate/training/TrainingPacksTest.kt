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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrainingPacksTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun root() = File(tmp.root, "packs")
    private fun install(bytes: ByteArray) = TrainingPacks.install(root(), bytes.inputStream())

    @Test fun validPackIsExtractedAndLoaded() {
        val m = install(PackFixture.zip())
        assertEquals("testpack", m.id)
        assertEquals("Test attribution text", m.attribution)
        val packs = TrainingPacks.load(root())
        assertEquals(1, packs.size)
        val squat = packs[0].exercises.first { it.id == "uuid-squat" }
        assertEquals("Goblet Squat", squat.names["en"])
        assertEquals("Sentadilla goblet", squat.names["es"])
        assertEquals("quads, glutes", squat.muscle)
        assertEquals("calves", squat.musclesSecondary)
        assertEquals("barbell", squat.equipment)
        assertEquals(Measure.REPS_WEIGHT, squat.measure)
        assertFalse(squat.custom)
        assertEquals(listOf("es"), squat.pack!!.draftTranslations)
        assertEquals(2, squat.pack!!.media.size)
        assertTrue(squat.pack!!.media[1].ai)
        assertNotNull(packs[0].mediaFile("media/uuid-squat_1.png"))
        assertNull(packs[0].mediaFile("media/missing.png"))
        assertNull(packs[0].mediaFile("../manifest.json"))
        // The staging folder never stays behind.
        assertEquals(listOf("testpack"), root().list()!!.toList())
    }

    @Test fun wgerStyleEquipmentMapsToTheCatalogVocabulary() {
        val json = """{"schema":1,"exercises":[{"id":"a","name":{"en":"Push-up"},"category":"Chest","muscles":["Chest"],"musclesSecondary":[],"equipment":["none (bodyweight exercise)"],"media":[]},
            {"id":"b","name":{"en":"Rowing"},"category":"Cardio","muscles":[],"musclesSecondary":[],"equipment":[],"media":[]}]}"""
        install(PackFixture.zip(exercises = json, media = emptyMap()))
        val list = TrainingPacks.load(root())[0].exercises
        assertEquals("bodyweight", list[0].equipment)
        assertEquals(Measure.REPS, list[0].measure)
        assertEquals(Measure.TIME, list[1].measure)
    }

    @Test fun zipSlipIsRejectedAndNothingIsInstalled() {
        for (name in listOf("../evil.txt", "media/../../evil.txt", "/abs/evil.txt", "media\\..\\evil.txt")) {
            assertThrows(name, PackException::class.java) { install(PackFixture.zip(extra = mapOf(name to byteArrayOf(9)))) }
        }
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertTrue(TrainingPacks.load(root()).isEmpty())
        assertEquals(emptyList<String>(), root().list()!!.toList())
    }

    @Test fun badManifestsAreRejected() {
        val bad = listOf(
            PackFixture.manifest(schema = 3), PackFixture.manifest(id = "../x"), PackFixture.manifest(id = "Has Space"),
            PackFixture.manifest(id = ""), "not json", null,
        )
        for (m in bad) assertThrows(PackException::class.java) { install(PackFixture.zip(manifest = m)) }
        assertTrue(TrainingPacks.load(root()).isEmpty())
    }

    @Test fun badExerciseListsAreRejected() {
        assertThrows(PackException::class.java) { install(PackFixture.zip(exercises = null)) }
        assertThrows(PackException::class.java) { install(PackFixture.zip(exercises = "{}")) }
        assertThrows(PackException::class.java) { install(PackFixture.zip(exercises = """{"schema":1,"exercises":[{"id":"x","name":{}}]}""")) }
    }

    @Test fun aFailedInstallKeepsThePreviousVersion() {
        install(PackFixture.zip(manifest = PackFixture.manifest(version = "1")))
        assertThrows(PackException::class.java) { install(PackFixture.zip(manifest = PackFixture.manifest(version = "2"), exercises = "{}")) }
        assertEquals("1", TrainingPacks.load(root())[0].manifest.version)
    }

    @Test fun newerVersionReplacesTheOlderOne() {
        install(PackFixture.zip(manifest = PackFixture.manifest(version = "1")))
        val smaller = """{"schema":1,"exercises":[{"id":"only","name":{"en":"Only"},"media":[]}]}"""
        install(PackFixture.zip(manifest = PackFixture.manifest(version = "2"), exercises = smaller, media = mapOf("media/new.png" to byteArrayOf(5))))
        val packs = TrainingPacks.load(root())
        assertEquals(1, packs.size)
        assertEquals("2", packs[0].manifest.version)
        assertEquals(listOf("only"), packs[0].exercises.map { it.id })
        assertFalse("old media is gone", File(root(), "testpack/media/uuid-squat_1.png").exists())
        assertTrue(File(root(), "testpack/media/new.png").exists())
        assertEquals(listOf("testpack"), root().list()!!.toList())
    }

    @Test fun absurdSizesAreRejected() {
        val big = ByteArray((TrainingPacks.MAX_ENTRY_BYTES + 1).toInt())
        assertThrows(PackException::class.java) { install(PackFixture.zip(media = mapOf("media/huge.png" to big))) }
        assertTrue(TrainingPacks.load(root()).isEmpty())
    }

    @Test fun unknownFilesAreIgnored() {
        install(PackFixture.zip(extra = mapOf("script.sh" to byteArrayOf(1), "media/readme.txt" to byteArrayOf(1))))
        assertFalse(File(root(), "testpack/script.sh").exists())
        assertFalse(File(root(), "testpack/media/readme.txt").exists())
    }
}
