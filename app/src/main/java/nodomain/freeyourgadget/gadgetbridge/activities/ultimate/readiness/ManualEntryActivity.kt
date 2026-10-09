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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.readiness

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme

/** Manual sleep/workout entry for today, so readiness works without the watch. */
class ManualEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ManualLogStore(this)
        setContent {
            UltimateTheme {
                ManualEntryScreen(
                    initial = store.get(),
                    onBack = { finish() },
                    onSave = {
                        store.set(it)
                        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualEntryScreen(
    initial: ManualLogStore.ManualDay,
    onBack: () -> Unit,
    onSave: (ManualLogStore.ManualDay) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var sleep by remember { mutableStateOf(initial.sleepHours?.toString() ?: "") }
    var quality by remember { mutableStateOf(initial.sleepQuality?.toString() ?: "") }
    var minutes by remember { mutableStateOf(initial.workoutMinutes?.toString() ?: "") }
    var rpe by remember { mutableStateOf(initial.workoutRpe?.toString() ?: "") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Registro manual", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.background, titleContentColor = scheme.onSurface,
                    navigationIconContentColor = scheme.onSurface,
                ),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Sueño de anoche", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            OutlinedTextField(
                value = sleep, onValueChange = { sleep = it },
                label = { Text("Horas (p.ej. 7.5)") }, singleLine = true,
                keyboardOptions = num, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = quality, onValueChange = { quality = it },
                label = { Text("Calidad 0-100 (opcional)") }, singleLine = true,
                keyboardOptions = num, modifier = Modifier.fillMaxWidth(),
            )
            Text("Entreno de hoy", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            OutlinedTextField(
                value = minutes, onValueChange = { minutes = it },
                label = { Text("Minutos") }, singleLine = true,
                keyboardOptions = num, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rpe, onValueChange = { rpe = it },
                label = { Text("Intensidad percibida 1-10 (RPE)") }, singleLine = true,
                keyboardOptions = num, modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onSave(
                        ManualLogStore.ManualDay(
                            sleepHours = sleep.toDoubleOrNull(),
                            sleepQuality = quality.toIntOrNull()?.coerceIn(0, 100),
                            workoutMinutes = minutes.toIntOrNull()?.coerceAtLeast(0),
                            workoutRpe = rpe.toIntOrNull()?.coerceIn(1, 10),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Guardar") }
        }
    }
}
