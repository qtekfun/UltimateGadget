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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardCardId
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DashboardData
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.dashboard.DbHealthRepository
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.UltimateWorkoutMapActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.text.SimpleDateFormat
import java.util.Locale

/** Expanded view for one dashboard metric, opened from a dashboard card. */
class UltimateHealthDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val card = runCatching { DashboardCardId.valueOf(intent.getStringExtra(EXTRA_CARD) ?: "") }
            .getOrNull() ?: DashboardCardId.STEPS

        setContent {
            UltimateTheme {
                var data by remember { mutableStateOf<DashboardData?>(null) }
                var workouts by remember { mutableStateOf<List<WorkoutRow>>(emptyList()) }
                var loading by remember { mutableStateOf(true) }

                androidx.compose.runtime.LaunchedEffect(card) {
                    val d = withContext(Dispatchers.IO) { DbHealthRepository(application).load() }
                    val w = if (card == DashboardCardId.LAST_WORKOUT)
                        withContext(Dispatchers.IO) { loadRecentWorkouts(this@UltimateHealthDetailActivity, 20) }
                    else emptyList()
                    data = d; workouts = w; loading = false
                }

                HealthDetailScreen(
                    card = card,
                    data = data,
                    workouts = workouts,
                    loading = loading,
                    onBack = { finish() },
                    onOpenWorkout = { id ->
                        activeDevice(this@UltimateHealthDetailActivity)?.let { dev ->
                            startActivity(UltimateWorkoutMapActivity.intent(this@UltimateHealthDetailActivity, id, dev))
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_CARD = "ug_card"

        fun intent(context: Context, card: DashboardCardId): Intent =
            Intent(context, UltimateHealthDetailActivity::class.java).putExtra(EXTRA_CARD, card.name)

        /** Recent workouts for the active device, newest first. */
        /** The device whose data the dashboard shows: the initialized one, else the first paired. */
        fun activeDevice(context: Context): GBDevice? {
            val dm = (context.applicationContext as GBApplication).deviceManager
            return dm.devices.firstOrNull { it.isInitialized } ?: dm.devices.firstOrNull()
        }

        fun loadRecentWorkouts(context: Context, limit: Int): List<WorkoutRow> {
            val device = activeDevice(context) ?: return emptyList()
            return runCatching {
                GBApplication.acquireDB().use { db ->
                    val session = db.daoSession
                    val dbDevice = DBHelper.findDevice(device, session) ?: return emptyList()
                    session.baseActivitySummaryDao.queryBuilder()
                        .where(BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id))
                        .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                        .limit(limit)
                        .list()
                        .map { s ->
                            val start = s.startTime
                            val end = s.endTime
                            val dur = if (start != null && end != null)
                                ((end.time - start.time) / 1000).coerceAtLeast(0) else 0L
                            val kind = s.activityKind?.let { k ->
                                runCatching { ActivityKind.fromCode(k).getLabel(context) }.getOrNull()
                            }
                            WorkoutRow(
                                id = s.id ?: -1L,
                                title = s.name?.takeIf { it.isNotBlank() } ?: kind ?: "Workout",
                                type = kind ?: "",
                                whenLabel = start?.let { FMT.format(it) } ?: "",
                                durationSeconds = dur,
                            )
                        }
                }
            }.getOrDefault(emptyList())
        }

        private val FMT = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
    }
}

data class WorkoutRow(
    val id: Long,
    val title: String,
    val type: String,
    val whenLabel: String,
    val durationSeconds: Long,
)
