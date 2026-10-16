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
    /** The code the watch uses for a normal sinus rhythm. */
    public static final long RESULT_NORMAL_SINUS = 0x01020401L;

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
        return type == 0x0103;
    }

    public boolean hasSignal() {
        return signal != null && signal.length > 0;
    }

    /** True when the watch called this recording a normal sinus rhythm. */
    public boolean isNormalRhythm() {
        return result == RESULT_NORMAL_SINUS;
    }
}
