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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.contacts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.Contact
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.UUID

/**
 * Native editor for the contacts synced to a watch (the quick-dial / caller-name list): list,
 * add, edit and delete, within the device's contact slot limit. Replaces the classic
 * `ConfigureContacts` + `ContactDetails` pair; storage and the device push are unchanged
 * ([DBHelper] contact table, `onSetContacts`).
 */
class UltimateContactsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (device == null) {
            finish()
            return
        }
        setContent { UltimateTheme { ContactsScreen(device = device, onBack = { finish() }) } }
    }

    companion object {
        @JvmStatic
        fun intent(context: Context, device: GBDevice): Intent =
            Intent(context, UltimateContactsActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
    }
}

private fun loadContacts(device: GBDevice): List<Contact> =
    runCatching { DBHelper.getContacts(device) }.getOrDefault(emptyList())

private fun pushToDevice(device: GBDevice) {
    if (device.isInitialized) {
        GBApplication.deviceService(device).onSetContacts(ArrayList(loadContacts(device)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsScreen(device: GBDevice, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var contacts by remember { mutableStateOf(loadContacts(device)) }
    var editing by remember { mutableStateOf<Contact?>(null) }
    var deleting by remember { mutableStateOf<Contact?>(null) }
    val slots = runCatching { device.deviceCoordinator.getContactsSlotCount(device) }.getOrDefault(0)

    fun refresh() { contacts = loadContacts(device) }

    Scaffold(
        containerColor = scheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ult_contacts), fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (contacts.size >= slots) {
                    Toast.makeText(ctx, ctx.getString(R.string.contact_no_free_slots_description, slots.toString()), Toast.LENGTH_LONG).show()
                } else {
                    runCatching {
                        GBApplication.acquireDB().use { db ->
                            val session = db.daoSession
                            val dbDevice = DBHelper.getDevice(device, session)
                            val user = DBHelper.getUser(session)
                            editing = Contact().apply {
                                name = ""
                                number = ""
                                deviceId = dbDevice.id!!
                                userId = user.id!!
                                contactId = UUID.randomUUID().toString()
                            }
                        }
                    }
                }
            }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.ult_add_contact)) }
        },
    ) { inner ->
        if (contacts.isEmpty()) {
            Text(
                stringResource(R.string.ult_contacts_empty),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(inner).padding(16.dp),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(inner),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(contacts, key = { it.contactId ?: it.hashCode().toString() }) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceContainer)
                            .clickable { editing = c }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                            Text(c.number.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleting = c }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ult_delete), tint = scheme.error)
                        }
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var name by remember(c) { mutableStateOf(c.name.orEmpty()) }
        var number by remember(c) { mutableStateOf(c.number.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.ult_contact)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(stringResource(R.string.ult_name)) },
                    )
                    OutlinedTextField(
                        value = number, onValueChange = { number = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        label = { Text(stringResource(R.string.ult_phone_number)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        name.isBlank() -> Toast.makeText(ctx, ctx.getString(R.string.contact_missing_name), Toast.LENGTH_LONG).show()
                        number.isBlank() -> Toast.makeText(ctx, ctx.getString(R.string.contact_missing_number), Toast.LENGTH_LONG).show()
                        else -> {
                            c.name = name.trim()
                            c.number = number.trim()
                            DBHelper.store(c)
                            editing = null
                            refresh()
                            pushToDevice(device)
                        }
                    }
                }) { Text(stringResource(R.string.ult_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.ult_cancel)) } },
        )
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.ult_delete_contact)) },
            text = { Text(stringResource(R.string.ult_delete_contact_confirm, c.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    DBHelper.delete(c)
                    deleting = null
                    refresh()
                    pushToDevice(device)
                }) { Text(stringResource(R.string.ult_delete), color = scheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.ult_cancel)) } },
        )
    }
}
