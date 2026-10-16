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
import android.content.res.Resources
import android.content.res.XmlResourceParser
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.xmlpull.v1.XmlPullParser
import org.slf4j.LoggerFactory

/**
 * Presentation model for the Compose device-settings renderer.
 *
 * This layer is purely presentational: it reads the very same preference XML resources and keys
 * that the legacy androidx [nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsFragment]
 * uses, parses them into a small tree, and lets the Compose UI write to the exact same per-device
 * [android.content.SharedPreferences] ("devicesettings_<ADDRESS>") and fire the exact same
 * {@code onSendConfiguration(key)} event. No preference key, default or change-handling path is
 * reinvented here.
 */
/** What an XML `<intent>` child of a preference asks to launch. */
data class LaunchSpec(val targetClass: String?, val action: String?, val data: String?)

sealed interface PrefNode {
    /** A section header (maps to an androidx {@code PreferenceCategory}). */
    data class Category(val title: String) : PrefNode

    /** A boolean toggle (SwitchPreferenceCompat / SwitchPreference / CheckBoxPreference). */
    data class Switch(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: Boolean,
        val dependency: String?,
    ) : PrefNode

    /** A single-choice list (ListPreference and simple list-like subclasses). */
    data class SingleChoice(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: String,
        val entries: List<String>,
        val values: List<String>,
        val dependency: String?,
    ) : PrefNode

    /** A free text / numeric input (EditTextPreference). Persisted as a String, like androidx does. */
    data class Text(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: String,
        val numeric: Boolean,
        val dependency: String?,
    ) : PrefNode

    /** An integer slider (SeekBarPreference). Persisted as an Int, like androidx does. */
    data class Slider(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: Int,
        val min: Int,
        val max: Int,
        val dependency: String?,
    ) : PrefNode

    /** A time-of-day picker ([nodomain.freeyourgadget.gadgetbridge.util.XTimePreference]), persisted as "HH:mm". */
    data class TimePicker(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: String,
        val dependency: String?,
    ) : PrefNode

    /**
     * A click-action preference (plain {@code <Preference>}): it opens a dedicated editor activity
     * or fires a device command, instead of persisting a value. The host decides what each key does.
     */
    data class Action(
        val key: String?,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        /** The `<intent>` the XML row carries, if any: an activity to open or a link to view. */
        val launch: LaunchSpec? = null,
    ) : PrefNode

    /** A multi-choice list (MultiSelectListPreference); persisted as a string set. */
    data class MultiChoice(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: Set<String>,
        val entries: List<String>,
        val values: List<String>,
        val dependency: String?,
    ) : PrefNode

    /** A reorderable list of options; persisted as the comma-joined value order. */
    data class Sortable(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: List<String>,
        val entries: List<String>,
        val values: List<String>,
        val dependency: String?,
    ) : PrefNode

    /** A calendar date picker, persisted as "yyyy-MM-dd". */
    data class DatePicker(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val default: String,
        val minDate: Long,
        val maxDate: Long,
        val dependency: String?,
    ) : PrefNode

    /** Opens the system file picker (several files); nothing is persisted. */
    data class FilePicker(
        val key: String,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val mimeTypes: List<String>,
        val onPicked: (List<android.net.Uri>) -> Unit,
        val dependency: String?,
    ) : PrefNode

    /** Opens the system folder picker and persists the chosen tree URI. */
    data class FolderPicker(
        val key: String,
        val title: String,
        val iconRes: Int,
        val persistPermission: Boolean,
        val onPicked: ((android.net.Uri) -> Unit)?,
        val dependency: String?,
    ) : PrefNode

    /**
     * Rows shown only while [visibleWhen] holds for the device's current preferences (the settings
     * DSL's `visibleWhen`). Re-evaluated whenever a value changes, like the classic screen did.
     */
    data class Conditional(
        val visibleWhen: (nodomain.freeyourgadget.gadgetbridge.util.Prefs) -> Boolean,
        val children: List<PrefNode>,
    ) : PrefNode

    /** A read-only information row (title + current value); not interactive and never persisted. */
    data class Info(
        val key: String?,
        val summary: String?,
        val title: String,
        val iconRes: Int,
    ) : PrefNode

    /** A nested, navigable screen (androidx nested {@code PreferenceScreen}). */
    data class SubScreen(
        val key: String?,
        val title: String,
        val summary: String?,
        val iconRes: Int,
        val children: List<PrefNode>,
    ) : PrefNode

    /**
     * A preference whose type/behaviour this generic renderer does not implement (complex custom
     * pickers, action-only preferences launching other activities, sortable lists, etc.). Rendered
     * as a row that hands the user off to the legacy settings screen, so nothing becomes
     * unreachable or silently broken.
     */
    data class Delegated(
        val key: String?,
        val title: String,
        val summary: String?,
        val iconRes: Int,
    ) : PrefNode
}

/**
 * Reads/writes a single device's preferences through the canonical, shared accessor and notifies
 * the running device service on every change — the same two-step the legacy fragment performs
 * (persist, then {@code GBApplication.deviceService(device).onSendConfiguration(key)}).
 */
class DeviceSettingsStore private constructor(
    private val prefs: android.content.SharedPreferences,
    private val onChanged: (String) -> Unit,
) {
    // Same file the device service reads from (GBApplication.getDeviceSpecificSharedPrefs), so a
    // value written here is immediately visible to onSendConfiguration's handler.
    constructor(device: GBDevice) : this(
        GBApplication.getDeviceSpecificSharedPrefs(device.address),
        { key -> GBApplication.deviceService(device).onSendConfiguration(key) },
    )

    companion object {
        /** A store over app-wide preferences (not tied to a device); [onChanged] runs after a write. */
        fun forApp(prefs: android.content.SharedPreferences, onChanged: (String) -> Unit = {}) =
            DeviceSettingsStore(prefs, onChanged)
    }

    /** The underlying preferences, for evaluating visibility predicates. */
    val rawPrefs: android.content.SharedPreferences get() = prefs

    fun getBoolean(key: String, def: Boolean): Boolean =
        runCatching { prefs.getBoolean(key, def) }.getOrElse {
            runCatching { prefs.getString(key, def.toString())!!.toBoolean() }.getOrDefault(def)
        }

    fun getString(key: String, def: String): String =
        runCatching { prefs.getString(key, def) ?: def }.getOrDefault(def)

    fun getInt(key: String, def: Int): Int =
        runCatching { prefs.getInt(key, def) }.getOrElse {
            runCatching { prefs.getString(key, def.toString())!!.toInt() }.getOrDefault(def)
        }

    fun getStringSet(key: String, def: Set<String>): Set<String> =
        runCatching { prefs.getStringSet(key, null) }.getOrNull() ?: def

    fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, HashSet(value)).apply()
        notify(key)
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        notify(key)
    }

    /** EditTextPreference values persist as Strings in androidx; keep that so device-side parsing is unchanged. */
    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        notify(key)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        notify(key)
    }

    private fun notify(key: String) {
        // Exact same signal the old fragment sends so the watch receives the change.
        onChanged(key)
    }
}

/**
 * Parses the preference XML resources referenced by a [DeviceSpecificSettings] into [PrefNode]s.
 */
object DeviceSettingsParser {
    private val LOG = LoggerFactory.getLogger(DeviceSettingsParser::class.java)

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val APP_NS = "http://schemas.android.com/apk/res-auto"

    /**
     * Builds the top-level node list from a coordinator-provided [DeviceSpecificSettings], mirroring
     * how the legacy fragment lays out the root screen: category headers and loose preferences
     * inline, enum-backed screens (Date & time, Notifications, …) as navigable sub-screens.
     */
    fun buildTopLevel(context: Context, settings: DeviceSpecificSettings): List<PrefNode> {
        val res = context.resources
        val out = ArrayList<PrefNode>()
        for (screenRes in settings.rootScreens) {
            val enum = DeviceSpecificSettingsScreen.fromXml(screenRes)
            if (enum != null) {
                // Enum root screens are title-only placeholders; their real content lives in the
                // sub-screen list keyed by the enum key (which also contains the placeholder itself).
                val subs = settings.getScreen(enum.key) ?: continue
                val children = ArrayList<PrefNode>()
                for (sub in subs) {
                    if (sub == enum.xml) continue
                    children.addAll(parseResource(res, sub))
                }
                if (children.isNotEmpty()) {
                    out.add(
                        PrefNode.SubScreen(
                            key = enum.key,
                            title = safeString(res, enum.title),
                            summary = summarize(children),
                            iconRes = 0,
                            children = children,
                        ),
                    )
                }
            } else {
                out.addAll(parseResource(res, screenRes))
            }
        }
        return out
    }

    /**
     * A short "what's inside" summary for a sub-screen row, from the titles of its first few
     * settings, so a nested screen (e.g. "Display") tells the user it holds raise-to-wake, rotate,
     * etc. instead of being an opaque row.
     */
    private fun summarize(children: List<PrefNode>): String? {
        val titles = ArrayList<String>()
        fun collect(nodes: List<PrefNode>) {
            for (n in nodes) {
                if (titles.size >= 3) return
                when (n) {
                    is PrefNode.Switch -> titles.add(n.title)
                    is PrefNode.SingleChoice -> titles.add(n.title)
                    is PrefNode.Text -> titles.add(n.title)
                    is PrefNode.Slider -> titles.add(n.title)
                    is PrefNode.TimePicker -> titles.add(n.title)
                    is PrefNode.Action -> titles.add(n.title)
                    is PrefNode.SubScreen -> titles.add(n.title)
                    else -> {}
                }
            }
        }
        collect(children)
        if (titles.isEmpty()) return null
        return titles.joinToString(" · ").let { if (it.length > 80) it.take(79) + "…" else it }
    }

    /** Parses one preference XML resource, returning the children of its root PreferenceScreen. */
    fun parseResource(res: Resources, xmlRes: Int): List<PrefNode> {
        var parser: XmlResourceParser? = null
        return try {
            parser = res.getXml(xmlRes)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    // This is the outermost <PreferenceScreen>; collect its children.
                    return parseContainer(parser, res)
                }
                event = parser.next()
            }
            emptyList()
        } catch (e: Exception) {
            LOG.warn("Failed to parse preference xml {}", xmlRes, e)
            emptyList()
        } finally {
            runCatching { parser?.close() }
        }
    }

    /**
     * Reads all direct children of the element the parser is currently positioned on (a START_TAG),
     * stopping at that element's matching END_TAG.
     */
    /** The `<intent>` last seen inside the element being parsed (read right after its subtree). */
    private var lastIntent: LaunchSpec? = null

    private fun parseContainer(parser: XmlResourceParser, res: Resources): List<PrefNode> {
        val out = ArrayList<PrefNode>()
        val depth = parser.depth
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && parser.depth == depth) break
            if (ev == XmlPullParser.START_TAG && parser.name == "intent") {
                lastIntent = LaunchSpec(
                    parser.getAttributeValue(ANDROID_NS, "targetClass"),
                    parser.getAttributeValue(ANDROID_NS, "action"),
                    parser.getAttributeValue(ANDROID_NS, "data"),
                )
            } else if (ev == XmlPullParser.START_TAG) {
                out.addAll(parseElement(parser, res))
            }
        }
        return out
    }

    private fun parseElement(parser: XmlResourceParser, res: Resources): List<PrefNode> {
        val simple = simpleName(parser.name)
        val key = parser.getAttributeValue(ANDROID_NS, "key")
        val title = attrText(parser, res, "title") ?: ""
        val summary = attrText(parser, res, "summary")
        val icon = parser.getAttributeResourceValue(ANDROID_NS, "icon", 0)
        val dependency = parser.getAttributeValue(ANDROID_NS, "dependency")

        // Every attribute must be read BEFORE the subtree is consumed below: once parseContainer
        // has advanced to the element's END_TAG its attributes are gone and these would all fall
        // back to their defaults (which left lists empty and rendered them as "not available").
        val entries = attrArray(parser, res, "entries")
        val entryValues = attrArray(parser, res, "entryValues")
        val defaultString = attrDefaultString(parser, res)
        val defaultBool = parser.getAttributeBooleanValue(ANDROID_NS, "defaultValue", false)
        val inputType = parser.getAttributeValue(ANDROID_NS, "inputType") ?: ""
        var min = parser.getAttributeIntValue(APP_NS, "min", Int.MIN_VALUE)
        if (min == Int.MIN_VALUE) min = parser.getAttributeIntValue(ANDROID_NS, "min", 0)
        val max = parser.getAttributeIntValue(ANDROID_NS, "max", 100)
        val defaultInt = parser.getAttributeIntValue(ANDROID_NS, "defaultValue", min)
        val defaultArray: List<String> = parser.getAttributeResourceValue(ANDROID_NS, "defaultValue", 0).let { rid ->
            if (rid == 0) emptyList() else runCatching { res.getStringArray(rid).toList() }.getOrDefault(emptyList())
        }

        // Consume (and parse) this element's subtree now; leaves yield an empty list.
        lastIntent = null
        val children = parseContainer(parser, res)
        val launch = lastIntent
        lastIntent = null

        return when {
            simple == "PreferenceScreen" ->
                listOf(PrefNode.SubScreen(key, title, summary, icon, children))

            simple == "PreferenceCategory" -> {
                val list = ArrayList<PrefNode>()
                if (title.isNotEmpty()) list.add(PrefNode.Category(title))
                list.addAll(children) // categories may hold their own preferences
                list
            }

            simple == "SwitchPreferenceCompat" || simple == "SwitchPreference" || simple == "CheckBoxPreference" -> {
                if (key == null) emptyList() else listOf(
                    PrefNode.Switch(
                        key, title, summary, icon,
                        default = defaultBool,
                        dependency = dependency,
                    ),
                )
            }

            // Simple single-choice lists. Custom list subclasses with extra chrome fall through to
            // Delegated below; only plain ListPreference is rendered generically.
            simple == "ListPreference" || simple == "SubtitleListPreference" -> {
                if (key == null) {
                    delegated(key, title, summary, icon)
                } else {
                    listOf(
                        PrefNode.SingleChoice(
                            key, title, summary, icon,
                            default = defaultString,
                            entries = entries, values = entryValues,
                            dependency = dependency,
                        ),
                    )
                }
            }

            simple == "EditTextPreference" -> {
                if (key == null) emptyList() else {
                    listOf(
                        PrefNode.Text(
                            key, title, summary, icon,
                            default = defaultString,
                            numeric = inputType.contains("number"),
                            dependency = dependency,
                        ),
                    )
                }
            }

            simple == "SeekBarPreference" -> {
                if (key == null) emptyList() else {
                    listOf(
                        PrefNode.Slider(
                            key, title, summary, icon,
                            default = defaultInt,
                            min = min, max = max,
                            dependency = dependency,
                        ),
                    )
                }
            }

            // Time-of-day picker (XTimePreference and subclasses), persisted as "HH:mm".
            simple == "XTimePreference" -> {
                if (key == null) emptyList() else listOf(
                    PrefNode.TimePicker(
                        key, title, summary, icon,
                        default = defaultString.ifEmpty { "00:00" },
                        dependency = dependency,
                    ),
                )
            }

            // Multi-select list: persisted as a string set.
            simple == "MultiSelectListPreference" -> {
                if (key == null) delegated(key, title, summary, icon)
                else listOf(PrefNode.MultiChoice(key, title, summary, icon, defaultArray.toSet(), entries, entryValues, dependency))
            }

            // Drag-to-sort list: persisted as the comma-joined value order.
            simple == "DragSortListPreference" -> {
                if (key == null) delegated(key, title, summary, icon)
                else listOf(PrefNode.Sortable(key, title, summary, icon, defaultArray, entries, entryValues, dependency))
            }

            // Plain <Preference>: a click-action row (opens an editor activity or fires a command).
            simple == "Preference" -> {
                when {
                    // A title-less row that only carries a summary is an informational note.
                    title.isEmpty() && !summary.isNullOrEmpty() && launch == null -> listOf(PrefNode.Info(key, summary, "", icon))
                    title.isEmpty() && launch == null -> emptyList()
                    else -> listOf(PrefNode.Action(key, title.ifEmpty { summary.orEmpty() }, if (title.isEmpty()) null else summary, icon, launch))
                }
            }

            // Any other custom preference class we do not model.
            else -> delegated(key, title, summary, icon)
        }
    }

    private fun delegated(key: String?, title: String, summary: String?, icon: Int): List<PrefNode> {
        if (title.isEmpty()) return emptyList()
        return listOf(PrefNode.Delegated(key, title, summary, icon))
    }

    private fun simpleName(name: String?): String {
        if (name == null) return ""
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot + 1) else name
    }

    private fun attrText(parser: XmlResourceParser, res: Resources, name: String): String? {
        val rid = parser.getAttributeResourceValue(ANDROID_NS, name, 0)
        if (rid != 0) return runCatching { res.getString(rid) }.getOrNull()
        return parser.getAttributeValue(ANDROID_NS, name)
    }

    private fun attrArray(parser: XmlResourceParser, res: Resources, name: String): List<String> {
        val rid = parser.getAttributeResourceValue(ANDROID_NS, name, 0)
        if (rid == 0) return emptyList()
        return runCatching { res.getTextArray(rid).map { it.toString() } }.getOrDefault(emptyList())
    }

    private fun attrDefaultString(parser: XmlResourceParser, res: Resources): String {
        val rid = parser.getAttributeResourceValue(ANDROID_NS, "defaultValue", 0)
        if (rid != 0) {
            runCatching { return res.getString(rid) }
        }
        return parser.getAttributeValue(ANDROID_NS, "defaultValue") ?: ""
    }

    private fun safeString(res: Resources, strRes: Int): String =
        if (strRes == 0) "" else runCatching { res.getString(strRes) }.getOrDefault("")
}
