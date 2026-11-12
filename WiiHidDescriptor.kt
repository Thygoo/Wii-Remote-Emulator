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
    val bytes: ByteArray = byteArrayOf(
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
