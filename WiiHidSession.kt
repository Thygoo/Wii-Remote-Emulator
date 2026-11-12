package org.example.wiiremote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Android Bluetooth Classic HID diagnostics. This is NOT a full Wii Remote
 * implementation and cannot override legacy pairing or Bluetooth SDP details.
 * Always interpret connect() as a request, not proof of a connection.
 */
@SuppressLint("MissingPermission")
class WiiHidSession(
    private val context: Context,
    private val log: (String) -> Unit,
    private val onRegistrationChanged: (Boolean) -> Unit = {},
    private val onHostStateChanged: (String) -> Unit = {}
) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private val main = Handler(Looper.getMainLooper())
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var currentButtons = 0
    private var reportMode = WiiReportCodec.REPORT_BUTTONS
    private var leds = 0x10

    private var registered = false
    private var registering = false
    private var profileRequested = false
    private var wanted = false
    private var foreground = false
    private var recoveryAttempts = 0
    private var connectionStatus = "Disconnected"
    private val recovery = Runnable {
        if (wanted && foreground && !registered && !registering) {
            log("Attempting one foreground HID re-registration")
            start()
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registering = false
            registered = isRegistered
            log("HID registration: $isRegistered")
            if (!isRegistered) {
                host = null
                connectionStatus = "Disconnected (HID unregistered)"
                onHostStateChanged(connectionStatus)
                // One limited retry, only while our screen is foreground.
                if (wanted && foreground && recoveryAttempts < 1) {
                    recoveryAttempts++
                    main.removeCallbacks(recovery)
                    main.postDelayed(recovery, 900)
                    log("HID recovery scheduled (attempt $recoveryAttempts/1)")
                }
            } else {
                recoveryAttempts = 0
                main.removeCallbacks(recovery)
            }
            onRegistrationChanged(isRegistered)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            host = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            connectionStatus = stateLabel(state) + " - " + deviceLabel(device)
            log("Host connection state: $state (${stateLabel(state)}) with ${deviceLabel(device)}")
            onHostStateChanged(connectionStatus)
            if (state == BluetoothProfile.STATE_CONNECTED) sendButtons()
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            log("GET_REPORT type=${type.toInt() and 0xFF}, id=0x${id.hex()}, size=$bufferSize")
            if (device == null) return
            val payload = when (id.toInt() and 0xFF) {
                WindowsGamepad.REPORT_ID -> WindowsGamepad.report(currentButtons)
                0x20 -> WiiReportCodec.status(currentButtons, leds)
                0x30 -> WiiReportCodec.buttons(currentButtons)
                0x31 -> WiiReportCodec.buttonsAndNeutralAccel(currentButtons)
                else -> null
            }
            if (payload != null) hid?.replyReport(device, type, id, payload)
            else hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_INVALID_RPT_ID)
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            handleOutput(id.toInt() and 0xFF, data ?: byteArrayOf())
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            handleOutput(reportId.toInt() and 0xFF, data ?: byteArrayOf())
        }

        override fun onVirtualCableUnplug(device: BluetoothDevice?) {
            host = null
            connectionStatus = "Unplugged - " + deviceLabel(device)
            log("Host unplugged: ${deviceLabel(device)}")
            onHostStateChanged(connectionStatus)
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            profileRequested = false
            val deviceProfile = proxy as? BluetoothHidDevice
            if (deviceProfile == null) {
                log("HID Device profile unavailable")
                onRegistrationChanged(false)
                return
            }
            hid = deviceProfile
            log("HID Device profile available")
            if (wanted) registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            registered = false
            registering = false
            profileRequested = false
            host = null
            hid = null
            connectionStatus = "HID service disconnected"
            log("HID Device service disconnected")
            onRegistrationChanged(false)
            onHostStateChanged(connectionStatus)
            maybeRecover()
        }
    }

    fun isRegistered(): Boolean = registered
    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        log(if (isForeground) "App entered foreground" else "App left foreground; Android may unregister HID")
        if (isForeground && wanted && !registered && !registering) start()
    }

    fun start() {
        wanted = true
        if (!isBluetoothEnabled()) {
            log("Bluetooth is disabled or unavailable")
            return
        }
        if (registered) {
            log("HID already registered")
            onRegistrationChanged(true)
            return
        }
        if (registering) {
            log("HID registration request pending")
            return
        }
        if (hid != null) {
            registerApp() // Critical v0.2 fix: re-register using an existing profile proxy.
            return
        }
        if (profileRequested) {
            log("Waiting for HID Device profile service")
            return
        }
        val requested = adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE) == true
        profileRequested = requested
        log("Requested HID profile: $requested")
        if (!requested) onRegistrationChanged(false)
    }

    private fun registerApp() {
        val deviceProfile = hid ?: return
        if (registered || registering || !wanted) return
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "Wii Remote HID Lab Gamepad", "Windows gamepad and HID report probe", "Open prototype",
            0x08.toByte(), WiiHidDescriptor.bytes
        )
        registering = true
        val accepted = try {
            deviceProfile.registerApp(sdp, null, null, context.mainExecutor, callback)
        } catch (e: Exception) {
            log("registerApp() exception: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
        log("registerApp() accepted request: $accepted (not proof of registration)")
        if (!accepted) {
            registering = false
            onRegistrationChanged(false)
        }
    }

    private fun maybeRecover() {
        if (wanted && foreground && recoveryAttempts < 1) {
            recoveryAttempts++
            main.removeCallbacks(recovery)
            main.postDelayed(recovery, 900)
            log("HID profile recovery scheduled (attempt $recoveryAttempts/1)")
        }
    }

    fun pairedDevices(): List<BluetoothDevice> = try {
        adapter?.bondedDevices?.sortedWith(compareBy({ it.name ?: "" }, { it.address })) ?: emptyList()
    } catch (e: SecurityException) {
        log("Paired-device permission error: ${e.message}")
        emptyList()
    }

    fun deviceLabel(device: BluetoothDevice?): String = try {
        device?.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
    } catch (_: SecurityException) {
        "Bluetooth device (permission missing)"
    }

    fun connect(device: BluetoothDevice) {
        if (!registered || hid == null) {
            log("Cannot connect: register HID first")
            return
        }
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            log("Cannot connect: selected device is not paired")
            return
        }
        log("Requesting HID connection to ${deviceLabel(device)}")
        val requested = try { hid?.connect(device) == true }
                        catch (e: Exception) {
                            log("connect() exception: ${e.javaClass.simpleName}: ${e.message}")
                            false
                        }
        log("connect() request accepted: $requested (not proof of connection)")
        if (requested) {
            connectionStatus = "Request sent - ${deviceLabel(device)}"
            onHostStateChanged(connectionStatus)
        }
    }

    fun disconnectHost() {
        val device = host
        if (device == null) { log("No connected HID host to disconnect"); return }
        val requested = hid?.disconnect(device) == true
        log("disconnect() request accepted: $requested")
    }

    fun diagnostics(): String {
        val a = adapter
        if (a == null) return "Bluetooth adapter: unavailable"
        val isOn = try { a.isEnabled } catch (_: SecurityException) { false }
        val name = try { a.name ?: "(unnamed)" } catch (_: SecurityException) { "(permission needed)" }
        val discoverability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            "Unknown (Nearby Devices / SCAN permission required)"
        } else try {
            when (a.scanMode) {
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "Discoverable"
                BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "Connectable, not discoverable"
                else -> "Not connectable / Bluetooth off"
            }
        } catch (_: SecurityException) { "Unavailable (permission)" }
        val hosts = try { hid?.connectedDevices?.joinToString { deviceLabel(it) } }
                    catch (_: SecurityException) { "(permission missing)" }
        return "Bluetooth on: $isOn\nPhone name: $name\nDiscoverability: $discoverability" +
            "\nHID registered: $registered\nHID registering: $registering" +
            "\nHID host: $connectionStatus\nConnected HID hosts: ${hosts?.ifEmpty { "None" } ?: "None"}"
    }

    fun setButtons(mask: Int) {
        if (currentButtons == mask) return
        currentButtons = mask
        log("Buttons ${mask.toString(16).padStart(4, '0')}")
        sendButtons()
    }

    fun stop() {
        wanted = false // Prevent re-registering as a result of stop() callbacks.
        main.removeCallbacks(recovery)
        try { hid?.unregisterApp() } catch (_: Exception) { }
        try { hid?.let { adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) } }
        catch (_: Exception) { }
        hid = null
        host = null
        registered = false
        registering = false
        profileRequested = false
        recoveryAttempts = 0
        connectionStatus = "Stopped"
        log("HID session stopped")
        onRegistrationChanged(false)
        onHostStateChanged(connectionStatus)
    }

    private fun sendButtons() {
        val device = host ?: return
        val service = hid ?: return
        val gamepadQueued = service.sendReport(device, WindowsGamepad.REPORT_ID, WindowsGamepad.report(currentButtons))
        log("Gamepad report 1: queued=$gamepadQueued")
        val payload = if (reportMode == WiiReportCodec.REPORT_BUTTONS_ACCEL)
            WiiReportCodec.buttonsAndNeutralAccel(currentButtons)
        else WiiReportCodec.buttons(currentButtons)
        val mode = if (reportMode == WiiReportCodec.REPORT_BUTTONS_ACCEL) 0x31 else 0x30
        val queued = service.sendReport(device, mode, payload)
        log("sendReport(0x${mode.toString(16)}): queued=$queued")
    }

    private fun handleOutput(id: Int, bytes: ByteArray) {
        log("OUTPUT 0x${id.toString(16)}: ${bytes.joinToString(" ") { it.hex() }}")
        when (id) {
            0x11 -> { leds = (bytes.firstOrNull()?.toInt() ?: 0) and 0xF0; sendAck(id) }
            0x12 -> {
                val requested = bytes.getOrNull(1)?.toInt()?.and(0xFF)
                if (requested == 0x30 || requested == 0x31) reportMode = requested
                else log("Report mode unsupported: $requested")
                sendAck(id)
            }
            0x15 -> host?.let { hid?.sendReport(it, WiiReportCodec.REPORT_STATUS,
                WiiReportCodec.status(currentButtons, leds)) }
            else -> log("Output command 0x${id.toString(16)} not implemented")
        }
    }

    private fun sendAck(outputId: Int) {
        host?.let { hid?.sendReport(it, WiiReportCodec.REPORT_ACK,
            WiiReportCodec.ack(currentButtons, outputId)) }
    }

    private fun stateLabel(state: Int) = when (state) {
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "DISCONNECTED"
    }

    private fun Byte.hex(): String = (toInt() and 0xFF).toString(16).padStart(2, '0')
}
