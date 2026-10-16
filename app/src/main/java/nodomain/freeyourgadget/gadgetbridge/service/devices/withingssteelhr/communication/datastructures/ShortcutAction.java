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

/** The action of the long press on the crown / button. */
public class ShortcutAction extends ByteSetting {
    public ShortcutAction() {
        super(WithingsStructureType.SHORTCUT_ACTION, new byte[1]);
    }

    public ShortcutAction(final byte action) {
        super(WithingsStructureType.SHORTCUT_ACTION, new byte[]{action});
    }

    public byte getAction() {
        return get(0);
    }
}
