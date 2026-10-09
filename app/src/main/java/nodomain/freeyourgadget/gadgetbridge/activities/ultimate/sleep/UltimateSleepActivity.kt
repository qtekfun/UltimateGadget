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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.sleep

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Advanced sleep: day selector, phase hypnogram, score (device-native or local heuristic), nocturnal
 * SpO2 and naps — all read locally from the Gadgetbridge database, no network. Optionally opens on a
 * specific day via [EXTRA_DAY_MILLIS].
 */
class UltimateSleepActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialDay = intent.getLongExtra(EXTRA_DAY_MILLIS, 0L)
        setContent {
            UltimateTheme {
                val vm: SleepViewModel = viewModel()
                if (initialDay > 0L) {
                    LaunchedEffect(initialDay) { vm.setDay(initialDay) }
                }
                val state = vm.state.collectAsStateWithLifecycle().value
                SleepScreen(
                    state = state,
                    onBack = { finish() },
                    onPreviousDay = vm::previousDay,
                    onNextDay = vm::nextDay,
                )
            }
        }
    }

    companion object {
        const val EXTRA_DAY_MILLIS = "ug_sleep_day_millis"

        fun intent(context: Context): Intent =
            Intent(context, UltimateSleepActivity::class.java)

        /** Opens the screen on the day that contains [dayMillis]. */
        fun intent(context: Context, dayMillis: Long): Intent =
            intent(context).putExtra(EXTRA_DAY_MILLIS, dayMillis)
    }
}
