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
class DeviceSettingsStore(private val device: GBDevice) {
    // Same file the device service reads from (GBApplication.getDeviceSpecificSharedPrefs), so a
    // value written here is immediately visible to onSendConfiguration's handler.
    private val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)

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
        GBApplication.deviceService(device).onSendConfiguration(key)
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
                            summary = null,
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
    private fun parseContainer(parser: XmlResourceParser, res: Resources): List<PrefNode> {
        val out = ArrayList<PrefNode>()
        val depth = parser.depth
        while (true) {
            val ev = parser.next()
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.END_TAG && parser.depth == depth) break
            if (ev == XmlPullParser.START_TAG) {
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

        // Consume (and parse) this element's subtree now; leaves yield an empty list.
        val children = parseContainer(parser, res)

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
                        default = parser.getAttributeBooleanValue(ANDROID_NS, "defaultValue", false),
                        dependency = dependency,
                    ),
                )
            }

            // Simple single-choice lists. Custom list subclasses with extra chrome fall through to
            // Delegated below; only plain ListPreference is rendered generically.
            simple == "ListPreference" -> {
                val entries = attrArray(parser, res, "entries")
                val values = attrArray(parser, res, "entryValues")
                if (key == null || entries.isEmpty() || entries.size != values.size) {
                    delegated(key, title, summary, icon)
                } else {
                    listOf(
                        PrefNode.SingleChoice(
                            key, title, summary, icon,
                            default = attrDefaultString(parser, res),
                            entries = entries, values = values,
                            dependency = dependency,
                        ),
                    )
                }
            }

            simple == "EditTextPreference" -> {
                if (key == null) emptyList() else {
                    val inputType = parser.getAttributeValue(ANDROID_NS, "inputType") ?: ""
                    listOf(
                        PrefNode.Text(
                            key, title, summary, icon,
                            default = attrDefaultString(parser, res),
                            numeric = inputType.contains("number"),
                            dependency = dependency,
                        ),
                    )
                }
            }

            simple == "SeekBarPreference" -> {
                if (key == null) emptyList() else {
                    var min = parser.getAttributeIntValue(APP_NS, "min", Int.MIN_VALUE)
                    if (min == Int.MIN_VALUE) min = parser.getAttributeIntValue(ANDROID_NS, "min", 0)
                    val max = parser.getAttributeIntValue(ANDROID_NS, "max", 100)
                    listOf(
                        PrefNode.Slider(
                            key, title, summary, icon,
                            default = parser.getAttributeIntValue(ANDROID_NS, "defaultValue", min),
                            min = min, max = max,
                            dependency = dependency,
                        ),
                    )
                }
            }

            // Plain <Preference> or any custom preference class: not something we persist generically.
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
