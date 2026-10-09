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
package nodomain.freeyourgadget.gadgetbridge.database.schema;

import android.database.sqlite.SQLiteDatabase;

import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.database.DBUpdateScript;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutSectionsSampleDao;

/**
 * UltimateGadget golf: adds the per-hole golf scorecard columns to the Huawei workout sections
 * table. Every column is a nullable INTEGER with no default, so existing rows (and every non-golf
 * workout) simply read back as NULL and nothing already stored is touched. Golf rounds synced after
 * this upgrade populate them; rounds synced earlier need a re-fetch (the raw section TLV is not
 * stored, so a "Reparse workout data" run cannot backfill them).
 */
public class GadgetbridgeUpdate_152 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        final String table = HuaweiWorkoutSectionsSampleDao.TABLENAME;
        final Property[] golfColumns = new Property[]{
                HuaweiWorkoutSectionsSampleDao.Properties.GolfHoleId,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfPar,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfScore,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfPutts,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfPenalty,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfFairwayHits,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfHandicap,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfValidTracks,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfBackSwingTime,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfDownSwingTime,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfHeadSpeed,
                HuaweiWorkoutSectionsSampleDao.Properties.GolfSwingTempo,
        };
        for (final Property column : golfColumns) {
            if (!DBHelper.existsColumn(table, column.columnName, db)) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN \"" + column.columnName + "\" INTEGER;");
            }
        }
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
