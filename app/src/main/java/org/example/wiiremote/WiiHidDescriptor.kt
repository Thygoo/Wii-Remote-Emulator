package org.example.wiiremote

/**
 * A deliberately minimal vendor-defined HID descriptor that advertises four
 * input reports and three output reports. It is NOT the genuine Nintendo
 * Wii Remote SDP record, identity, report descriptor, or authentication flow.
 *
 * The point is to probe whether Android can register this HID device and
 * whether a host will establish a connection, before implementing more modes.
 */
object WiiHidDescriptor {
    val bytes: ByteArray = WindowsGamepad.descriptor + byteArrayOf(
        0x06, 0x00, 0xFF.toByte(),       // Usage Page (vendor-defined)
        0x09, 0x01,                     // Usage (1)
        0xA1.toByte(), 0x01,            // Collection (Application)
        0x15, 0x00,                     // Logical min 0
        0x26, 0xFF.toByte(), 0x00,      // Logical max 255
        0x75, 0x08,                     // 8 bits per field
        0x09, 0x01, 0x85.toByte(), 0x30, 0x95.toByte(), 0x02, 0x81.toByte(), 0x02,
        0x09, 0x01, 0x85.toByte(), 0x31, 0x95.toByte(), 0x05, 0x81.toByte(), 0x02,
        0x09, 0x01, 0x85.toByte(), 0x20, 0x95.toByte(), 0x06, 0x81.toByte(), 0x02,
        0x09, 0x01, 0x85.toByte(), 0x22, 0x95.toByte(), 0x04, 0x81.toByte(), 0x02,
        0x09, 0x01, 0x85.toByte(), 0x11, 0x95.toByte(), 0x01, 0x91.toByte(), 0x02,
        0x09, 0x01, 0x85.toByte(), 0x12, 0x95.toByte(), 0x02, 0x91.toByte(), 0x02,
        0x09, 0x01, 0x85.toByte(), 0x15, 0x95.toByte(), 0x01, 0x91.toByte(), 0x02,
        0xC0.toByte()                   // End collection
    )
}

/** Standard Windows gamepad: report 1, sixteen buttons followed by X/Y axes. */
object WindowsGamepad {
    const val REPORT_ID = 1
    val descriptor: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x05, 0xA1, 0x01, 0x85, REPORT_ID,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x10,
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10, 0x81, 0x02,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31,
        0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x02, 0x81, 0x02, 0xC0
    ).map { it.toByte() }.toByteArray()

    fun report(mask: Int): ByteArray {
        val buttons = intArrayOf(WiiReportCodec.A, WiiReportCodec.B, WiiReportCodec.ONE,
            WiiReportCodec.TWO, WiiReportCodec.MINUS, WiiReportCodec.PLUS, WiiReportCodec.HOME)
        var bits = 0
        buttons.forEachIndexed { index, button ->
            if (mask and button != 0) bits = bits or (1 shl index)
        }
        val x = (if (mask and WiiReportCodec.RIGHT != 0) 127 else 0) -
            (if (mask and WiiReportCodec.LEFT != 0) 127 else 0)
        val y = (if (mask and WiiReportCodec.DOWN != 0) 127 else 0) -
            (if (mask and WiiReportCodec.UP != 0) 127 else 0)
        return byteArrayOf(bits.toByte(), (bits ushr 8).toByte(), x.toByte(), y.toByte())
    }
}
