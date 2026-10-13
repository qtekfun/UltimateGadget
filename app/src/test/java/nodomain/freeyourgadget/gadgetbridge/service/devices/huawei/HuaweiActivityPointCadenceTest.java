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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutDataSample;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutSummarySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;

public class HuaweiActivityPointCadenceTest {

    private static HuaweiWorkoutSummarySample summary(final boolean newSteps) {
        final HuaweiWorkoutSummarySample s = new HuaweiWorkoutSummarySample();
        s.setNewSteps(newSteps);
        return s;
    }

    /** A data sample with every optional metric marked absent (-1), like the watch sends them. */
    private static HuaweiWorkoutDataSample sample(final int stepRate, final int cadence) {
        final HuaweiWorkoutDataSample d = new HuaweiWorkoutDataSample();
        d.setTimestamp(1_788_453_861);
        d.setHeartRate((byte) 93);
        d.setSpeed((short) -1);
        d.setStepRate((byte) stepRate);
        d.setCadence((short) cadence);
        d.setSwolf((short) -1);
        d.setStrokeRate((short) -1);
        d.setFrequency((short) -1);
        return d;
    }

    @Test
    public void ellipticalStepRateBecomesCadence() {
        final ActivityPoint p = HuaweiWorkoutGbParser.buildActivityPoint(
                summary(true), ActivityKind.ELLIPTICAL_TRAINER, sample(72, -1));
        assertEquals(72, p.getCadence());
    }

    @Test
    public void stepRateAbove127IsReadUnsigned() {
        final ActivityPoint p = HuaweiWorkoutGbParser.buildActivityPoint(
                summary(true), ActivityKind.ELLIPTICAL_TRAINER, sample(150, -1));
        assertEquals(150, p.getCadence());
    }

    @Test
    public void realCadenceWinsOverStepRate() {
        final ActivityPoint p = HuaweiWorkoutGbParser.buildActivityPoint(
                summary(true), ActivityKind.RUNNING, sample(72, 170));
        assertEquals(170, p.getCadence());
    }

    @Test
    public void oldStepFormatKeepsCadenceUnset() {
        final ActivityPoint p = HuaweiWorkoutGbParser.buildActivityPoint(
                summary(false), ActivityKind.ELLIPTICAL_TRAINER, sample(72, -1));
        assertEquals(-1, p.getCadence());
    }
}
