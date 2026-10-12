package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard

import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.CrossDeviceSteps.Sample
import org.junit.Assert.assertEquals
import org.junit.Test

class CrossDeviceStepsTest {
    private val t0 = 1_700_000_000 - 1_700_000_000 % 60

    @Test
    fun morningAndAfternoonWatchesAdd() {
        val morning = listOf(Sample(t0, 100, 7000), Sample(t0 + 60, 50, 3500))
        val afternoon = listOf(Sample(t0 + 6 * 3600, 200, 14000))
        val m = CrossDeviceSteps.merge(listOf(morning, afternoon))
        assertEquals(350L, m.steps)
        assertEquals(24500L, m.distanceCm)
    }

    @Test
    fun wornTogetherIsNotCountedTwice() {
        val a = listOf(Sample(t0, 100, 7000), Sample(t0 + 60, 80, 5600))
        val b = listOf(Sample(t0, 90, 6300), Sample(t0 + 60, 85, 5950))
        val m = CrossDeviceSteps.merge(listOf(a, b))
        assertEquals(185L, m.steps) // 100 + 85
        assertEquals(12950L, m.distanceCm) // 7000 + 5950
    }

    @Test
    fun ignoresUnmeasuredAndZeroSteps() {
        val a = listOf(Sample(t0, -1, -1), Sample(t0 + 60, 0, 0), Sample(t0 + 120, 10, -1))
        val m = CrossDeviceSteps.merge(listOf(a))
        assertEquals(10L, m.steps)
        assertEquals(0L, m.distanceCm)
    }

    @Test
    fun sameMinuteSamplesOfOneWatchAdd() {
        val a = listOf(Sample(t0, 5, 100), Sample(t0 + 30, 7, 200))
        assertEquals(12L, CrossDeviceSteps.merge(listOf(a)).steps)
    }

    @Test
    fun emptyIsZero() {
        assertEquals(0L, CrossDeviceSteps.merge(emptyList()).steps)
    }
}
