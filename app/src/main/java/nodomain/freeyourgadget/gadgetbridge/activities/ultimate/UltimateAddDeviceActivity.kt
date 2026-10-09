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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.ui.AddDeviceScreen

/**
 * Branded "add device" entry for the new UI. Requests the Bluetooth/location runtime permissions and
 * then hands off to Gadgetbridge's existing discovery/pairing flow ([DiscoveryActivityV2]), which does
 * the actual BLE/classic scan and bonding. Reusing it keeps pairing on the tested code path.
 */
class UltimateAddDeviceActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            permissionsGrantedState = hasAllPermissions()
            if (granted) {
                launchDiscovery()
            } else {
                Toast.makeText(this, "Permisos necesarios para buscar dispositivos", Toast.LENGTH_SHORT).show()
            }
        }

    private var permissionsGrantedState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                var granted by remember { mutableStateOf(hasAllPermissions()) }
                AddDeviceScreen(
                    permissionsGranted = granted,
                    onRequestPermissions = {
                        permissionLauncher.launch(requiredPermissions())
                        granted = hasAllPermissions()
                    },
                    onStartDiscovery = { launchDiscovery() },
                )
                permissionsGrantedState = granted
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionsGrantedState = hasAllPermissions()
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return perms.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchDiscovery() {
        startActivity(nodomain.freeyourgadget.gadgetbridge.activities.ultimate.pairing.UltimateDiscoveryActivity.intent(this))
        finish()
    }
}
