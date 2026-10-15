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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.golf

import nodomain.freeyourgadget.gadgetbridge.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.GolfCity
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.GolfCountry
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.GolfCourseInfo
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.InAppNetworkImpl
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/**
 * Browses the Huawei golf course catalog (country -> city -> course) and downloads the picked
 * course map. Only reachable in builds that can use the network. It does not talk to the watch:
 * it returns the downloaded .bin to [UltimateGolfCoursesActivity], which already sends it.
 */
class UltimateGolfCatalogActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                CatalogScreen(
                    onBack = { finish() },
                    onPicked = { dl ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent()
                                .putExtra(EXTRA_COURSE_ID, dl.courseId)
                                .putExtra(EXTRA_VERSION, dl.version)
                                .putExtra(EXTRA_NAME, dl.friendlyName)
                                .putExtra(EXTRA_PATH, dl.file.absolutePath),
                        )
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_COURSE_ID = "courseId"
        const val EXTRA_VERSION = "version"
        const val EXTRA_NAME = "name"
        const val EXTRA_PATH = "path"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogScreen(
    onBack: () -> Unit,
    onPicked: (nodomain.freeyourgadget.gadgetbridge.activities.ultimate.net.GolfCourseDownload) -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalUltimatePalette.current
    val scope = rememberCoroutineScope()
    var countries by remember { mutableStateOf<List<GolfCountry>>(emptyList()) }
    var cities by remember { mutableStateOf<List<GolfCity>>(emptyList()) }
    var courses by remember { mutableStateOf<List<GolfCourseInfo>>(emptyList()) }
    var country by remember { mutableStateOf<GolfCountry?>(null) }
    var city by remember { mutableStateOf<GolfCity?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Search: filters the current level and, inside a country, looks for courses across all of its
    // cities (their course lists are fetched once and cached for the life of the screen).
    var query by remember { mutableStateOf("") }
    val courseCache = remember { mutableStateMapOf<String, List<GolfCourseInfo>>() }
    var searching by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<Pair<GolfCity, GolfCourseInfo>>>(emptyList()) }
    val q = remember(query) { normalizeForSearch(query.trim()) }

    LaunchedEffect(country, city, q, cities) {
        val c = country
        if (c == null || city != null || q.length < 2 || cities.isEmpty()) {
            matches = emptyList(); searching = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(350) // debounce typing
        searching = true
        for (ci in cities) {
            if (ci.id !in courseCache) {
                val list = runCatching { withContext(Dispatchers.IO) { InAppNetworkImpl.golfCourses(c, ci) } }.getOrNull()
                if (list != null) courseCache[ci.id] = list
            }
            matches = cities.flatMap { cc ->
                courseCache[cc.id].orEmpty().filter { normalizeForSearch(it.name).contains(q) }.map { cc to it }
            }
        }
        searching = false
    }

    fun <T> load(block: suspend () -> T, ok: (T) -> Unit) {
        loading = true; error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess(ok)
                .onFailure { error = it.message ?: context.getString(R.string.ult_network_error) }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load({ InAppNetworkImpl.golfCountries() }) { countries = it } }

    // Back steps up one level before leaving the screen.
    val goBack = {
        when {
            city != null -> { city = null; courses = emptyList(); query = "" }
            country != null -> { country = null; cities = emptyList(); query = ""; courseCache.clear(); matches = emptyList() }
            else -> onBack()
        }
    }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text(city?.name ?: country?.name ?: stringResource(R.string.ult_golf_courses)) },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.ult_clear))
                        }
                    }
                },
                placeholder = {
                    Text(
                        stringResource(
                            when {
                                city != null -> R.string.ult_golf_search_courses
                                country != null -> R.string.ult_golf_search_country
                                else -> R.string.ult_golf_search_countries
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (loading || searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = palette.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    country == null -> {
                        val shown = countries.filter { q.isEmpty() || normalizeForSearch(it.name).contains(q) }
                        items(shown, key = { it.id }) { c ->
                            CatalogRow(c.name, null) {
                                country = c
                                query = ""
                                load({ InAppNetworkImpl.golfCities(c) }) { cities = it }
                            }
                        }
                        if (shown.isEmpty() && !loading) item { NoResults() }
                    }
                    city == null -> {
                        if (matches.isNotEmpty()) {
                            item { SearchHeader(stringResource(R.string.ult_golf_courses)) }
                            items(matches, key = { "m-" + it.second.id }) { (ci, co) ->
                                CatalogRow(co.name, "${ci.name} · ${co.totalLength} m") {
                                    if (!loading) load({ InAppNetworkImpl.downloadGolfCourse(context, country!!, co) }) { onPicked(it) }
                                }
                            }
                            item { SearchHeader(stringResource(R.string.ult_golf_cities)) }
                        }
                        val shown = cities.filter { q.isEmpty() || normalizeForSearch(it.name).contains(q) }
                        items(shown, key = { it.id }) { ci ->
                            CatalogRow(ci.name, ci.province.ifBlank { null }) {
                                city = ci
                                query = ""
                                load({ InAppNetworkImpl.golfCourses(country!!, ci) }) { courses = it }
                            }
                        }
                        if (shown.isEmpty() && matches.isEmpty() && !loading && !searching) item { NoResults() }
                    }
                    else -> {
                        val shown = courses.filter { q.isEmpty() || normalizeForSearch(it.name).contains(q) }
                        items(shown, key = { it.id }) { co ->
                            CatalogRow(co.name, "${co.totalLength} m") {
                                if (!loading) load({ InAppNetworkImpl.downloadGolfCourse(context, country!!, co) }) { onPicked(it) }
                            }
                        }
                        if (shown.isEmpty() && !loading) item { NoResults() }
                    }
                }
            }
        }
    }
}

/** Lower-cases and strips accents so "Málaga" matches "malaga". */
private fun normalizeForSearch(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@Composable
private fun SearchHeader(text: String) {
    val palette = LocalUltimatePalette.current
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = palette.primary)
}

@Composable
private fun NoResults() {
    val palette = LocalUltimatePalette.current
    Text(stringResource(R.string.ult_no_results), color = palette.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CatalogRow(title: String, subtitle: String?, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.onSurfaceVariant)
            }
        }
    }
}
