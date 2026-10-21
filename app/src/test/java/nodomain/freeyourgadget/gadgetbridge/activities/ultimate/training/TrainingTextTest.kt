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
import org.junit.Test
import java.util.Locale

class TrainingTextTest {
    @Test fun hardWrappedLinesAreJoined() {
        assertEquals("Stand up straight and lift the bar.", TrainingText.clean("Stand up straight\nand lift\nthe bar."))
    }

    @Test fun blankLinesKeepTheParagraphs() {
        assertEquals("One two.\n\nThree four.", TrainingText.clean("One\ntwo.\n\nThree\nfour."))
        assertEquals("One.\n\nTwo.", TrainingText.clean("One.\n \t\n\n\nTwo."))
    }

    @Test fun windowsLineEndingsAndStrayWhitespaceAreHandled() {
        assertEquals("A b c", TrainingText.clean("  A\r\n  b   c \r\n"))
        assertEquals("A.\n\nB.", TrainingText.clean("A.\r\n\r\nB."))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", TrainingText.clean(""))
        assertEquals("", TrainingText.clean("\n \n"))
    }

    @Test fun exerciseWithoutDescriptionFallsBackToNotes() {
        val e = Exercise("x", mapOf("en" to "X"), notes = "line one\nline two")
        assertEquals("line one line two", e.cleanDescription(Locale.ENGLISH))
    }

    @Test fun languageFallbackFollowsTheNames() {
        val e = Exercise(
            "x", mapOf("en" to "X"),
            summaries = mapOf("en" to "English", "es" to "Español"),
            steps = mapOf("en" to listOf("a")),
        )
        assertEquals("Español", e.summary(Locale("es")))
        assertEquals("English", e.summary(Locale.FRENCH))
        assertEquals(listOf("a"), e.steps(Locale("es")))
    }
}
