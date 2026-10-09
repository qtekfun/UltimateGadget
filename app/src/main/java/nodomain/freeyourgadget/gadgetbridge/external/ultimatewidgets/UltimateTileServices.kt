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
package nodomain.freeyourgadget.gadgetbridge.external.ultimatewidgets

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.annotation.RequiresApi
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import org.slf4j.LoggerFactory

/**
 * Base for UltimateGadget Quick Settings tiles. Each tile acts on the active device (the first
 * initialized device, or the first known device otherwise) and runs entirely locally.
 *
 * Quick Settings tiles require API 24 (N); the manifest guards each `<service>` with
 * `tools:targetApi="24"` and the OS never instantiates a [TileService] below that level, so these
 * classes are never loaded on API 23. The [RequiresApi] annotation documents and enforces that.
 */
@RequiresApi(Build.VERSION_CODES.N)
abstract class AbstractUltimateTileService : TileService() {

    /** The device this tile acts on: first initialized device, else the first known device. */
    protected fun activeDevice(): GBDevice? {
        val manager = GBApplication.app()?.deviceManager ?: return null
        val devices = manager.devices ?: return null
        return devices.firstOrNull { it.isInitialized } ?: devices.firstOrNull()
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val device = activeDevice()
        tile.state = if (device != null && device.isInitialized) {
            Tile.STATE_INACTIVE
        } else {
            Tile.STATE_UNAVAILABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = device?.aliasOrName ?: getString(R.string.ultimate_widget_no_device)
        }
        tile.updateTile()
    }

    protected fun toast(messageRes: Int) {
        Toast.makeText(applicationContext, getString(messageRes), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Quick Settings tile that triggers a full data sync ([RecordedDataTypes.TYPE_SYNC]) on the active
 * device, the same action as the dashboard "sync now" button.
 */
@RequiresApi(Build.VERSION_CODES.N)
class UltimateSyncTileService : AbstractUltimateTileService() {
    override fun onClick() {
        super.onClick()
        val device = activeDevice()
        if (device == null || !device.isInitialized) {
            toast(R.string.device_not_connected)
            return
        }
        LOG.debug("Ultimate sync tile: fetching recorded data for {}", device.aliasOrName)
        GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
        toast(R.string.busy_task_fetch_activity_data)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UltimateSyncTileService::class.java)
    }
}

/**
 * Quick Settings tile that makes the active device buzz/ring ("find my watch"), if the device
 * supports it.
 */
@RequiresApi(Build.VERSION_CODES.N)
class UltimateFindWatchTileService : AbstractUltimateTileService() {
    override fun onClick() {
        super.onClick()
        val device = activeDevice()
        if (device == null || !device.isInitialized) {
            toast(R.string.device_not_connected)
            return
        }
        val coordinator = runCatching { device.deviceCoordinator }.getOrNull()
        if (coordinator != null && !coordinator.supportsFindDevice(device)) {
            toast(R.string.ultimate_tile_find_unsupported)
            return
        }
        LOG.debug("Ultimate find-watch tile: triggering find on {}", device.aliasOrName)
        GBApplication.deviceService(device).onFindDevice(true)
        toast(R.string.ultimate_tile_find_watch)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UltimateFindWatchTileService::class.java)
    }
}
