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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import nodomain.freeyourgadget.gadgetbridge.util.calendar.CalendarManager

/**
 * Native calendar picker: choose which phone calendars (and which event colours inside them) are
 * synced to the device. Replaces the classic `CalendarSelectionActivity`; it uses the same
 * [CalendarManager] blacklist storage and fires the same configuration events.
 */
class UltimateCalendarSelectionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        val manager = if (granted) CalendarManager(this, device.address) else null
        val calendars = manager?.calendars.orEmpty()
        setContent {
            UltimateTheme {
                CalendarSelectionScreen(
                    permissionGranted = granted,
                    calendars = calendars,
                    manager = manager,
                    onChanged = { key -> GBApplication.deviceService(device).onSendConfiguration(key) },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateCalendarSelectionActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarSelectionScreen(
    permissionGranted: Boolean,
    calendars: List<CalendarManager.CalendarEntry>,
    manager: CalendarManager?,
    onChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Blacklist state lives in the manager's preferences; bump to recompose after a write.
    var version by remember { mutableIntStateOf(0) }
    var colorsFor by remember { mutableStateOf<CalendarManager.CalendarEntry?>(null) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ult_calendars), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ult_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background,
                    titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        when {
            !permissionGranted || manager == null -> Text(
                stringResource(R.string.ult_calendar_no_permission),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(inner).padding(16.dp),
            )
            calendars.isEmpty() -> Text(
                stringResource(R.string.ult_calendar_none),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(inner).padding(16.dp),
            )
            else -> {
                @Suppress("UNUSED_EXPRESSION") version
                LazyColumn(
                    Modifier.fillMaxSize().padding(inner),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(calendars, key = { it.getUniqueString() }) { cal ->
                        val enabled = !manager.isCalendarBlacklisted(cal.getUniqueString())
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(scheme.surfaceContainer)
                                .clickable {
                                    if (enabled) manager.addCalendarToBlacklist(cal.getUniqueString())
                                    else manager.removeFromCalendarBlacklist(cal.getUniqueString())
                                    version++
                                    onChanged(GBPrefs.CALENDAR_BLACKLIST)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier.size(18.dp).clip(CircleShape)
                                    .background(Color(0xFF000000.toInt() or cal.color()).copy(alpha = if (enabled) 1f else 0.25f)),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    cal.displayName(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                                )
                                Text(
                                    cal.accountName(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            if (enabled && cal.eventColors().isNotEmpty()) {
                                IconButton(onClick = { colorsFor = cal }) {
                                    Icon(
                                        Icons.Filled.Palette,
                                        contentDescription = stringResource(R.string.pref_title_calendar_sync_colors),
                                        tint = scheme.primary,
                                    )
                                }
                            }
                            Checkbox(checked = enabled, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }

    val cal = colorsFor
    if (cal != null && manager != null) {
        AlertDialog(
            onDismissRequest = { colorsFor = null },
            title = { Text(stringResource(R.string.pref_title_calendar_sync_colors)) },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cal.eventColors().sorted().forEach { color ->
                        @Suppress("UNUSED_EXPRESSION") version
                        val on = !manager.isColorBlacklistedForCalendar(cal.getUniqueString(), color)
                        Box(
                            Modifier.size(44.dp).clip(CircleShape)
                                .background(Color(0xFF000000.toInt() or color).copy(alpha = if (on) 1f else 0.25f))
                                .clickable {
                                    if (on) manager.addColorToBlacklistForCalendar(cal.getUniqueString(), color)
                                    else manager.removeColorFromBlacklistForCalendar(cal.getUniqueString(), color)
                                    version++
                                    onChanged(DeviceSettingsPreferenceConst.PREF_CALENDAR_SYNC_COLOR_BLACKLIST)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { colorsFor = null }) { Text(stringResource(R.string.done)) } },
        )
    }
}
