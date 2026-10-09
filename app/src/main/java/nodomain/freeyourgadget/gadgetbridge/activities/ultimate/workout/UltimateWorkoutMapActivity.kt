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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.workout

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutGpsFragment
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Shows a single workout's recorded GPS track on a map, reusing Gadgetbridge's existing
 * [WorkoutGpsFragment] (mapsforge + MapsManager). Opened from the workouts list / Last Workout card.
 */
class UltimateWorkoutMapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ultimate_workout_map)

        findViewById<MaterialToolbar>(R.id.ug_workout_toolbar).setNavigationOnClickListener { finish() }

        val summaryId = intent.getLongExtra(EXTRA_SUMMARY_ID, -1L)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        val summary: BaseActivitySummary? = runCatching {
            GBApplication.acquireDB().use { it.daoSession.baseActivitySummaryDao.load(summaryId) }
        }.getOrNull()

        if (summary == null || device == null) {
            findViewById<TextView>(R.id.ug_workout_stats).text = getString(R.string.ug_workout_no_route)
            return
        }

        val kind = summary.activityKind?.let { k ->
            runCatching { ActivityKind.fromCode(k).getLabel(this) }.getOrNull()
        }
        findViewById<MaterialToolbar>(R.id.ug_workout_toolbar).title =
            summary.name?.takeIf { it.isNotBlank() } ?: kind ?: getString(R.string.ug_workout_map_title)
        findViewById<TextView>(R.id.ug_workout_stats).text = buildStats(summary, kind)

        val fragment = WorkoutGpsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.ug_workout_map_container, fragment)
            .commitNow()
        runCatching { fragment.setTrackData(summary, device) }
    }

    private fun buildStats(s: BaseActivitySummary, kind: String?): String {
        val parts = mutableListOf<String>()
        kind?.let { parts += it }
        val start = s.startTime
        val end = s.endTime
        if (start != null && end != null) {
            val dur = ((end.time - start.time) / 1000).coerceAtLeast(0)
            parts += String.format(Locale.getDefault(), "%d:%02d:%02d", dur / 3600, (dur % 3600) / 60, dur % 60)
        }
        start?.let { parts += FMT.format(it) }
        return parts.joinToString(" · ")
    }

    companion object {
        const val EXTRA_SUMMARY_ID = "ug_summary_id"
        private val FMT = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())

        fun intent(context: Context, summaryId: Long, device: GBDevice): Intent =
            Intent(context, UltimateWorkoutMapActivity::class.java)
                .putExtra(EXTRA_SUMMARY_ID, summaryId)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}
