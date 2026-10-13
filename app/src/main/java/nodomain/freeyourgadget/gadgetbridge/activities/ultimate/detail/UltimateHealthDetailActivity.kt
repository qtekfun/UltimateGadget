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
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf.UltimateGolf
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf.UltimateGolfActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.UltimateWorkoutActivity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout.WorkoutDetailRepository
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
                    onOpenWorkout = { row ->
                        // Resolve the device that actually recorded this workout (not just the
                        // active one), so a round from another or a disconnected watch still opens
                        // with its own GPS track.
                        val dev = deviceForSummary(this@UltimateHealthDetailActivity, row.deviceId)
                        if (row.isGolf) {
                            // Golf rounds open the scorecard; pass the device so the round's GPS
                            // track can be drawn on the course map below the card.
                            startActivity(
                                UltimateGolfActivity.intent(this@UltimateHealthDetailActivity, row.id, dev),
                            )
                        } else {
                            startActivity(UltimateWorkoutActivity.intent(this@UltimateHealthDetailActivity, row.id, dev))
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

        /**
         * The known [GBDevice] that recorded the workout with this DB device id (matched by MAC),
         * even when it is not connected, falling back to [activeDevice] when it cannot be resolved.
         */
        fun deviceForSummary(context: Context, deviceId: Long): GBDevice? {
            if (deviceId <= 0) return activeDevice(context)
            val mac = runCatching {
                GBApplication.acquireDB().use { db -> db.daoSession.deviceDao.load(deviceId)?.identifier }
            }.getOrNull()
            val dm = (context.applicationContext as GBApplication).deviceManager
            return dm.devices.firstOrNull { it.address.equals(mac, ignoreCase = true) } ?: activeDevice(context)
        }

        fun loadRecentWorkouts(context: Context, limit: Int): List<WorkoutRow> {
            // Gadgetbridge applies its language override to the application context, not to
            // these plain AppCompatActivity screens, so resolve localized labels against it.
            val localizedContext = GBApplication.getContext() ?: context.applicationContext
            return runCatching {
                GBApplication.acquireDB().use { db ->
                    val session = db.daoSession
                    // Every workout ever synced, from every device — these rows live in the DB for
                    // good, independent of whether a watch is currently connected. Newest first.
                    val all = session.baseActivitySummaryDao.queryBuilder()
                        .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                        .list()
                    // Merge across devices: keep a workout only when it does not overlap in time
                    // with one already kept, so the same session synced from two watches (or a
                    // phone + a watch) is not listed twice. Non-overlapping workouts from any
                    // device are all kept.
                    val kept = ArrayList<nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary>()
                    for (s in all) {
                        val start = s.startTime?.time ?: continue
                        val end = s.endTime?.time ?: start
                        val overlaps = kept.any { k ->
                            val ks = k.startTime?.time ?: return@any false
                            val ke = k.endTime?.time ?: ks
                            start < ke && ks < end
                        }
                        if (!overlaps) kept += s
                        if (kept.size >= limit) break
                    }
                    kept
                        .map { s ->
                            val start = s.startTime
                            val end = s.endTime
                            val dur = if (start != null && end != null)
                                ((end.time - start.time) / 1000).coerceAtLeast(0) else 0L
                            // Resolve the ActivityKind so the type name is shown in the app's
                            // language (via the localized Gadgetbridge string) with its icon.
                            // Use the application context: the Ultimate Compose activities do not
                            // extend AbstractGBActivity, so only the application context carries
                            // the language override Gadgetbridge applies at startup.
                            val rawKind = runCatching {
                                ActivityKind.fromCode(s.activityKind ?: ActivityKind.UNKNOWN.code)
                            }.getOrDefault(ActivityKind.UNKNOWN)
                            // Golf reaches us with a workout type upstream never mapped, so it would
                            // otherwise show as "unknown". Detect it and resolve it to GOLF so the
                            // list row and the type filter chip get the right name and icon.
                            val isGolf = UltimateGolf.isGolf(
                                rawKind.code, UltimateGolf.rawHuaweiType(s), s.name,
                            )
                            val kind = if (isGolf) ActivityKind.GOLF else rawKind
                            val typeLabel = runCatching { kind.getLabel(localizedContext) }.getOrNull().orEmpty()
                            WorkoutRow(
                                id = s.id ?: -1L,
                                title = s.name?.takeIf { it.isNotBlank() }
                                    ?: typeLabel.ifEmpty { "Workout" },
                                activityKindCode = kind.code,
                                typeLabel = typeLabel,
                                iconRes = kind.getIcon(),
                                whenLabel = start?.let { FMT.format(it) } ?: "",
                                durationSeconds = dur,
                                line = WorkoutDetailRepository.listLine(s, kind),
                                isGolf = isGolf,
                                deviceId = s.deviceId ?: -1L,
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
    /** Device-provided name, falling back to the localized type label. */
    val title: String,
    /** ActivityKind code, used to group/filter workouts by type. */
    val activityKindCode: Int,
    /** Localized activity type name (via ActivityKind.getLabel). */
    val typeLabel: String,
    /** Drawable resource for the type icon (via ActivityKind.getIcon). */
    val iconRes: Int,
    val whenLabel: String,
    val durationSeconds: Long,
    /** Sport-aware summary: distance first for distance sports, else duration, kcal and heart rate. */
    val line: String = "",
    /** True when this workout is a golf round (opens the golf scorecard instead of the workout screen). */
    val isGolf: Boolean = false,
    /** DB id of the device that recorded this workout, to resolve its track/device when opened. */
    val deviceId: Long = -1L,
)
