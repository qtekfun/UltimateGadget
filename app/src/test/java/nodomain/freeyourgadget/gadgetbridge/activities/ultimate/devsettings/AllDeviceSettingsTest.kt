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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ActionSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.CategorySetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.GroupSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.InfoSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ScreenSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.XmlScreenSetting
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase

/**
 * Walks the settings of EVERY device type through the native settings pipeline and checks that
 * nothing is dropped, left as a "not available" hand-off row, or impossible to change.
 *
 *  - the tree builds for every device (no exception),
 *  - no row is a [PrefNode.Delegated] hand-off,
 *  - every preference key a coordinator declares (XML or settings DSL) has a native node,
 *  - every option can really be changed: a sample value is written through [DeviceSettingsStore],
 *    read back unchanged, and reported through the change callback.
 */
class AllDeviceSettingsTest : TestBase() {

    private val ctx: Context get() = app.applicationContext

    private fun deviceOf(type: DeviceType) =
        GBDevice("00:11:22:33:44:55", type.name, null, null, type)

    private fun allTypes() = DeviceType.values().filter { it != DeviceType.UNKNOWN }

    private fun flatten(nodes: List<PrefNode>): List<PrefNode> =
        nodes.flatMap {
            when (it) {
                is PrefNode.SubScreen -> listOf(it) + flatten(it.children)
                is PrefNode.Conditional -> flatten(it.children)
                else -> listOf(it)
            }
        }

    private fun keyOf(n: PrefNode): String? = when (n) {
        is PrefNode.Switch -> n.key
        is PrefNode.SingleChoice -> n.key
        is PrefNode.Text -> n.key
        is PrefNode.Slider -> n.key
        is PrefNode.TimePicker -> n.key
        is PrefNode.MultiChoice -> n.key
        is PrefNode.Sortable -> n.key
        is PrefNode.DatePicker -> n.key
        is PrefNode.FilePicker -> n.key
        is PrefNode.FolderPicker -> n.key
        is PrefNode.Action -> n.key
        is PrefNode.SubScreen -> n.key
        is PrefNode.Delegated -> n.key
        is PrefNode.Info -> n.key
        else -> null
    }

    private data class Built(val type: DeviceType, val nodes: List<PrefNode>?, val error: Throwable?)

    private fun buildAll(): List<Built> = allTypes().map { type ->
        runCatching { DeviceSettingsTree.build(ctx, deviceOf(type)).nodes }
            .fold({ Built(type, it, null) }, { Built(type, null, it) })
    }

    @Test
    fun treeBuildsForEveryDevice() {
        val failed = buildAll().filter { it.error != null }
        assertTrue(
            "Settings tree failed to build for: " +
                failed.joinToString { "${it.type} (${it.error!!.javaClass.simpleName}: ${it.error.message})" },
            failed.isEmpty(),
        )
    }

    @Test
    fun everyActionRowHasAHandler() {
        val unhandled = sortedSetOf<String>()
        for (type in allTypes()) {
            val built = runCatching { DeviceSettingsTree.build(ctx, deviceOf(type)) }.getOrNull() ?: continue
            for (a in flatten(built.nodes).filterIsInstance<PrefNode.Action>()) {
                val k = a.key ?: continue
                if (k in ActionRouting.editors || k in ActionRouting.nativeScreens || k in built.dslActions ||
                    k in ActionRouting.CANNED_GENERIC || k == ActionRouting.CANNED_DISMISS_CALL || k in COMMAND_KEYS
                ) continue
                unhandled += "$k ($type)"
            }
        }
        assertTrue("Action rows with no handler:\n" + unhandled.joinToString("\n"), unhandled.isEmpty())
    }

    @Test
    fun huaweiWatchesOfferTheAccountIdUnderAuthentication() {
        val missing = allTypes().filter { type ->
            val coordinator = runCatching { type.deviceCoordinator }.getOrNull()
            coordinator is nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiCoordinator
        }.filter { type ->
            val tree = DeviceSettingsTree.build(ctx, deviceOf(type)).nodes
            val auth = tree.filterIsInstance<PrefNode.SubScreen>()
                .firstOrNull { it.key == nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen.AUTHENTICATION.key }
            auth == null || flatten(auth.children).none { it is PrefNode.Text && it.key == "huawei_account" }
        }
        assertTrue("Huawei devices without the account id field: $missing", missing.isEmpty())
    }

    @Test
    fun noOptionIsLeftAsAHandOffRow() {
        val offenders = buildAll().flatMap { b ->
            flatten(b.nodes.orEmpty()).filterIsInstance<PrefNode.Delegated>().map { "${b.type}: '${it.title}' (${it.key})" }
        }
        assertTrue("Options still rendered as 'not available':\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun everyDeclaredKeyHasANode() {
        val problems = mutableListOf<String>()
        for (type in allTypes()) {
            val device = deviceOf(type)
            val coordinator = runCatching { type.deviceCoordinator }.getOrNull() ?: continue
            val tree = runCatching { DeviceSettingsTree.build(ctx, device) }.getOrNull() ?: continue
            // Rows the brand's customizer hides on purpose (unsupported features) are not missing.
            val present = flatten(tree.nodes).mapNotNull { keyOf(it) }.toSet() + tree.hidden

            val declared = mutableSetOf<String>()
            // Settings DSL
            runCatching { coordinator.getDeviceSettings(device) }.getOrNull()?.let { spec ->
                fun walk(items: List<DeviceSetting>) {
                    for (i in items) {
                        // An action with no title, summary or click is only a visual separator.
                        val separator = i is ActionSetting && i.title == 0 && i.titleText == null &&
                            i.summary == 0 && i.summaryProvider == null && i.onClick == null
                        val skip = i is CategorySetting || i is InfoSetting || i is XmlScreenSetting || separator
                        if (!skip && i.key.isNotEmpty()) declared += i.key
                        if (i is XmlScreenSetting) declared += xmlKeys(i.subScreens)
                        if (i is ScreenSetting) declared += xmlKeys(i.xmlSubScreens)
                        if (i is GroupSetting) walk(i.children)
                    }
                }
                walk(spec.items)
            }
            // Preference XML
            runCatching { coordinator.getDeviceSpecificSettings(device) }.getOrNull()?.let { s ->
                declared += xmlKeys(s.rootScreens)
                nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen.values()
                    .forEach { screen -> s.getScreen(screen.key)?.let { declared += xmlKeys(it) } }
            }
            // Authentication settings (Huawei account id, auth keys, ...)
            runCatching { coordinator.supportedDeviceSpecificAuthenticationSettings }.getOrNull()?.let {
                declared += xmlKeys(it.toList())
            }
            val missing = declared - present
            if (missing.isNotEmpty()) problems += "$type: ${missing.sorted()}"
        }
        assertTrue("Declared preference keys with no native node:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /** All android:key values found in the given preference XML resources. */
    private fun xmlKeys(xmlIds: List<Int>): Set<String> {
        val out = mutableSetOf<String>()
        for (id in xmlIds) {
            runCatching {
                val p = ctx.resources.getXml(id)
                while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (p.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                    val tag = p.name.substringAfterLast('.')
                    if (tag in NON_PREFERENCE_TAGS) continue
                    p.getAttributeValue("http://schemas.android.com/apk/res/android", "key")?.let { out += it }
                }
            }
        }
        return out
    }

    @Test
    fun everyOptionCanBeChangedAndReadBack() {
        val problems = mutableListOf<String>()
        var checked = 0
        for (b in buildAll()) {
            val prefs = ctx.getSharedPreferences("coverage-" + b.type.name, Context.MODE_PRIVATE)
            prefs.edit().clear().commit()
            val changed = mutableListOf<String>()
            val store = DeviceSettingsStore.forApp(prefs) { changed += it }
            for (n in flatten(b.nodes.orEmpty())) {
                val where = "${b.type}: ${keyOf(n)}"
                fun expect(cond: Boolean, msg: String) { if (!cond) problems += "$where $msg" }
                when (n) {
                    is PrefNode.Switch -> {
                        store.putBoolean(n.key, !n.default); checked++
                        expect(store.getBoolean(n.key, n.default) == !n.default, "switch not persisted")
                    }
                    is PrefNode.SingleChoice -> {
                        expect(n.entries.size == n.values.size, "list entries and values differ in length")
                        expect(n.entries.isNotEmpty() || n.key in FILLED_BY_DEVICE, "list has no usable entries")
                        n.values.lastOrNull()?.let {
                            store.putString(n.key, it); checked++
                            expect(store.getString(n.key, "") == it, "list value not persisted")
                        }
                    }
                    is PrefNode.Text -> {
                        store.putString(n.key, "7"); checked++
                        expect(store.getString(n.key, "") == "7", "text not persisted")
                    }
                    is PrefNode.Slider -> {
                        expect(n.min <= n.max, "slider range inverted")
                        store.putInt(n.key, n.max); checked++
                        expect(store.getInt(n.key, n.min) == n.max, "slider not persisted")
                    }
                    is PrefNode.TimePicker -> {
                        store.putString(n.key, "12:34"); checked++
                        expect(store.getString(n.key, "") == "12:34", "time not persisted")
                    }
                    is PrefNode.MultiChoice -> {
                        val pick = n.values.take(1).toSet()
                        store.putStringSet(n.key, pick); checked++
                        expect(store.getStringSet(n.key, emptySet()) == pick, "multi-choice not persisted")
                    }
                    is PrefNode.Sortable -> {
                        val order = n.values.reversed().joinToString(",")
                        store.putString(n.key, order); checked++
                        expect(store.getString(n.key, "") == order, "order not persisted")
                    }
                    is PrefNode.DatePicker -> {
                        store.putString(n.key, "2020-01-02"); checked++
                        expect(store.getString(n.key, "") == "2020-01-02", "date not persisted")
                    }
                    else -> {}
                }
            }
            assertTrue("${b.type}: no change notifications were emitted", checked == 0 || changed.isNotEmpty())
        }
        assertTrue("Options that cannot be changed:\n" + problems.joinToString("\n"), problems.isEmpty())
        assertEquals(true, checked > 0)
    }

    private companion object {
        val COMMAND_KEYS = setOf(
            // Handled by the device support's onSendConfiguration(key).
            "battery_discharge_intervals_set",
            // Upstream declares this row but no support code reacts to it (a no-op there as well).
            "pref_even_realities_g1_screen_calibrate_angle",
        )
        /**
         * Lists whose options only exist once the watch/headset has connected and reported them
         * (stored in the device preferences). They are shown inert until then, by design.
         */
        val FILLED_BY_DEVICE = setOf(
            "always_on_display_style",
            "pref_soundcore_equalizer_band9_freq",
            "bose_voice_prompts_language",
            "bose_shortcut",
            "pref_cardo_fm_preset",
        )
        val NON_PREFERENCE_TAGS = setOf("PreferenceScreen", "PreferenceCategory", "intent", "SearchPreference")
    }
}
