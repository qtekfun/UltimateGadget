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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.clock

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.model.Alarm as AlarmModel

/** Shared scaffold + helpers for the Ultimate clock screens (alarms, reminders, world clocks). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClockScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                    actionIconContentColor = palette.onSurface,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) { content() }
    }
}

/** Tappable field row showing a label and its current value; opens a picker on click. */
@Composable
internal fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
        }
    }
}

/** Weekday bit masks in Monday-first order, with short Spanish labels. */
internal val WEEKDAY_BITS = intArrayOf(
    AlarmModel.ALARM_MON.toInt(),
    AlarmModel.ALARM_TUE.toInt(),
    AlarmModel.ALARM_WED.toInt(),
    AlarmModel.ALARM_THU.toInt(),
    AlarmModel.ALARM_FRI.toInt(),
    AlarmModel.ALARM_SAT.toInt(),
    AlarmModel.ALARM_SUN.toInt(),
)
internal val WEEKDAY_LABELS = arrayOf("L", "M", "X", "J", "V", "S", "D")

/** Human-readable repetition summary from an alarm repetition bit mask. */
internal fun repetitionSummary(mask: Int): String {
    if (mask == 0) return "Una vez"
    if (mask and AlarmModel.ALARM_DAILY.toInt() == AlarmModel.ALARM_DAILY.toInt()) return "Todos los días"
    val days = WEEKDAY_BITS.indices.filter { (mask and WEEKDAY_BITS[it]) != 0 }.map { WEEKDAY_LABELS[it] }
    return days.joinToString(" ")
}

/** Formats an hour/minute pair respecting the phone's 12/24h setting. */
internal fun formatTime(context: Context, hour: Int, minute: Int): String {
    return if (DateFormat.is24HourFormat(context)) {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", hour, minute)
    } else {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val suffix = if (hour < 12) "AM" else "PM"
        String.format(java.util.Locale.getDefault(), "%d:%02d %s", h12, minute, suffix)
    }
}
