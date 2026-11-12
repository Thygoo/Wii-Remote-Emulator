package org.example.wiiremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context

/**
 * The Android classic Bluetooth HID Device probe. Requires the calling
 * Activity to have BLUETOOTH_CONNECT permission on Android 12+.
 * No root and no privileged/hidden API calls are used.
 */
@SuppressLint("MissingPermission")
class WiiHidSession(
    private val context: Context,
    private val log: (String) -> Unit,
    private val onRegistrationChanged: (Boolean) -> Unit = {}
) {
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private var currentButtons: Int = 0
    private var reportMode: Int = WiiReportCodec.REPORT_BUTTONS
    private var registered: Boolean = false
    private var profileRequested: Boolean = false
    private var leds: Int = 0x10

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, isRegistered: Boolean) {
            registered = isRegistered
            log("HID registration: $isRegistered")
            onRegistrationChanged(isRegistered)
            if (!isRegistered) host = null
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            host = if (state == BluetoothProfile.STATE_CONNECTED) device else null
            log("Host connection state: $state")
            if (state == BluetoothProfile.STATE_CONNECTED) {
                sendButtons()
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            log("GET_REPORT type=${type.toInt() and 0xFF}, id=0x${id.hex()}, size=$bufferSize")
            if (device == null) return
            val payload: ByteArray? = when (id.toInt() and 0xFF) {
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
            log("Host unplugged")
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            val deviceProfile = proxy as? BluetoothHidDevice
            if (deviceProfile == null) {
                log("HID Device profile unavailable")
                profileRequested = false
                onRegistrationChanged(false)
                return
            }
            hid = deviceProfile
            log("HID Device profile available")
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "Wii Remote HID Lab", "Experimental report probe", "Open prototype",
                0x00.toByte(), WiiHidDescriptor.bytes
            )
            val accepted = deviceProfile.registerApp(sdp, null, null, context.mainExecutor, callback)
            log("registerApp() accepted request: $accepted")
            if (!accepted) onRegistrationChanged(false)
        }
        override fun onServiceDisconnected(profile: Int) {
            registered = false
            profileRequested = false
            host = null
            hid = null
            log("HID Device service disconnected")
            onRegistrationChanged(false)
        }
    }

    fun isRegistered(): Boolean = registered

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun start() {
        if (adapter?.isEnabled != true) {
            log("Bluetooth is off or unavailable")
            return
        }
        if (profileRequested || hid != null) {
            log("HID setup already requested; registered=$registered")
            if (registered) onRegistrationChanged(true)
            return
        }
        val requested = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        profileRequested = requested
        log("Requested HID profile: $requested")
        if (!requested) onRegistrationChanged(false)
    }

    fun setButtons(mask: Int) {
        currentButtons = mask
        log("Buttons ${mask.toString(16).padStart(4, '0')}")
        sendButtons()
    }

    fun stop() {
        hid?.unregisterApp()
        if (hid != null) adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        hid = null
        host = null
        registered = false
        profileRequested = false
        log("HID session stopped")
    }

    private fun sendButtons() {
        val device = host ?: return
        val service = hid ?: return
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
            0x11 -> {
                leds = (bytes.firstOrNull()?.toInt() ?: 0) and 0xF0
                sendAck(id)
            }
            0x12 -> {
                // Wii report 0x12: byte 0 flags, byte 1 requested mode.
                val requested = bytes.getOrNull(1)?.toInt()?.and(0xFF)
                if (requested == 0x30 || requested == 0x31) {
                    reportMode = requested
                    log("Requested report mode 0x${requested.toString(16)}")
                } else log("Report mode unsupported: $requested")
                sendAck(id)
            }
            0x15 -> {
                val device = host ?: return
                hid?.sendReport(device, WiiReportCodec.REPORT_STATUS,
                    WiiReportCodec.status(currentButtons, leds))
            }
            else -> log("Output command 0x${id.toString(16)} not implemented")
        }
    }

    private fun sendAck(outputId: Int) {
        val device = host ?: return
        hid?.sendReport(device, WiiReportCodec.REPORT_ACK,
            WiiReportCodec.ack(currentButtons, outputId))
    }

    private fun Byte.hex(): String = (toInt() and 0xFF).toString(16).padStart(2, '0')
}
