package org.example.wiiremote

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Touch UI and public-API Bluetooth pairing probe; not a genuine Wii Remote SYNC implementation. */
class MainActivity : Activity() {
    private lateinit var session: WiiHidSession
    private lateinit var logs: TextView
    private lateinit var syncStatus: TextView
    private val pressed: MutableSet<Int> = mutableSetOf()
    private var syncRequested = false

    companion object {
        private const val PERMISSIONS_REQUEST = 42
        private const val ENABLE_BLUETOOTH_REQUEST = 43
        private const val DISCOVERABILITY_REQUEST = 44
        private const val DISCOVERABILITY_SECONDS = 120
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = WiiHidSession(
            this,
            { message -> runOnUiThread { addLog(message) } },
            { isRegistered -> runOnUiThread { onHidRegistrationChanged(isRegistered) } }
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        root.addView(TextView(this).apply {
            text = "Wii Remote HID Lab v0.2 — Wii U pairing unverified"
            textSize = 17f
        })
        root.addView(TextView(this).apply {
            text = "Experimental SYNC registers an Android HID device and asks Android to be visible. " +
                "It cannot reproduce every part of a real Wii Remote's Bluetooth pairing."
        })
        root.addView(Button(this).apply {
            text = "SYNC — attempt pairing with Wii U"
            setOnClickListener { onSyncTapped() }
        })
        syncStatus = TextView(this).apply { text = "Status: Ready to attempt sync" }
        root.addView(syncStatus)
        root.addView(Button(this).apply {
            text = "Register HID Device (diagnostic)"
            setOnClickListener { if (ensurePermissions()) session.start() }
        })
        root.addView(Button(this).apply {
            text = "Make phone discoverable (120 sec)"
            setOnClickListener { if (ensurePermissions()) requestDiscoverability() }
        })
        root.addView(Button(this).apply {
            text = "Stop HID"
            setOnClickListener {
                syncRequested = false
                session.stop()
                setStatus("Stopped")
            }
        })
        root.addView(TextView(this).apply {
            text = "Touch buttons below. Reports transmit only after an HID host connects."
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
        root.addView(TextView(this).apply { text = "Session log (newest at bottom)" })
        logs = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { addView(logs) }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        addLog("Ready. On Wii U, press console SYNC until the Wii Remote pairing screen is shown.")
    }

    private fun onSyncTapped() {
        syncRequested = true
        setStatus("Starting experimental SYNC...")
        addLog("SYNC pressed: request permissions, Bluetooth power, HID registration, then discoverability")
        if (ensurePermissions()) continueSync()
    }

    private fun continueSync() {
        if (!syncRequested) return
        if (!session.isBluetoothEnabled()) {
            setStatus("Waiting for Bluetooth to be enabled")
            addLog("Requesting Bluetooth enable permission from Android")
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

    private fun onHidRegistrationChanged(isRegistered: Boolean) {
        if (isRegistered) {
            setStatus("HID registered; waiting for Wii U connection")
            if (syncRequested) {
                syncRequested = false
                requestDiscoverability()
            }
        } else if (syncRequested) {
            syncRequested = false
            setStatus("HID registration failed; see log")
            addLog("SYNC could not continue: HID profile was not registered")
        }
    }

    private fun requestDiscoverability() {
        setStatus("Requesting 120 seconds of Bluetooth visibility...")
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABILITY_SECONDS)
        startActivityForResult(intent, DISCOVERABILITY_REQUEST)
    }

    @Deprecated("Using Activity result for a minimal API-28-compatible prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST -> {
                if (resultCode == RESULT_OK && session.isBluetoothEnabled()) {
                    addLog("Bluetooth enabled")
                    continueSync()
                } else {
                    syncRequested = false
                    setStatus("Bluetooth enable cancelled")
                    addLog("SYNC cancelled: Bluetooth was not enabled")
                }
            }
            DISCOVERABILITY_REQUEST -> {
                if (resultCode > 0) {
                    setStatus("Visible for $resultCode seconds; Wii U connection not confirmed")
                    addLog("Android discoverable for $resultCode seconds. This does NOT confirm Wii U pairing.")
                } else {
                    setStatus("Discoverability declined")
                    addLog("Discoverability was cancelled or denied")
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST) return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            addLog("Bluetooth permissions granted")
            continueSync()
        } else {
            syncRequested = false
            setStatus("Bluetooth permissions denied")
            addLog("Grant Bluetooth/Nearby Devices permission before attempting SYNC")
        }
    }

    private fun updateButtons() {
        session.setButtons(pressed.fold(0) { acc, bit -> acc or bit })
    }

    private fun setStatus(message: String) {
        if (::syncStatus.isInitialized) syncStatus.text = "Status: $message"
    }

    private fun addLog(line: String) {
        if (!::logs.isInitialized) return
        logs.text = (logs.text.toString() + "\n" + line).takeLast(6000)
        // Connection callbacks log the result. Bluetooth visibility alone never means paired.
        if (line.startsWith("Host connection state: 2")) {
            setStatus("Bluetooth HID host connected (not proof of Wii Remote compatibility)")
        }
    }

    private fun ensurePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val permissions = arrayOf(Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), PERMISSIONS_REQUEST)
            addLog("Bluetooth permissions requested")
            return false
        }
        return true
    }

    override fun onDestroy() {
        syncRequested = false
        session.stop()
        super.onDestroy()
    }
}
