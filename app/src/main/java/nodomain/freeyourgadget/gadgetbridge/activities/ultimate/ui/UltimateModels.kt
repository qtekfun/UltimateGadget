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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui

import androidx.annotation.DrawableRes

/** Display model for one device card (derived from a GBDevice). */
data class DeviceCardUi(
    val address: String,
    val name: String,
    val typeName: String,
    val stateLabel: String,
    val connected: Boolean,
    val busy: Boolean,
    val batteryLevel: Int,          // -1 = unknown
    val model: String?,
    @DrawableRes val iconRes: Int,
    val accentSeed: Int,            // stable seed for the gradient
    val connecting: Boolean = false, // mid-connection (connecting/initializing), not yet connected
)

/**
 * Live health snapshot for the connected device, shown on the home "hero" card. All fields are
 * loaded off the main thread (DB reads) and are null when the device has no such data yet.
 */
data class HeroStats(
    val steps: Int?,          // today's step count
    val stepsGoal: Int?,      // user step goal (for progress context)
    val heartRate: Int?,      // most recent heart-rate reading, bpm
    val distanceKm: Double?,  // today's distance in km
)

/** One action tile in the device detail screen. [section] groups rows under a header (commercial-app
 * style: Conexión / Reloj / Mapas y navegación / Dispositivo). Sections render in first-seen order. */
data class DeviceOptionUi(
    val id: String,
    val title: String,
    val subtitle: String?,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val section: String = "",
)
