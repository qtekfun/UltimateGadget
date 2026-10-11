package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GolfScoreStyleTest {
    @Test fun referenceCaptureScale() {
        // Par 3 holes from the Huawei Health capture.
        assertEquals(ScoreCategory.BOGEY, GolfScoreStyle.category(4, 3))
        assertEquals(ScoreCategory.DOUBLE_BOGEY, GolfScoreStyle.category(5, 3))
        assertEquals(ScoreCategory.TRIPLE_OR_WORSE, GolfScoreStyle.category(7, 3))
        assertEquals(ScoreCategory.TRIPLE_OR_WORSE, GolfScoreStyle.category(9, 3))
        // Par 4 with 7 strokes (+3).
        assertEquals(ScoreCategory.TRIPLE_OR_WORSE, GolfScoreStyle.category(7, 4))
    }

    @Test fun parAndBetter() {
        assertEquals(ScoreCategory.PAR_OR_BETTER, GolfScoreStyle.category(3, 3))
        assertEquals(ScoreCategory.PAR_OR_BETTER, GolfScoreStyle.category(2, 3))
        assertEquals(ScoreCategory.PAR_OR_BETTER, GolfScoreStyle.category(1, 5))
    }

    @Test fun unplayed() {
        assertEquals(ScoreCategory.UNPLAYED, GolfScoreStyle.category(null, 3))
        assertEquals(ScoreCategory.UNPLAYED, GolfScoreStyle.category(0, 3))
    }

    @Test fun missingParIsNeutral() {
        assertEquals(ScoreCategory.PAR_OR_BETTER, GolfScoreStyle.category(6, null))
        assertEquals(ScoreCategory.PAR_OR_BETTER, GolfScoreStyle.category(6, 0))
    }

    @Test fun borders() {
        assertNull(GolfScoreStyle.borderColor(ScoreCategory.UNPLAYED, false))
        assertNull(GolfScoreStyle.borderColor(ScoreCategory.PAR_OR_BETTER, true))
        assertNotNull(GolfScoreStyle.borderColor(ScoreCategory.BOGEY, false))
    }

    @Test fun stagger() {
        assertEquals(0, GolfScoreStyle.staggerDelayMs(0))
        assertEquals(90, GolfScoreStyle.staggerDelayMs(2))
        assertEquals(600, GolfScoreStyle.staggerDelayMs(100))
    }
}
