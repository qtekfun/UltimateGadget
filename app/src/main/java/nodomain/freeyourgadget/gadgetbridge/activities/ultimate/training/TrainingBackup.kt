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

/**
 * Bridges the workouts store and the app-wide backup: the backup zip carries one extra JSON entry
 * (our own versioned format) instead of a copy of the SQLite file.
 */
object TrainingBackup {
    /** Name of the zip entry that holds the workouts document. */
    const val ENTRY = "training/workouts.json"

    /** The whole workouts store as the bytes to put in [ENTRY]. */
    fun snapshot(store: TrainingStore): ByteArray = TrainingJson.export(store.exportDocument()).toByteArray(Charsets.UTF_8)

    /**
     * Replaces the contents of [store] with the backup bytes. Throws [IllegalArgumentException] for a
     * file that is not ours, before anything is touched. Returns the number of entries restored.
     */
    fun restore(store: TrainingStore, bytes: ByteArray): Int {
        val doc = TrainingJson.import(String(bytes, Charsets.UTF_8))
        return store.importDocument(doc, replace = true).added
    }
}
