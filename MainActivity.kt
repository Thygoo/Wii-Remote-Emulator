package org.example.wiiremote

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/** Android-only Bluetooth HID diagnostic utility, not a real Nintendo SYNC implementation. */
class MainActivity : Activity() {
    private lateinit var session: WiiHidSession
    private lateinit var logs: TextView
    private lateinit var syncStatus: TextView
    private lateinit var diagnostics: TextView
    private lateinit var devicesSpinner: Spinner
    private val pressed = mutableSetOf<Int>()
    private var paired = listOf<BluetoothDevice>()
    private var syncRequested = false
    private var pendingPermissionAction: (() -> Unit)? = null

    companion object {
        private const val PERMISSIONS_REQUEST = 42
        private const val ENABLE_BLUETOOTH_REQUEST = 43
        private const val DISCOVERABILITY_REQUEST = 44
        private const val DISCOVERABILITY_SECONDS = 120
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                    addLog("Bluetooth bond-state event: $state (12=paired, 11=pairing, 10=not paired)")
                    refreshPairedDevices()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED ->
                    addLog("Bluetooth ACL link connected (not proof of HID connection)")
                BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    addLog("Bluetooth ACL link disconnected")
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED ->
                    addLog("Bluetooth scan/discoverability mode changed")
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    addLog("Bluetooth power state changed")
            }
            updateDiagnostics()
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = WiiHidSession(
            this,
            { message -> runOnUiThread { addLog(message) } },
            { registered -> runOnUiThread { onRegistrationChanged(registered) } },
            { connection -> runOnUiThread { setStatus("HID host: $connection"); updateDiagnostics() } }
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(24))
        }
        val screen = ScrollView(this).apply { fillViewport = true; addView(root) }
        setContentView(screen)

        root.addView(TextView(this).apply {
            text = "Wii Remote HID Lab v0.3 — Windows HID Diagnostics"
            textSize = 19f
        })
        root.addView(TextView(this).apply {
            text = "Pairing with Windows is not the same as connecting a Bluetooth HID host. " +
                "This prototype does not implement Nintendo's legacy Wii Remote pairing."
            setPadding(0, dp(6), 0, dp(8))
        })

        root.addView(Button(this).apply {
            text = "SYNC — experimental Wii U discovery"
            setOnClickListener { onSyncTapped() }
        })
        syncStatus = TextView(this).apply { text = "Status: Ready"; textSize = 15f }
        root.addView(syncStatus)
        root.addView(Button(this).apply {
            text = "Register / recover HID device"
            setOnClickListener { withPermissions { session.start(); updateDiagnostics() } }
        })
        root.addView(Button(this).apply {
            text = "Make phone discoverable (120 sec)"
            setOnClickListener { withPermissions { requestDiscoverability() } }
        })

        root.addView(TextView(this).apply { text = "PAIRED DEVICES — choose your Windows PC"; textSize = 16f })
        devicesSpinner = Spinner(this)
        root.addView(devicesSpinner)
        root.addView(Button(this).apply {
            text = "Refresh paired devices"
            setOnClickListener { withPermissions { refreshPairedDevices() } }
        })
        root.addView(Button(this).apply {
            text = "CONNECT HID TO SELECTED PAIRED PC"
            setOnClickListener { withPermissions { connectSelected() } }
        })
        root.addView(Button(this).apply {
            text = "Disconnect HID host"
            setOnClickListener { withPermissions { session.disconnectHost() } }
        })
        root.addView(Button(this).apply {
            text = "STOP HID"
            setOnClickListener {
                syncRequested = false
                session.stop()
                setStatus("Stopped")
            }
        })
        root.addView(TextView(this).apply { text = "BLUETOOTH DIAGNOSTICS"; textSize = 16f })
        diagnostics = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        root.addView(diagnostics)
        root.addView(Button(this).apply {
            text = "Refresh diagnostics"
            setOnClickListener { withPermissions { updateDiagnostics(); refreshPairedDevices() } }
        })

        root.addView(TextView(this).apply {
            text = "Touch buttons (report IDs 0x30/0x31; only sent if HID host CONNECTED)"
            setPadding(0, dp(8), 0, 0)
        })
        val rows = listOf(
            listOf("UP" to WiiReportCodec.UP, "HOME" to WiiReportCodec.HOME, "A" to WiiReportCodec.A),
            listOf("LEFT" to WiiReportCodec.LEFT, "DOWN" to WiiReportCodec.DOWN, "RIGHT" to WiiReportCodec.RIGHT),
            listOf("B" to WiiReportCodec.B, "1" to WiiReportCodec.ONE, "2" to WiiReportCodec.TWO),
            listOf("-" to WiiReportCodec.MINUS, "+" to WiiReportCodec.PLUS)
        )
        for (row in rows) {
            val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((caption, bit) in row) {
                val btn = Button(this).apply {
                    text = caption
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> { pressed.add(bit); updateButtons(); true }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                pressed.remove(bit); updateButtons(); true
                            }
                            else -> false
                        }
                    }
                }
                container.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
            root.addView(container)
        }
        root.addView(TextView(this).apply { text = "SESSION LOG"; textSize = 16f })
        logs = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        root.addView(logs)
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else registerReceiver(bluetoothReceiver, filter)
        addLog("Ready. Pair PC with phone using Windows Bluetooth settings, then refresh paired devices.")
        withPermissions { refreshPairedDevices(); updateDiagnostics() }
    }

    private fun connectSelected() {
        val index = devicesSpinner.selectedItemPosition - 1
        if (index !in paired.indices) {
            addLog("Select an already-paired Windows PC first")
            return
        }
        session.connect(paired[index])
        updateDiagnostics()
    }

    private fun refreshPairedDevices() {
        val oldSelected = devicesSpinner.selectedItemPosition - 1
        val oldAddress = paired.getOrNull(oldSelected)?.address
        paired = session.pairedDevices()
        val labels = listOf("Select a paired computer...") + paired.map { session.deviceLabel(it) + " (${it.address.takeLast(5)})" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        devicesSpinner.adapter = adapter
        val keep = paired.indexOfFirst { it.address == oldAddress }
        devicesSpinner.setSelection(if (keep >= 0) keep + 1 else 0)
        addLog("Paired Bluetooth devices: ${paired.size}")
    }

    private fun onSyncTapped() {
        syncRequested = true
        setStatus("Starting experimental SYNC...")
        withPermissions { continueSync() }
    }

    private fun continueSync() {
        if (!syncRequested) return
        if (!session.isBluetoothEnabled()) {
            setStatus("Waiting for Bluetooth to be enabled")
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH_REQUEST)
            return
        }
        if (session.isRegistered()) {
            syncRequested = false
            requestDiscoverability()
            return
        }
        setStatus("Waiting for HID registration...")
        session.start()
    }

    private fun onRegistrationChanged(registered: Boolean) {
        setStatus(if (registered) "HID registered; select Windows PC and tap CONNECT" else "HID unregistered")
        updateDiagnostics()
        if (registered && syncRequested) {
            syncRequested = false
            requestDiscoverability()
        }
    }

    private fun requestDiscoverability() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABILITY_SECONDS)
        startActivityForResult(intent, DISCOVERABILITY_REQUEST)
    }

    @Deprecated("Minimal Android 9+ activity result interface")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST -> {
                if (resultCode == RESULT_OK && session.isBluetoothEnabled()) continueSync()
                else { syncRequested = false; setStatus("Bluetooth enable cancelled") }
            }
            DISCOVERABILITY_REQUEST -> {
                if (resultCode > 0) {
                    addLog("Android discoverable for $resultCode seconds. HID host connection NOT yet confirmed.")
                    setStatus("Discoverable for $resultCode seconds; not yet connected")
                } else {
                    addLog("Bluetooth discoverability declined")
                    setStatus("Discoverability declined")
                }
            }
        }
        updateDiagnostics()
    }

    private fun withPermissions(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN)
            val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) {
                pendingPermissionAction = action
                requestPermissions(missing.toTypedArray(), PERMISSIONS_REQUEST)
                return
            }
        }
        action()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST) return
        val pending = pendingPermissionAction
        pendingPermissionAction = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            addLog("Nearby Devices permissions granted")
            pending?.invoke()
        } else addLog("Bluetooth permission denied; enable Nearby Devices in app settings")
    }

    private fun updateButtons() { session.setButtons(pressed.fold(0) { acc, bit -> acc or bit }) }

    private fun setStatus(message: String) {
        if (::syncStatus.isInitialized) syncStatus.text = "Status: $message"
    }

    private fun updateDiagnostics() {
        if (::diagnostics.isInitialized) diagnostics.text = session.diagnostics()
    }

    private fun addLog(line: String) {
        if (!::logs.isInitialized) return
        logs.text = (logs.text.toString() + "\n" + line).takeLast(12000)
        updateDiagnostics()
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) session.setForeground(true)
    }
    override fun onPause() {
        if (::session.isInitialized) session.setForeground(false)
        super.onPause()
    }
    override fun onDestroy() {
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) { }
        if (::session.isInitialized) session.stop()
        super.onDestroy()
    }
}
