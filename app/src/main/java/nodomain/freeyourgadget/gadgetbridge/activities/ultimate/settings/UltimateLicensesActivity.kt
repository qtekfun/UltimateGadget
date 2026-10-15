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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.licenses.License
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Native list of third-party licenses bundled with the app (replaces the classic
 * `LicensesActivity`). Reads the same `licenses/licenses.json` asset; tapping a row opens the
 * project's page.
 */
class UltimateLicensesActivity : AppCompatActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val licenses: List<License> = runCatching {
            assets.open("licenses/licenses.json").use { input ->
                val type = object : TypeToken<List<License>>() {}.type
                Gson().fromJson<List<License>>(input.reader(), type)
                    .sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
            }
        }.getOrDefault(emptyList())

        setContent {
            UltimateTheme {
                val scheme = MaterialTheme.colorScheme
                val ctx = LocalContext.current
                Scaffold(
                    containerColor = scheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.ult_about_third), fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
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
                    LazyColumn(
                        Modifier.fillMaxSize().padding(inner),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(licenses, key = { it.name + it.url }) { lic ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(scheme.surfaceContainer)
                                    .clickable {
                                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lic.url))) }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(lic.name, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                                Text(
                                    "${lic.owner} · ${lic.type}",
                                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                                )
                                Text(lic.url, style = MaterialTheme.typography.labelSmall, color = scheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, UltimateLicensesActivity::class.java)
    }
}
