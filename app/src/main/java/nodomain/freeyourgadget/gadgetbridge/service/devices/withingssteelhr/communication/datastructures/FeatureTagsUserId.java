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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication.datastructures;

import java.nio.ByteBuffer;

/** Header of a feature-tag list: the Withings user id the tags apply to (0 = the paired user). */
public class FeatureTagsUserId extends WithingsStructure {
    private final int userId;

    public FeatureTagsUserId(final int userId) {
        this.userId = userId;
    }

    @Override
    public short getLength() {
        return HEADER_SIZE + 4;
    }

    @Override
    protected void fillinTypeSpecificData(ByteBuffer buffer) {
        buffer.putInt(userId);
    }

    @Override
    public short getType() {
        return WithingsStructureType.FEATURE_TAGS_USER_ID;
    }
}
