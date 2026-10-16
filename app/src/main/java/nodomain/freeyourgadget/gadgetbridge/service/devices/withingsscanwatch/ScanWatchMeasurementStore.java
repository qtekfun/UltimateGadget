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

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanWatchMeasurement;
import nodomain.freeyourgadget.gadgetbridge.entities.WithingsScanWatchMeasurementDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/** Keeps the watch's measurements in the app database (one row per time and kind, so a repeat replaces). */
public final class ScanWatchMeasurementStore {
    private ScanWatchMeasurementStore() {
    }

    /** Saves the measurements and returns those that were read back from the database afterwards. */
    public static List<StoredMeasurement> save(final DBHandler db, final GBDevice gbDevice, final List<StoredMeasurement> measurements) {
        final DaoSession session = db.getDaoSession();
        final Device device = DBHelper.getDevice(gbDevice, session);
        final long userId = DBHelper.getUser(session).getId();
        final WithingsScanWatchMeasurementDao dao = session.getWithingsScanWatchMeasurementDao();
        final List<StoredMeasurement> verified = new ArrayList<>();
        for (final StoredMeasurement m : measurements) {
            dao.insertOrReplace(new WithingsScanWatchMeasurement(m.timestamp, device.getId(), m.type, userId, m.result,
                    m.heartRate, m.sampleRate, m.durationSeconds, m.readings, m.hasSignal() ? m.signal : null));
            final WithingsScanWatchMeasurement stored = dao.queryBuilder()
                    .where(WithingsScanWatchMeasurementDao.Properties.Timestamp.eq(m.timestamp),
                            WithingsScanWatchMeasurementDao.Properties.DeviceId.eq(device.getId()),
                            WithingsScanWatchMeasurementDao.Properties.Type.eq(m.type))
                    .unique();
            final int expected = m.hasSignal() ? m.signal.length : 0;
            final int actual = stored == null || stored.getSignal() == null ? 0 : stored.getSignal().length;
            if (stored != null && actual == expected) {
                verified.add(m);
            }
        }
        return verified;
    }

    public static List<WithingsScanWatchMeasurement> load(final DBHandler db, final GBDevice gbDevice) {
        final DaoSession session = db.getDaoSession();
        final Device device = DBHelper.findDevice(gbDevice, session);
        if (device == null) {
            return new ArrayList<>();
        }
        return session.getWithingsScanWatchMeasurementDao().queryBuilder()
                .where(WithingsScanWatchMeasurementDao.Properties.DeviceId.eq(device.getId()))
                .orderDesc(WithingsScanWatchMeasurementDao.Properties.Timestamp)
                .list();
    }
}
