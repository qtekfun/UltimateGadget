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

/** Text helpers for exercise descriptions; no Android types. */
object TrainingText {
    private val PARAGRAPH_BREAK = Regex("\\n[ \\t]*\\n+")
    private val SPACES = Regex("[ \\t]+")

    /**
     * Joins the hard-wrapped lines inside each paragraph into one line and keeps blank-line paragraph
     * breaks (as a single empty line). Schema 1 pack text is wrapped at about 70 columns.
     */
    fun clean(raw: String): String =
        raw.replace("\r\n", "\n").replace('\r', '\n').trim()
            .split(PARAGRAPH_BREAK)
            .map { p -> SPACES.replace(p.lines().joinToString(" ") { it.trim() }, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
}
