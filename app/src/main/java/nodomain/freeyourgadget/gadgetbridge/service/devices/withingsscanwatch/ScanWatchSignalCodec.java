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

/**
 * The ECG strip as the ScanWatch stores it: a stream of bytes that is a signed 8-bit difference to
 * the previous sample, except that 0x80 introduces a 16-bit big-endian absolute value (the watch
 * resynchronises every 50 samples and whenever a step does not fit in a byte). Checked on a real
 * 30 s recording: 300 Hz, 14 bits, exactly 9000 samples.
 */
public final class ScanWatchSignalCodec {
    private static final int ESCAPE = 0x80;

    private ScanWatchSignalCodec() {
    }

    public static int[] decode(final byte[] data) {
        int[] out = new int[Math.max(16, data.length)];
        int count = 0;
        int current = 0;
        int i = 0;
        while (i < data.length) {
            final int value = data[i] & 0xff;
            if (value == ESCAPE) {
                if (i + 2 >= data.length) {
                    break; // a truncated escape: nothing trustworthy follows
                }
                current = (short) (((data[i + 1] & 0xff) << 8) | (data[i + 2] & 0xff));
                i += 3;
            } else {
                current += (byte) value;
                i += 1;
            }
            if (count == out.length) {
                out = java.util.Arrays.copyOf(out, out.length * 2);
            }
            out[count++] = current;
        }
        return java.util.Arrays.copyOf(out, count);
    }
}
