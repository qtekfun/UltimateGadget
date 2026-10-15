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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.devsettings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Renders an app-wide preference XML (not tied to a device) with the native settings screen, so
 * the few remaining app-level preference pages no longer use the classic preference UI. The XML
 * resource and the title come in as extras; values are read and written straight to the app
 * preferences.
 */
class UltimateXmlPrefsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val xml = intent.getIntExtra(EXTRA_XML, 0)
        val titleRes = intent.getIntExtra(EXTRA_TITLE, 0)
        if (xml == 0) {
            finish()
            return
        }
        val nodes = DeviceSettingsParser.parseResource(resources, xml)
        val store = DeviceSettingsStore.forApp(GBApplication.getPrefs().preferences) { key ->
            // The reconnect-by-scan service only reads this at start-up.
            if (key == RECONNECT_SCAN_KEY) {
                Toast.makeText(this, getString(R.string.prompt_restart_gadgetbridge), Toast.LENGTH_LONG).show()
            }
        }
        setContent {
            UltimateTheme {
                UltimateDeviceSettingsScreen(
                    rootTitle = if (titleRes != 0) getString(titleRes) else "",
                    rootNodes = nodes,
                    store = store,
                    hasClassicFallback = false,
                    onBack = { finish() },
                    onDelegate = {},
                    onAction = {},
                )
            }
        }
    }

    companion object {
        private const val EXTRA_XML = "ug_xml_res"
        private const val EXTRA_TITLE = "ug_title_res"
        private const val RECONNECT_SCAN_KEY = "prefs_general_key_auto_reconnect_scan"

        fun intent(context: Context, xmlRes: Int, titleRes: Int): Intent =
            Intent(context, UltimateXmlPrefsActivity::class.java)
                .putExtra(EXTRA_XML, xmlRes)
                .putExtra(EXTRA_TITLE, titleRes)
    }
}
