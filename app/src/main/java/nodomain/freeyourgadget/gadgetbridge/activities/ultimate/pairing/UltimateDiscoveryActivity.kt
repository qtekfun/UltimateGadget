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
package nodomain.freeyourgadget.gadgetbridge.activities.ultimate.pairing

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AuthKeyActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryPairingPreferenceActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.GBScanEvent
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.GBScanEventProcessor
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.LocalUltimatePalette
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.SectionLabelStyle
import nodomain.freeyourgadget.gadgetbridge.activities.ultimate.theme.UltimateTheme
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.service.btle.BleNamesResolver
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.BondingInterface
import nodomain.freeyourgadget.gadgetbridge.util.BondingUtil
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory

/**
 * Branded (Compose) device discovery + pairing launcher for the UltimateGadget UI.
 *
 * This screen is a drop-in replacement for the look & feel of the legacy
 * [nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryActivityV2]: it performs the
 * same BLE + classic Bluetooth scan, builds the same [GBDeviceCandidate] list via the shared
 * [GBScanEventProcessor], and starts pairing through the exact same tested code path
 * ([DeviceCoordinator.getPairingActivity] for brand-specific pairing activities, otherwise the
 * [BondingUtil] bonding flow). None of the BLE/BT protocol nor the per-brand pairing activities are
 * reimplemented here — only the discovery UI and the hand-off into pairing.
 */
class UltimateDiscoveryActivity : AppCompatActivity(), BondingInterface, GBScanEventProcessor.Callback {

    private val handler = Handler(Looper.getMainLooper())

    private var adapter: BluetoothAdapter? = null
    private var scanning = false
    private var deviceTarget: GBDeviceCandidate? = null

    private val bleScanCallback = BleScanCallback()
    private val bluetoothReceiver = BluetoothReceiver()
    private val deviceFoundProcessor = GBScanEventProcessor(this)

    /** Backing list used to pass EXTRA_DEVICE_ALL_CANDIDATES and to resolve candidates by MAC. */
    private val deviceCandidates = ArrayList<GBDeviceCandidate>()

    /** Compose-observable UI state. */
    private val uiDevices = mutableStateListOf<DiscoveredDevice>()
    private val isScanning = mutableStateOf(false)
    private val hasPermissions = mutableStateOf(false)

    private val stopRunnable = Runnable {
        stopDiscovery()
        LOG.info("Discovery stopped by thread timeout.")
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            hasPermissions.value = hasAllPermissions()
            if (result.values.all { it }) {
                startDiscovery()
            } else {
                GB.toast(this, getString(R.string.permission_granting_mandatory), Toast.LENGTH_LONG, GB.ERROR)
            }
        }

    private val authKeyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val candidate =
                    data?.getParcelableExtra<GBDeviceCandidate>(AuthKeyActivity.EXTRA_DEVICE_CANDIDATE_RESULT)
                if (candidate != null) {
                    val deviceType = DeviceHelper.getInstance().resolveDeviceType(candidate)
                    startPair(candidate, deviceType.deviceCoordinator)
                } else {
                    GB.toast(this, "Auth data is null", Toast.LENGTH_LONG, GB.ERROR)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()
        hasPermissions.value = hasAllPermissions()

        setContent {
            UltimateTheme {
                DiscoveryScreen(
                    devices = uiDevices,
                    scanning = isScanning.value,
                    permissionsGranted = hasPermissions.value,
                    onBack = { finish() },
                    onToggleScan = { toggleDiscovery() },
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
                    onDeviceClick = { onCandidateClicked(it) },
                    onOpenPairingPrefs = {
                        startActivity(Intent(this, DiscoveryPairingPreferenceActivity::class.java))
                    },
                )
            }
        }

        if (hasAllPermissions()) {
            startDiscovery()
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
        hasPermissions.value = hasAllPermissions()
        registerBroadcastReceivers()
    }

    override fun onPause() {
        unregisterBroadcastReceivers()
        stopDiscovery()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterBroadcastReceivers()
        stopDiscovery()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHILD_RESULT && resultCode == RESULT_OK) {
            // A brand pairing activity signalled that discovery should close.
            finish()
        } else {
            BondingUtil.handleActivityResult(this, requestCode, resultCode, data)
        }
    }

    // region permissions

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        return perms.toTypedArray()
    }

    private fun hasAllPermissions(): Boolean = requiredPermissions().all {
        androidx.core.content.ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    // endregion

    // region scanning (reused logic, mirrors DiscoveryActivityV2)

    private fun toggleDiscovery() {
        if (scanning) stopDiscovery() else startDiscovery()
    }

    private fun startDiscovery() {
        if (scanning) {
            LOG.warn("Not starting discovery, because already scanning.")
            return
        }
        if (!hasAllPermissions()) {
            permissionLauncher.launch(requiredPermissions())
            return
        }

        LOG.info("Starting discovery")
        DeviceHelper.getInstance().clearForcedDeviceTypes()
        deviceFoundProcessor.clear()
        deviceFoundProcessor.start()
        refreshDeviceList()

        // Pre-add currently connected bonded devices, as those will not trigger discovery events.
        try {
            val bonded = BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
            for (device in bonded) {
                try {
                    val isConnectedMethod = device.javaClass.getMethod("isConnected")
                    val isConnected = isConnectedMethod.invoke(device) as? Boolean
                    if (isConnected == true) {
                        deviceFoundProcessor.scheduleProcessing(GBScanEvent(device, (-1).toShort(), device.uuids, null))
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to check whether {} is connected", device.address)
                }
            }
        } catch (e: SecurityException) {
            LOG.error("Failed to pre-add paired devices", e)
        }

        try {
            if (!ensureBluetoothReady()) {
                GB.toast(this, getString(R.string.discovery_enable_bluetooth), Toast.LENGTH_SHORT, GB.ERROR)
                return
            }
            if (GB.supportsBluetoothLE()) {
                startBTLEDiscovery()
            }
            startBTDiscovery()
        } catch (e: SecurityException) {
            LOG.error("SecurityException on startDiscovery")
            deviceFoundProcessor.stop()
            return
        }

        setScanning(true)
    }

    private fun stopDiscovery() {
        LOG.info("Stopping discovery")
        try {
            stopBTDiscovery()
            stopBLEDiscovery()
        } catch (e: SecurityException) {
            LOG.error("SecurityException on stopDiscovery")
        }
        setScanning(false)
        deviceFoundProcessor.stop()
        handler.removeCallbacks(stopRunnable)
        refreshDeviceList()
    }

    private fun setScanning(value: Boolean) {
        scanning = value
        isScanning.value = value
    }

    private fun startBTLEDiscovery() {
        LOG.info("Starting BLE discovery")
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, SCAN_DURATION)
        adapter?.bluetoothLeScanner?.startScan(null, getScanSettings(), bleScanCallback)
    }

    private fun stopBLEDiscovery() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        try {
            scanner.stopScan(bleScanCallback)
        } catch (e: NullPointerException) {
            LOG.warn("Internal NullPointerException when stopping the scan!")
        }
    }

    private fun startBTDiscovery() {
        LOG.info("Starting BT discovery")
        try {
            stopBTDiscovery()
        } catch (ignored: Exception) {
        }
        handler.removeCallbacks(stopRunnable)
        handler.postDelayed(stopRunnable, SCAN_DURATION)
        if (adapter?.startDiscovery() == true) {
            LOG.debug("Discovery started successfully")
        } else {
            LOG.error("Discovery starting failed")
        }
    }

    private fun stopBTDiscovery() {
        adapter?.cancelDiscovery()
    }

    private fun ensureBluetoothReady(): Boolean {
        if (!checkBluetoothAvailable()) return false
        adapter?.cancelDiscovery()
        return true
    }

    private fun checkBluetoothAvailable(): Boolean {
        val bluetoothService = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        if (bluetoothService == null) {
            adapter = null
            return false
        }
        val btAdapter = bluetoothService.adapter
        if (btAdapter == null) {
            adapter = null
            return false
        }
        if (!btAdapter.isEnabled) {
            LOG.warn("Bluetooth not enabled")
            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            adapter = null
            return false
        }
        adapter = btAdapter
        return true
    }

    private fun refreshDeviceList() {
        handler.post {
            deviceCandidates.clear()
            deviceCandidates.addAll(deviceFoundProcessor.devices)
            uiDevices.clear()
            uiDevices.addAll(deviceCandidates.map { it.toDiscoveredDevice() })
        }
    }

    override fun onDeviceChanged() {
        refreshDeviceList()
    }

    // endregion

    // region pairing hand-off (reuses DiscoveryActivityV2's exact path)

    private fun onCandidateClicked(device: DiscoveredDevice) {
        DeviceHelper.getInstance().clearForcedDeviceTypes()
        preparePair(device.candidate)
    }

    private fun preparePair(deviceCandidate: GBDeviceCandidate) {
        val deviceType = DeviceHelper.getInstance().resolveDeviceType(deviceCandidate)
        if (!deviceType.isSupported) {
            LOG.warn("Unsupported device candidate {}", deviceCandidate)
            copyDetailsToClipboard(deviceCandidate)
            return
        }

        stopDiscovery()

        val coordinator = deviceType.deviceCoordinator
        LOG.info("Using device candidate {} with coordinator {}", deviceCandidate, coordinator.javaClass)

        if (coordinator.suggestUnbindBeforePair() && deviceCandidate.isBonded) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unbind_before_pair_title)
                .setMessage(R.string.unbind_before_pair_message)
                .setIcon(R.drawable.ic_warning_gray)
                .setPositiveButton(R.string.ok) { _, _ -> checkAuthKeyAndPair(deviceCandidate, coordinator) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            checkAuthKeyAndPair(deviceCandidate, coordinator)
        }
    }

    private fun checkAuthKeyAndPair(deviceCandidate: GBDeviceCandidate, coordinator: DeviceCoordinator) {
        if (coordinator.requiresAuthKey()) {
            authKeyLauncher.launch(AuthKeyActivity.newIntent(this, deviceCandidate))
        } else {
            startPair(deviceCandidate, coordinator)
        }
    }

    private fun startPair(deviceCandidate: GBDeviceCandidate, coordinator: DeviceCoordinator) {
        val pairingActivity = coordinator.pairingActivity
        if (pairingActivity != null) {
            val intent = Intent(this, pairingActivity)
            intent.putExtra(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE, deviceCandidate)
            intent.putParcelableArrayListExtra(DeviceCoordinator.EXTRA_DEVICE_ALL_CANDIDATES, deviceCandidates)
            startActivityForResult(intent, CHILD_RESULT)
        } else {
            if (coordinator.bondingStyle == DeviceCoordinator.BONDING_STYLE_NONE ||
                coordinator.bondingStyle == DeviceCoordinator.BONDING_STYLE_LAZY
            ) {
                LOG.info("No bonding needed, according to coordinator, so connecting right away")
                BondingUtil.connectThenComplete(this, deviceCandidate)
                return
            }
            try {
                deviceTarget = deviceCandidate
                BondingUtil.initiateCorrectBonding(this, deviceCandidate, coordinator)
            } catch (e: Exception) {
                LOG.error("Error pairing device {}", deviceCandidate.macAddress, e)
            }
        }
    }

    private fun copyDetailsToClipboard(deviceCandidate: GBDeviceCandidate) {
        val details = mutableListOf(deviceCandidate.name, deviceCandidate.macAddress)
        try {
            for (uuid in deviceCandidate.serviceUuids) details.add(uuid.uuid.toString())
        } catch (e: Exception) {
            LOG.error("Error collecting device uuids", e)
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(deviceCandidate.name, details.joinToString(", ")))
        GB.toast(this, "Device details copied to clipboard", Toast.LENGTH_SHORT, GB.INFO)
    }

    // endregion

    // region BondingInterface

    override fun onBondingComplete(success: Boolean) {
        finish()
    }

    override fun getCurrentTarget(): GBDeviceCandidate? = deviceTarget

    override fun getAttemptToConnect(): Boolean = true

    override fun registerBroadcastReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, bluetoothReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun unregisterBroadcastReceivers() {
        AndroidUtils.safeUnregisterBroadcastReceiver(this, bluetoothReceiver)
    }

    override fun getContext(): Context = this

    // endregion

    private fun loadSettings() {
        deviceFoundProcessor.setDiscoverUnsupported(
            GBApplication.getPrefs().getBoolean("discover_unsupported_devices", false),
        )
    }

    private fun bluetoothStateChanged(newState: Int) {
        adapter = if (newState == BluetoothAdapter.STATE_ON) {
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        } else {
            null
        }
    }

    private fun getCandidateFromMAC(device: BluetoothDevice): GBDeviceCandidate? =
        deviceCandidates.firstOrNull { it.macAddress == device.address }

    private fun GBDeviceCandidate.toDiscoveredDevice(): DiscoveredDevice {
        val deviceType = DeviceHelper.getInstance().resolveDeviceType(this)
        val coordinator = deviceType.deviceCoordinator
        return DiscoveredDevice(
            candidate = this,
            name = name,
            typeName = getString(coordinator.deviceNameResource),
            macAddress = macAddress,
            iconRes = coordinator.defaultIconResource,
            rssi = rssi,
            bonded = isBonded,
            supported = deviceType.isSupported,
            experimental = coordinator.isExperimental,
        )
    }

    private inner class BluetoothReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    bluetoothStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF))

                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, GBDevice.RSSI_UNKNOWN)
                    deviceFoundProcessor.scheduleProcessing(GBScanEvent(device, rssi, device.uuids, null))
                }

                BluetoothDevice.ACTION_UUID -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, GBDevice.RSSI_UNKNOWN)
                    val rawUuids: Array<Parcelable>? = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                    val uuids = AndroidUtils.toParcelUuids(rawUuids)
                    deviceFoundProcessor.scheduleProcessing(GBScanEvent(device, rssi, uuids, null))
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val macAddress = device.address
                    val targetMacAddress = macAddress
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val prevBondState =
                        intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                    LOG.info(
                        "Bond state changed for {} from {} to {}, target address {}",
                        macAddress,
                        BleNamesResolver.getBondStateString(prevBondState),
                        BleNamesResolver.getBondStateString(bondState),
                        getMacAddress(),
                    )
                    val expected = getMacAddress()
                    if (expected == null || !expected.equals(targetMacAddress, ignoreCase = true)) {
                        LOG.debug("ignore due to MAC address: got {} but expected {}", macAddress, expected)
                    } else if (bondState != BluetoothDevice.BOND_BONDED) {
                        LOG.debug("ignore due bonding state: {}", BleNamesResolver.getBondStateString(bondState))
                    } else {
                        BondingUtil.handleDeviceBonded(this@UltimateDiscoveryActivity, getCandidateFromMAC(device))
                    }
                }
            }
        }
    }

    private inner class BleScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            try {
                val scanRecord = result.scanRecord ?: return
                val serviceUuids = scanRecord.serviceUuids
                val uuids = serviceUuids?.toTypedArray()
                val device = result.device
                val rssi = result.rssi.toShort()
                deviceFoundProcessor.scheduleProcessing(
                    GBScanEvent(device, rssi, uuids, scanRecord.manufacturerSpecificData),
                )
            } catch (e: Exception) {
                LOG.warn("Error handling BLE scan result", e)
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(UltimateDiscoveryActivity::class.java)

        private const val CHILD_RESULT = 0x826983
        private const val SCAN_DURATION = 30_000L

        fun intent(context: Context): Intent = Intent(context, UltimateDiscoveryActivity::class.java)

        private fun getScanSettings(): ScanSettings {
            val builder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
            }
            return builder.build()
        }
    }
}

/** Display model for a discovered candidate, pre-resolved off the Compose recomposition path. */
data class DiscoveredDevice(
    val candidate: GBDeviceCandidate,
    val name: String,
    val typeName: String,
    val macAddress: String,
    val iconRes: Int,
    val rssi: Short,
    val bonded: Boolean,
    val supported: Boolean,
    val experimental: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryScreen(
    devices: List<DiscoveredDevice>,
    scanning: Boolean,
    permissionsGranted: Boolean,
    onBack: () -> Unit,
    onToggleScan: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDeviceClick: (DiscoveredDevice) -> Unit,
    onOpenPairingPrefs: () -> Unit,
) {
    val palette = LocalUltimatePalette.current
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = { Text("Añadir dispositivo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPairingPrefs) {
                        Icon(Icons.Filled.Tune, contentDescription = "Ajustes de emparejamiento")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.surface,
                    titleContentColor = palette.onSurface,
                    navigationIconContentColor = palette.onSurface,
                    actionIconContentColor = palette.onSurface,
                ),
            )
        },
        floatingActionButton = {
            if (permissionsGranted) {
                ExtendedFloatingActionButton(
                    onClick = onToggleScan,
                    containerColor = palette.primary,
                    contentColor = palette.onPrimary,
                ) {
                    Icon(
                        if (scanning) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (scanning) "Detener" else "Buscar", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
        ) {
            if (!permissionsGranted) {
                PermissionPrompt(onRequestPermissions)
                return@Column
            }

            ScanStatusRow(scanning = scanning, count = devices.size)

            if (devices.isEmpty()) {
                EmptyState(scanning)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp, top = 4.dp),
                ) {
                    items(devices, key = { it.macAddress }) { device ->
                        DeviceCandidateCard(device = device, onClick = { onDeviceClick(device) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStatusRow(scanning: Boolean, count: Int) {
    val palette = LocalUltimatePalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (scanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = palette.secondary,
            )
        }
        Text(
            if (scanning) "BUSCANDO DISPOSITIVOS" else "BÚSQUEDA DETENIDA",
            style = SectionLabelStyle,
            color = palette.secondary,
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = palette.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeviceCandidateCard(device: DiscoveredDevice, onClick: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = CircleShape, color = palette.surfaceHigh, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(device.iconRes),
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.onSurface,
                )
                Text(
                    device.typeName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onSurfaceVariant,
                )
                Text(
                    device.macAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
                val badges = buildList {
                    if (device.bonded) add("Emparejado")
                    if (!device.supported) add("No compatible")
                    if (device.experimental) add("Experimental")
                }
                if (badges.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        badges.forEach { Badge(it) }
                    }
                }
            }
            if (device.rssi > GBDevice.RSSI_UNKNOWN) {
                Text(
                    GB.formatRssi(device.rssi),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    val palette = LocalUltimatePalette.current
    Surface(shape = RoundedCornerShape(8.dp), color = palette.surfaceHighest) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = palette.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(scanning: Boolean) {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.BluetoothSearching,
            contentDescription = null,
            tint = palette.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (scanning) "Buscando dispositivos cercanos…" else "No se encontraron dispositivos",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pon el dispositivo en modo de emparejamiento y mantenlo cerca.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionPrompt(onRequestPermissions: () -> Unit) {
    val palette = LocalUltimatePalette.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = CircleShape, color = palette.primaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Bluetooth,
                    contentDescription = null,
                    tint = palette.onPrimaryContainer,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Permiso necesario",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = palette.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Necesitamos permiso de Bluetooth (y ubicación en versiones antiguas de Android) para buscar dispositivos.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        ExtendedFloatingActionButton(
            onClick = onRequestPermissions,
            containerColor = palette.primary,
            contentColor = palette.onPrimary,
            modifier = Modifier.height(52.dp),
        ) {
            Text("Conceder permisos", fontWeight = FontWeight.Bold)
        }
    }
}
