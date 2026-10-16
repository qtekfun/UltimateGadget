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
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import org.slf4j.LoggerFactory

/**
 * Lets the per-brand settings customizers (the classes that fill in lists at runtime, hide rows,
 * set summaries and ranges) run against the native settings tree.
 *
 * A customizer works on androidx [Preference] objects found through a handler. This bridge
 * creates a headless [Preference] for each native node, hands them to the customizer through the
 * handler interface, and then reads back what the customizer changed (list entries, visibility,
 * slider range, summary) and applies it to the nodes. Nothing is rendered by androidx.
 */
internal class CustomizerBridge(
    private val ctx: Context,
    private val dev: GBDevice,
) : DeviceSpecificSettingsHandler {

    private val prefs = LinkedHashMap<String, Preference>()

    // ---- DeviceSpecificSettingsHandler / SettingsRenderHost -------------------------------

    @Suppress("UNCHECKED_CAST")
    override fun <T : Preference> findPreference(preferenceKey: CharSequence): T? = prefs[preferenceKey.toString()] as T?

    override fun addPreferenceHandlerFor(preferenceKey: String) {}
    override fun addPreferenceHandlerFor(preferenceKey: String, extraListener: Preference.OnPreferenceChangeListener?) {}
    override fun notifyPreferenceChanged(preferenceKey: String) {}
    override fun setInputTypeFor(preferenceKey: String, editTypeFlags: Int) {}
    override fun getDevice(): GBDevice = dev
    override fun getContext(): Context = ctx
    override fun navigateToScreen(screen: PreferenceScreen) {}
    override fun addXmlPreferences(resId: Int) {}
    override fun getPreferenceScreen(): PreferenceScreen? = null

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> = throw UnsupportedOperationException("not available while building the settings tree")

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>,
    ): ActivityResultLauncher<I> = throw UnsupportedOperationException("not available while building the settings tree")

    // ---- bridging --------------------------------------------------------------------------

    private fun register(nodes: List<PrefNode>) {
        for (n in nodes) {
            when (n) {
                is PrefNode.SubScreen -> register(n.children)
                is PrefNode.Conditional -> register(n.children)
                is PrefNode.Switch -> put(n.key, SwitchPreferenceCompat(ctx).also { it.isChecked = n.default })
                is PrefNode.SingleChoice -> put(n.key, ListPreference(ctx).also {
                    it.entries = n.entries.toTypedArray<CharSequence>()
                    it.entryValues = n.values.toTypedArray<CharSequence>()
                    it.value = n.default.takeIf { d -> d in n.values }
                })
                is PrefNode.MultiChoice -> put(n.key, MultiSelectListPreference(ctx).also {
                    it.entries = n.entries.toTypedArray<CharSequence>()
                    it.entryValues = n.values.toTypedArray<CharSequence>()
                })
                is PrefNode.Text -> put(n.key, EditTextPreference(ctx))
                is PrefNode.Slider -> put(n.key, SeekBarPreference(ctx).also {
                    it.min = n.min
                    it.max = n.max
                })
                is PrefNode.Sortable -> put(n.key, runCatching {
                    com.mobeta.android.dslv.DragSortListPreference(ctx, null).also {
                        it.entries = n.entries.toTypedArray<CharSequence>()
                        it.entryValues = n.values.toTypedArray<CharSequence>()
                    } as Preference
                }.getOrDefault(Preference(ctx)))
                is PrefNode.TimePicker -> put(n.key, Preference(ctx))
                is PrefNode.DatePicker -> put(n.key, Preference(ctx))
                is PrefNode.FilePicker -> put(n.key, Preference(ctx))
                is PrefNode.FolderPicker -> put(n.key, Preference(ctx))
                is PrefNode.Action -> n.key?.let { put(it, Preference(ctx)) }
                is PrefNode.Info -> n.key?.let { put(it, Preference(ctx)) }
                else -> {}
            }
        }
    }

    private fun put(key: String, p: Preference) {
        p.key = key
        prefs[key] = p
    }

    /** Re-creates [nodes] with whatever the customizer changed on the matching preferences. */
    private fun apply(nodes: List<PrefNode>): List<PrefNode> = nodes.mapNotNull { n ->
        when (n) {
            is PrefNode.SubScreen -> n.copy(children = apply(n.children))
            is PrefNode.Conditional -> n.copy(children = apply(n.children))
            is PrefNode.SingleChoice -> prefs[n.key]?.let { p ->
                if (!p.isVisible) null else {
                    val lp = p as? ListPreference
                    val e = lp?.entries?.map { it.toString() }
                    val v = lp?.entryValues?.map { it.toString() }
                    if (e != null && v != null && e.size == v.size && e.isNotEmpty()) {
                        n.copy(entries = e, values = v, summary = n.summary ?: staticSummary(p))
                    } else n.copy(summary = n.summary ?: staticSummary(p))
                }
            } ?: n
            is PrefNode.MultiChoice -> prefs[n.key]?.let { p ->
                if (!p.isVisible) null else {
                    val mp = p as? MultiSelectListPreference
                    val e = mp?.entries?.map { it.toString() }
                    val v = mp?.entryValues?.map { it.toString() }
                    if (e != null && v != null && e.size == v.size && e.isNotEmpty()) n.copy(entries = e, values = v) else n
                }
            } ?: n
            is PrefNode.Sortable -> prefs[n.key]?.let { p ->
                if (!p.isVisible) null else {
                    val lp = p as? ListPreference
                    val e = lp?.entries?.map { it.toString() }
                    val v = lp?.entryValues?.map { it.toString() }
                    if (e != null && v != null && e.size == v.size && e.isNotEmpty()) n.copy(entries = e, values = v) else n
                }
            } ?: n
            is PrefNode.Slider -> prefs[n.key]?.let { p ->
                if (!p.isVisible) null else (p as? SeekBarPreference)?.let { sp ->
                    n.copy(min = sp.min, max = sp.max, summary = n.summary ?: staticSummary(p))
                } ?: n
            } ?: n
            is PrefNode.Switch -> prefs[n.key]?.let { p -> if (!p.isVisible) null else n.copy(summary = n.summary ?: staticSummary(p)) } ?: n
            is PrefNode.Text -> prefs[n.key]?.let { p -> if (!p.isVisible) null else n.copy(summary = n.summary ?: staticSummary(p)) } ?: n
            is PrefNode.TimePicker -> prefs[n.key]?.let { p -> if (!p.isVisible) null else n } ?: n
            else -> n
        }
    }

    private fun staticSummary(p: Preference): String? = p.summary?.toString()?.takeIf { it.isNotBlank() }

    companion object {
        private val LOG = LoggerFactory.getLogger(CustomizerBridge::class.java)

        /** Runs the device's customizer (if any) over [nodes] and returns the adjusted tree. */
        fun customize(ctx: Context, device: GBDevice, nodes: List<PrefNode>): List<PrefNode> {
            val customizer = runCatching { device.deviceCoordinator.getDeviceSpecificSettingsCustomizer(device) }.getOrNull()
                ?: return nodes
            val bridge = CustomizerBridge(ctx, device)
            bridge.register(nodes)
            val prefs = Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.address))
            // The classic fragment calls the customizer once per screen it opens; do the same for the
            // root and for every sub-screen, all against the one shared set of preferences.
            val screenKeys = mutableListOf<String?>(null)
            fun collect(list: List<PrefNode>) {
                for (n in list) {
                    if (n is PrefNode.SubScreen) { n.key?.let { screenKeys += it }; collect(n.children) }
                    if (n is PrefNode.Conditional) collect(n.children)
                }
            }
            collect(nodes)
            for (key in screenKeys) {
                runCatching { customizer.customizeSettings(bridge, prefs, key) }
                    .onFailure { LOG.debug("Customizer failed for root key {}: {}", key, it.toString()) }
            }
            return bridge.apply(nodes)
        }
    }
}
