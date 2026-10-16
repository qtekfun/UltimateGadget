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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.health

import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgDataSample
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgDataSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgSummarySample
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiEcgSummarySampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * The ECGs stored for a Huawei watch. The watch's own result code is kept as it comes: no brand
 * mapping of Huawei's codes to names has been verified against real recordings yet.
 */
object HuaweiHealthSource : HealthMeasurementSource {
    /** Only watches that have stored an ECG: there is no flag that says a Huawei watch can make one. */
    override fun supports(device: GBDevice): Boolean {
        if (device.deviceCoordinator !is HuaweiCoordinator) return false
        return runCatching {
            nodomain.freeyourgadget.gadgetbridge.GBApplication.acquireDB().use { db ->
                val dev = DBHelper.findDevice(device, db.daoSession) ?: return@use false
                db.daoSession.huaweiEcgSummarySampleDao.queryBuilder()
                    .where(HuaweiEcgSummarySampleDao.Properties.DeviceId.eq(dev.id))
                    .limit(1).count() > 0
            }
        }.getOrDefault(false)
    }

    override fun load(db: DBHandler, device: GBDevice): List<HealthMeasurement> {
        val session = db.daoSession
        val dev = DBHelper.findDevice(device, session) ?: return emptyList()
        val summaries = session.huaweiEcgSummarySampleDao.queryBuilder()
            .where(HuaweiEcgSummarySampleDao.Properties.DeviceId.eq(dev.id))
            .orderDesc(HuaweiEcgSummarySampleDao.Properties.StartTimestamp)
            .list()
        return summaries.map { s ->
            val data = session.huaweiEcgDataSampleDao.queryBuilder()
                .where(HuaweiEcgDataSampleDao.Properties.EcgId.eq(s.ecgId))
                .orderAsc(HuaweiEcgDataSampleDao.Properties.TimeDelta)
                .list()
            convert(device.address, device.name.orEmpty(), s, data)
        }
    }

    /** Pure conversion, so it can be tested without a database. */
    fun convert(address: String, name: String, s: HuaweiEcgSummarySample, data: List<HuaweiEcgDataSample>): EcgRecord {
        val seconds = ((s.endTimestamp - s.startTimestamp) / 1000).toInt().coerceAtLeast(0)
        val rate = if (seconds > 0) data.size / seconds else 0
        return EcgRecord(
            address, name, s.startTimestamp / 1000, "Huawei", seconds, rate,
            FloatArray(data.size) { data[it].value },
            s.averageHeartRate.takeIf { it > 0 },
            EcgClassification.UNKNOWN,
            s.arrhythmiaType,
        )
    }
}
