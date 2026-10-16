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

import android.app.Activity
import android.content.Intent
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric

/** Opens every screen a settings action row routes to, the way a tap on the row would. */
class ActionScreensLaunchTest : TestBase() {

    private fun device(type: DeviceType) = GBDevice("00:11:22:33:44:55", type.name, null, null, type)

    private fun launch(cls: Class<*>, type: DeviceType, mustStayOpen: Boolean = true): String? = runCatching {
        val intent = Intent(app, cls).putExtra(GBDevice.EXTRA_DEVICE, device(type))
        val controller = Robolectric.buildActivity(cls.asSubclass(Activity::class.java), intent)
        controller.create().start().resume()
        check(!mustStayOpen || !controller.get().isFinishing) { "closed itself on start" }
        controller.pause().stop().destroy()
    }.exceptionOrNull()?.let { "${cls.simpleName} (${type.name}): ${it.javaClass.simpleName}: ${it.message}" }

    /** Every device type whose settings tree has an action row with [key]. */
    private fun typesShowing(key: String): List<DeviceType> = DeviceType.values().filter { type ->
        type != DeviceType.UNKNOWN && runCatching {
            fun has(nodes: List<PrefNode>): Boolean = nodes.any {
                when (it) {
                    is PrefNode.SubScreen -> has(it.children)
                    is PrefNode.Conditional -> has(it.children)
                    is PrefNode.Action -> it.key == key
                    else -> false
                }
            }
            has(DeviceSettingsTree.build(app, device(type)).nodes)
        }.getOrDefault(false)
    }

    @Test
    fun everyRoutedEditorOpensOnEveryDeviceThatShowsIt() {
        val failures = mutableListOf<String>()
        for ((key, cls) in ActionRouting.editors) {
            // Some rows only appear once the device reports a capability, so a bare test device does
            // not list them; those are launched below with a device that supports them.
            val types = typesShowing(key)
            // The FTP browser closes itself, by design, while the watch has no FTP session yet.
            for (type in types) launch(cls, type, mustStayOpen = key != "ftp_server_browse")?.let { failures += "$key -> $it" }
        }
        assertTrue("Screens that failed to open:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun capabilityGatedEditorsOpenOnASupportingDevice() {
        // Capabilities the real device reports once connected.
        val garmin = nodomain.freeyourgadget.gadgetbridge.GBApplication.getDeviceSpecificSharedPrefs("00:11:22:33:44:55")
        garmin.edit()
            .putStringSet("garmin_capabilities", setOf("WORKOUT_DOWNLOAD"))
            .putBoolean(nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiPreferences.FEAT_WIDGETS, true)
            .commit()
        val cases = listOf(
            "pref_workout_templates" to DeviceType.GARMIN_VENU_3,
            "pref_key_internet_firewall" to DeviceType.GARMIN_VENU_3,
            "pref_widgets" to DeviceType.XIAOMI_WATCH_S3,
            "pref_key_audio_recordings" to DeviceType.AMAZFITBALANCE,
        )
        val failures = cases.mapNotNull { (key, type) ->
            launch(ActionRouting.editors.getValue(key), type)?.let { "$key -> $it" }
        }
        assertTrue("Screens that failed to open:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun tappingARowStartsItsScreenFromTheSettingsActivity() {
        val dev = device(DeviceType.HUAWEIWATCHULTIMATE2)
        val activity = Robolectric.buildActivity(
            UltimateDeviceSettingsActivity::class.java,
            UltimateDeviceSettingsActivity.intent(app, dev),
        ).create().start().resume().get()
        val onAction = UltimateDeviceSettingsActivity::class.java.getDeclaredMethod(
            "onAction", String::class.java, GBDevice::class.java,
        ).apply { isAccessible = true }
        val shadow = org.robolectric.Shadows.shadowOf(activity)
        val wrong = mutableListOf<String>()
        for ((key, cls) in ActionRouting.editors) {
            onAction.invoke(activity, key, dev)
            val started = shadow.nextStartedActivity?.component?.className
            if (started != cls.name) wrong += "$key started $started, expected ${cls.name}"
        }
        assertTrue("Rows that did not start their screen:\n" + wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun cannedMessageButtonsSendTheStoredMessages() {
        val dev = device(DeviceType.HUAWEIWATCHULTIMATE2)
        val activity = Robolectric.buildActivity(
            UltimateDeviceSettingsActivity::class.java,
            UltimateDeviceSettingsActivity.intent(app, dev),
        ).create().start().resume().get()
        val onAction = UltimateDeviceSettingsActivity::class.java.getDeclaredMethod(
            "onAction", String::class.java, GBDevice::class.java,
        ).apply { isAccessible = true }
        val prefs = nodomain.freeyourgadget.gadgetbridge.GBApplication.getDeviceSpecificSharedPrefs(dev.address)
        prefs.edit().putString("canned_reply_1", "On my way").commit()
        val appShadow = org.robolectric.Shadows.shadowOf(app)
        while (appShadow.nextStartedService != null) { /* drain */ }
        for (key in listOf(ActionRouting.CANNED_GENERIC, ActionRouting.CANNED_DISMISS_CALL)) {
            onAction.invoke(activity, key, dev)
            val svc = appShadow.nextStartedService
            assertTrue("$key started no device command", svc != null)
            assertTrue(
                "$key sent ${svc!!.action}",
                svc.action == nodomain.freeyourgadget.gadgetbridge.model.DeviceService.ACTION_SETCANNEDMESSAGES,
            )
        }
    }
}
