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
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Visual category a device is drawn as in the new UI. Instead of one bitmap per model (there are
 * dozens of coordinators), every GBDevice is mapped to one of these categories and rendered with
 * the matching Ultimate icon (`ic_ultimate_dev_*`). The icons are monochrome and tintable, so the
 * card tints them with the theme accent (works in light, dark, AMOLED and Material You).
 */
enum class DeviceIconCategory(@DrawableRes val iconRes: Int) {
    WATCH_ROUND(R.drawable.ic_ultimate_dev_watch_round),
    WATCH_SQUARE(R.drawable.ic_ultimate_dev_watch_square),
    BAND(R.drawable.ic_ultimate_dev_band),
    EARBUDS(R.drawable.ic_ultimate_dev_earbuds),
    SCALE(R.drawable.ic_ultimate_dev_scale),
    RING(R.drawable.ic_ultimate_dev_ring),
    GLASSES(R.drawable.ic_ultimate_dev_glasses),
    GENERIC(R.drawable.ic_ultimate_dev_generic);

    companion object {
        /**
         * Derives a category from a device's coordinator class name plus its name/model, without
         * touching the (huge) DeviceType enum or the per-brand coordinator classes. [coordinatorFqcn]
         * is the fully-qualified coordinator class name (e.g.
         * `...devices.huawei.huaweiwatchgtrunner2.HuaweiWatchGTRunner2Coordinator`); its package
         * segment identifies the device family robustly. Checks run most-specific first; anything
         * unmatched falls back to [GENERIC].
         */
        fun of(coordinatorFqcn: String?, name: String?, model: String?): DeviceIconCategory {
            val cls = (coordinatorFqcn ?: "").lowercase()
            val hay = "$cls ${(name ?: "")} ${(model ?: "")}".lowercase()
            if (cls.isEmpty() && name.isNullOrEmpty()) return GENERIC

            // --- Earbuds / headphones --------------------------------------------------------
            // Family packages that are exclusively audio devices (matched as `.family.`), plus
            // Huawei's audio class names which live under the shared `huawei` package.
            val earbudFamilies = listOf(
                "galaxy_buds", "earfun", "generic_headphones", "moondrop", "nothing", "redmibuds",
                "sennheiser", "shokz", "sony", "soundcore", "bose", "jabra", "onemoresonoflow",
                "cardo", "roidmi", "ugreen", "oppo", "realme", "bandwpseries", "haylou",
            )
            if (earbudFamilies.any { cls.contains(".$it.") } ||
                listOf("freebud", "freeclip", "freelace", "freearc", "headphone", "earbud", "buds")
                    .any { cls.contains(it) }
            ) return EARBUDS

            // --- Scale -----------------------------------------------------------------------
            val scaleFamilies = listOf("generic_scale", "miscale", "qnscale", "xiaomi_hipee")
            if (scaleFamilies.any { cls.contains(".$it.") } || cls.contains("scale")) return SCALE

            // --- Ring ------------------------------------------------------------------------
            if (cls.contains(".ultrahuman.") || cls.contains("ring")) return RING

            // --- Glasses / HUD ---------------------------------------------------------------
            if (cls.contains(".evenrealities.") || hay.contains("glass")) return GLASSES

            // --- Band / pulsera (before watch: Amazfit/Mi/Huawei bands live near watches) -----
            val bandFamilies = listOf(
                "miband", "makibeshr3", "id115", "no1f1", "jyou", "lefun", "tlw64", "xwatch",
                "smaq2oss", "wearfit", "hplus", "fitpro", "sonyswr12", "laxasfit", "keephealth",
                "nxwear", "yawell",
            )
            if (bandFamilies.any { cls.contains(".$it.") } ||
                listOf("miband", "vivosmart", "vivosport", "talkband", "heliostrap").any { cls.contains(it) } ||
                cls.contains("band")
            ) return BAND

            // --- Watch: round vs square ------------------------------------------------------
            if (isWatch(cls)) {
                val square = listOf(
                    "square", "gts", "venusq", "watchfit", "pebble", "pinetime", "bangle", "cmfwatch",
                ).any { cls.contains(it) }
                return if (square) WATCH_SQUARE else WATCH_ROUND
            }

            return GENERIC
        }

        /**
         * True when the coordinator looks like a wrist watch. Garmin/Huami/GloryFit watches sit in
         * a `.watches.` sub-package (bike computers, GPS handhelds and HR straps do not), Huawei /
         * Honor watches carry `watch` in the class name, and the remaining standalone watch
         * platforms are matched by family keyword.
         */
        private fun isWatch(cls: String): Boolean {
            if (cls.contains(".watches.")) return true
            if (cls.contains("watch")) return true
            return listOf(
                "amazfit", "pebble", "pinetime", "bangle", "asteroid", "zetime", "qhybrid",
                "cmfwatch", "moyoung", "zeblaze", "zeppe", "gtr", "gts", "trex", "zepp",
            ).any { cls.contains(it) }
        }
    }
}

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
    /** Which Ultimate icon this device is drawn with (replaces the legacy per-model GB drawable). */
    val category: DeviceIconCategory = DeviceIconCategory.GENERIC,
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
