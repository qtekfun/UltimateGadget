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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.reports

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/** Weekly / monthly health reports with charts, read from the local Gadgetbridge database. */
class UltimateReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                val vm: UltimateReportsViewModel = viewModel()
                val state = vm.state.collectAsStateWithLifecycle().value
                ReportsScreen(
                    state = state,
                    onSetPeriod = vm::setPeriod,
                    onBack = { finish() },
                    onOpenGoals = { startActivity(UltimateGoalsActivity.intent(this)) },
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, UltimateReportsActivity::class.java)
    }
}
