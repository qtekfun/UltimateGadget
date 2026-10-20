package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.training

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingBackupTest {
    private lateinit var store: TrainingStore

    @Before fun setUp() {
        TrainingStore.reset()
        store = TrainingStore.get(RuntimeEnvironment.getApplication())
    }

    @After fun tearDown() { TrainingStore.reset() }

    private fun seed() {
        store.saveExercise(Exercise("custom:1", mapOf("en" to "Sled push"), Measure.DISTANCE, custom = true))
        store.saveRoutine(Routine(id = "r", name = "Push"), nowMs = 5)
        store.saveSession(Session(id = "s", title = "Done", startMs = 1000, endMs = 5000, status = SessionStatus.DONE))
    }

    @Test fun snapshotInsideAZipRestoresIntoAnEmptyStore() {
        seed()
        val zip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { z ->
                z.putNextEntry(ZipEntry(TrainingBackup.ENTRY)); z.write(TrainingBackup.snapshot(store)); z.closeEntry()
            }
        }.toByteArray()
        val bytes = ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            assertEquals(TrainingBackup.ENTRY, z.nextEntry!!.name); z.readBytes()
        }
        store.deleteSession("s"); store.deleteRoutine("r"); store.deleteExercise("custom:1")
        assertTrue(store.routines().isEmpty())

        assertEquals(3, TrainingBackup.restore(store, bytes))
        assertEquals("Sled push", store.customExercises().single().displayName())
        assertEquals("Push", store.routine("r")!!.name)
        assertEquals("Done", store.session("s")!!.title)
    }

    @Test fun restoreReplacesWhatIsThere() {
        seed()
        val bytes = TrainingBackup.snapshot(store)
        store.saveRoutine(Routine(id = "extra", name = "Newer"), nowMs = 9)
        TrainingBackup.restore(store, bytes)
        assertEquals(null, store.routine("extra"))
    }

    @Test fun garbageIsRejectedAndNothingIsWiped() {
        seed()
        try { TrainingBackup.restore(store, "nope".toByteArray()); fail() } catch (_: IllegalArgumentException) {}
        assertEquals("Push", store.routine("r")!!.name)
    }
}
