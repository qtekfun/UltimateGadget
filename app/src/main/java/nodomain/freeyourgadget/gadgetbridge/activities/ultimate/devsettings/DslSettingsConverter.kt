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
import android.text.InputType
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ActionSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.CategorySetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.InfoSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DateSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.FilePickerSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.FolderPickerSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.MultiSelectSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.SortableListSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ScreenSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.SeekBarSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.SwitchSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.TextSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.TimeSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.XmlScreenSetting
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * Turns a coordinator's programmatic [DeviceSettingsSpec] (the settings DSL used by rings, earbuds,
 * scales, …) into the same [PrefNode] tree the native settings screen already renders for XML-based
 * devices, so those devices no longer have to open the classic preference screen.
 *
 * Only presentation is converted: values are still read and written through
 * [DeviceSettingsStore] (the device's own shared preferences) and every change still fires
 * `onSendConfiguration(key)`, exactly as the classic fragment does. Visibility predicates are
 * evaluated once, when the screen is built.
 */
object DslSettingsConverter {

    /** Click handlers of [ActionSetting]s, keyed by preference key, collected while converting. */
    class Result(val nodes: List<PrefNode>, val actions: Map<String, (Context, GBDevice?) -> Boolean>)

    fun convert(context: Context, device: GBDevice, spec: DeviceSettingsSpec): Result {
        val prefs = Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.address))
        val actions = mutableMapOf<String, (Context, GBDevice?) -> Boolean>()
        val nodes = convertAll(context, prefs, spec.items, actions, device)
        return Result(nodes, actions)
    }

    private fun convertAll(
        ctx: Context,
        prefs: Prefs,
        items: List<DeviceSetting>,
        actions: MutableMap<String, (Context, GBDevice?) -> Boolean>,
        device: GBDevice,
    ): List<PrefNode> {
        val out = mutableListOf<PrefNode>()
        for (item in items) {
            val visible = runCatching { item.visibleWhen?.invoke(prefs) ?: true }.getOrDefault(true)
            if (!visible) continue
            out += convertOne(ctx, prefs, item, actions, device)
        }
        return out
    }

    private fun entryLabel(ctx: Context, e: ListEntry): String = when (e) {
        is ListEntry.Res -> str(ctx, e.label).orEmpty()
        is ListEntry.Text -> e.label
    }

    private fun str(ctx: Context, @androidx.annotation.StringRes id: Int): String? =
        if (id == 0) null else runCatching { ctx.getString(id) }.getOrNull()

    private fun convertOne(
        ctx: Context,
        prefs: Prefs,
        item: DeviceSetting,
        actions: MutableMap<String, (Context, GBDevice?) -> Boolean>,
        device: GBDevice,
    ): List<PrefNode> = when (item) {
        is ScreenSetting -> {
            val children = convertAll(ctx, prefs, item.children, actions, device).toMutableList()
            item.xmlSubScreens.forEach { children += DeviceSettingsParser.parseResource(ctx.resources, it) }
            listOf(
                PrefNode.SubScreen(
                    key = item.key,
                    title = str(ctx, item.title).orEmpty(),
                    summary = str(ctx, item.summary),
                    iconRes = item.icon,
                    children = children,
                ),
            )
        }

        is XmlScreenSetting -> listOf(
            PrefNode.SubScreen(
                key = item.screen.key,
                title = str(ctx, item.screen.title).orEmpty(),
                summary = null,
                iconRes = 0,
                children = item.subScreens.flatMap { DeviceSettingsParser.parseResource(ctx.resources, it) },
            ),
        )

        is CategorySetting -> {
            val title = item.titleText ?: str(ctx, item.title).orEmpty()
            listOf<PrefNode>(PrefNode.Category(title)) + convertAll(ctx, prefs, item.children, actions, device)
        }

        is SwitchSetting -> listOf(
            PrefNode.Switch(
                key = item.key,
                title = str(ctx, item.title).orEmpty(),
                summary = str(ctx, item.summary) ?: str(ctx, item.summaryOn),
                iconRes = item.icon,
                default = item.defaultValue,
                dependency = item.dependency,
            ),
        )

        is ListSetting -> {
            val entries: List<ListEntry> = when {
                item.entries.isNotEmpty() -> item.entries
                item.entriesProvider != null -> runCatching { item.entriesProvider.invoke(prefs) }.getOrDefault(emptyList())
                else -> emptyList()
            }
            val labels: List<String>
            val values: List<String>
            if (entries.isNotEmpty()) {
                labels = entries.map {
                    when (it) {
                        is ListEntry.Res -> str(ctx, it.label).orEmpty()
                        is ListEntry.Text -> it.label
                    }
                }
                values = entries.map { it.value }
            } else if (item.entriesRes != 0 && item.entryValuesRes != 0) {
                labels = ctx.resources.getStringArray(item.entriesRes).toList()
                values = ctx.resources.getStringArray(item.entryValuesRes).toList()
            } else {
                labels = emptyList(); values = emptyList()
            }
            listOf(
                PrefNode.SingleChoice(
                    key = item.key,
                    title = str(ctx, item.title).orEmpty(),
                    summary = str(ctx, item.summary),
                    iconRes = item.icon,
                    default = item.defaultValue,
                    entries = labels,
                    values = values,
                    dependency = item.dependency,
                ),
            )
        }

        is SeekBarSetting -> listOf(
            PrefNode.Slider(
                key = item.key,
                title = str(ctx, item.title).orEmpty(),
                summary = str(ctx, item.summary),
                iconRes = item.icon,
                default = item.defaultValue,
                min = item.min,
                max = item.max,
                dependency = item.dependency,
            ),
        )

        is TextSetting -> listOf(
            PrefNode.Text(
                key = item.key,
                title = str(ctx, item.title).orEmpty(),
                summary = str(ctx, item.summary),
                iconRes = item.icon,
                default = item.defaultValue,
                numeric = (item.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER,
                dependency = item.dependency,
            ),
        )

        is TimeSetting -> listOf(
            PrefNode.TimePicker(
                key = item.key,
                title = str(ctx, item.title).orEmpty(),
                summary = str(ctx, item.summary),
                iconRes = item.icon,
                default = item.defaultValue,
                dependency = item.dependency,
            ),
        )

        // Read-only information row: shown as a non-interactive action row (no command is sent for
        // a key that has no registered handler and is flagged as informational).
        is InfoSetting -> {
            val title = item.titleText ?: str(ctx, item.title).orEmpty()
            val summary = runCatching { item.summaryProvider?.invoke(ctx, prefs)?.toString() }.getOrNull()
                ?: str(ctx, item.summary)
            listOf(PrefNode.Info(title = title, summary = summary, iconRes = item.icon))
        }

        is ActionSetting -> {
            val title = item.titleText ?: str(ctx, item.title).orEmpty()
            val summary = runCatching { item.summaryProvider?.invoke(ctx, prefs)?.toString() }.getOrNull()
                ?: str(ctx, item.summary)
            item.onClick?.let { actions[item.key] = it }
            listOf(PrefNode.Action(key = item.key, title = title, summary = summary, iconRes = item.icon))
        }

        is MultiSelectSetting -> {
            val entries = when {
                item.entries.isNotEmpty() -> item.entries
                item.entriesProvider != null -> runCatching { item.entriesProvider.invoke(prefs) }.getOrDefault(emptyList())
                else -> emptyList()
            }
            listOf(
                PrefNode.MultiChoice(
                    key = item.key, title = str(ctx, item.title).orEmpty(), summary = str(ctx, item.summary),
                    iconRes = item.icon, default = item.defaultValue,
                    entries = entries.map { entryLabel(ctx, it) }, values = entries.map { it.value },
                    dependency = item.dependency,
                ),
            )
        }

        is SortableListSetting -> listOf(
            PrefNode.Sortable(
                key = item.key, title = str(ctx, item.title).orEmpty(), summary = str(ctx, item.summary),
                iconRes = item.icon, default = item.defaultValue,
                entries = item.entries.map { entryLabel(ctx, it) }, values = item.entries.map { it.value },
                dependency = item.dependency,
            ),
        )

        is DateSetting -> listOf(
            PrefNode.DatePicker(
                key = item.key, title = str(ctx, item.title).orEmpty(), summary = str(ctx, item.summary),
                iconRes = item.icon, default = item.defaultValue, minDate = item.minDate, maxDate = item.maxDate,
                dependency = item.dependency,
            ),
        )

        is FilePickerSetting -> listOf(
            PrefNode.FilePicker(
                key = item.key, title = str(ctx, item.title).orEmpty(), summary = str(ctx, item.summary),
                iconRes = item.icon, mimeTypes = item.mimeTypes,
                onPicked = { uris -> item.onPicked(ctx, device, uris) }, dependency = item.dependency,
            ),
        )

        is FolderPickerSetting -> listOf(
            PrefNode.FolderPicker(
                key = item.key, title = str(ctx, item.title).orEmpty(), iconRes = item.icon,
                persistPermission = item.persistUriPermission,
                onPicked = item.onPicked?.let { cb -> { uri: android.net.Uri -> cb(ctx, device, uri) } },
                dependency = item.dependency,
            ),
        )

        // Anything else (a node type added upstream later) stays reachable through the hand-off row.
        // keep them reachable through the (rare) hand-off row instead of dropping them silently.
        else -> listOf(
            PrefNode.Delegated(
                key = item.key,
                title = when (item) {
                    is MultiSelectSetting -> str(ctx, item.title)
                    is SortableListSetting -> str(ctx, item.title)
                    is DateSetting -> str(ctx, item.title)
                    is FilePickerSetting -> str(ctx, item.title)
                    is FolderPickerSetting -> str(ctx, item.title)
                    else -> null
                } ?: item.key,
                summary = null,
                iconRes = 0,
            ),
        )
    }
}
