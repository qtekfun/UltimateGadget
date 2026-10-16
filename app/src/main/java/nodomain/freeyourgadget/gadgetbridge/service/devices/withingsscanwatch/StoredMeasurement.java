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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingsscanwatch;

/** A measurement the watch kept (an ECG, a SpO2 reading...) with everything it sent for it. */
public final class StoredMeasurement {
    /** What a measurement is, worked out from its content (the header's type is the same for ECG and SpO2). */
    public static final int KIND_ECG = 1;
    public static final int KIND_SPO2 = 2;

    /** The code the watch uses for a normal sinus rhythm. */
    public static final long RESULT_NORMAL_SINUS = 0x01020401L;
    /** The code of an SpO2 reading the watch called "inconclusive" (seen on a real one, with a placeholder value). */
    public static final long RESULT_SPO2_INCONCLUSIVE = 0x02010011L;

    public final long timestamp;
    public final int type;
    /** The watch's classification code (see {@link #describeResult}), or -1 when none was sent. */
    public final long result;
    /** Average heart rate in bpm, or -1 when the watch did not send one. */
    public final int heartRate;
    public final int sampleRate;
    public final int durationSeconds;
    /** The values the watch sent, as "type:value" separated by ';'. */
    public final String readings;
    /** The signal exactly as received (see {@link ScanWatchSignalCodec}); empty when there is none. */
    public final byte[] signal;
    /** What must be echoed to the watch to delete this measurement, or null. */
    public final byte[] deleteKey;

    public StoredMeasurement(final long timestamp, final int type, final long result, final int heartRate,
                             final int sampleRate, final int durationSeconds, final String readings,
                             final byte[] signal, final byte[] deleteKey) {
        this.timestamp = timestamp;
        this.type = type;
        this.result = result;
        this.heartRate = heartRate;
        this.sampleRate = sampleRate;
        this.durationSeconds = durationSeconds;
        this.readings = readings;
        this.signal = signal;
        this.deleteKey = deleteKey;
    }

    public boolean isEcg() {
        return type == KIND_ECG;
    }

    public boolean isSpo2() {
        return type == KIND_SPO2;
    }

    /** True when the watch could not conclude the SpO2 reading: its value must not be taken as a result. */
    public boolean isInconclusiveSpo2() {
        return isSpo2() && result == RESULT_SPO2_INCONCLUSIVE;
    }

    /** The SpO2 in tenths of a percent (941 is 94.1 %), or -1 when the measurement has none. */
    public int spo2Tenths() {
        return spo2TenthsFrom(readings);
    }

    public static int spo2TenthsFrom(final String readings) {
        if (readings == null) {
            return -1;
        }
        for (final String reading : readings.split(";")) {
            final String[] parts = reading.split(":");
            if (parts.length >= 2 && parts[0].equals("54")) {
                final int exponent = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
                final long value = Long.parseLong(parts[1]);
                // value * 10^exponent percent, in tenths
                final double percent = value * Math.pow(10, exponent);
                return (int) Math.round(percent * 10);
            }
        }
        return -1;
    }

    public boolean hasSignal() {
        return signal != null && signal.length > 0;
    }

    /** True when the watch called this recording a normal sinus rhythm. */
    public boolean isNormalRhythm() {
        return result == RESULT_NORMAL_SINUS;
    }
}
