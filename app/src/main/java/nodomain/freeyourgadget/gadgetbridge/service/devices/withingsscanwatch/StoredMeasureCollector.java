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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureDataExtend;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredMeasureMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMeta;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.StoredSignalMetaExtend;
import nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures.WithingsStructure;

/**
 * Assembles the measurements out of the structures of the watch's answer. A measurement starts with
 * its header and then comes its values, its extension, the description of its signal and the
 * signal itself in chunks; a new header closes the previous measurement.
 */
public final class StoredMeasureCollector {
    private final List<StoredMeasurement> done = new ArrayList<>();
    private Pending current;

    private static final class Pending {
        long timestamp;
        int type;
        long result = -1;
        int heartRate = -1;
        int sampleRate;
        int duration;
        final StringBuilder readings = new StringBuilder();
        final ByteArrayOutputStream signal = new ByteArrayOutputStream();
        byte[] deleteKey;
    }

    public void accept(final List<WithingsStructure> structures) {
        for (final WithingsStructure structure : structures) {
            if (structure instanceof StoredMeasureMeta) {
                close();
                final StoredMeasureMeta meta = (StoredMeasureMeta) structure;
                current = new Pending();
                current.timestamp = meta.getTimestamp();
                current.type = meta.getMeasurementType();
            } else if (current == null) {
                continue;
            } else if (structure instanceof StoredMeasureData) {
                final StoredMeasureData data = (StoredMeasureData) structure;
                if (current.readings.length() > 0) {
                    current.readings.append(';');
                }
                current.readings.append(data.getMeasurementType()).append(':').append(data.getValue());
                if (data.getMeasurementType() == StoredMeasureData.TYPE_AVERAGE_HEART_RATE) {
                    current.heartRate = data.getValue();
                } else if (data.getMeasurementType() == StoredMeasureData.TYPE_ECG_RESULT && current.result < 0) {
                    current.result = data.getValue();
                }
            } else if (structure instanceof StoredMeasureDataExtend) {
                final StoredMeasureDataExtend extend = (StoredMeasureDataExtend) structure;
                if (extend.getMeasurementType() == StoredMeasureData.TYPE_ECG_RESULT) {
                    // The classification lives here; the plain value next to it is usually 0.
                    current.result = extend.getExtraData();
                }
            } else if (structure instanceof StoredSignalMeta) {
                final StoredSignalMeta meta = (StoredSignalMeta) structure;
                current.sampleRate = meta.getSamplingFreq();
                current.deleteKey = meta.getRawPayload();
            } else if (structure instanceof StoredSignalMetaExtend) {
                current.duration = (int) ((StoredSignalMetaExtend) structure).getDurationSeconds();
            } else if (structure instanceof StoredSignalData) {
                final byte[] samples = ((StoredSignalData) structure).getSamples();
                current.signal.write(samples, 0, samples.length);
            }
        }
    }

    /** Closes the measurement in progress and returns everything collected since the last call. */
    public List<StoredMeasurement> finish() {
        close();
        final List<StoredMeasurement> result = new ArrayList<>(done);
        done.clear();
        return result;
    }

    private void close() {
        if (current == null) {
            return;
        }
        done.add(new StoredMeasurement(current.timestamp, current.type, current.result, current.heartRate,
                current.sampleRate, current.duration, current.readings.toString(),
                current.signal.toByteArray(), current.deleteKey));
        current = null;
    }
}
