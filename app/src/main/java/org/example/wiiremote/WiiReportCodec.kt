package org.example.wiiremote

/** Pure Kotlin encoding of the basic Wii Remote report modes. No Android dependencies. */
object WiiReportCodec {
    const val REPORT_BUTTONS = 0x30
    const val REPORT_BUTTONS_ACCEL = 0x31
    const val REPORT_STATUS = 0x20
    const val REPORT_ACK = 0x22

    // Values follow WiiBrew's 16-bit big-endian button mask.
    const val TWO = 0x0001
    const val ONE = 0x0002
    const val B = 0x0004
    const val A = 0x0008
    const val MINUS = 0x0010
    const val HOME = 0x0080
    const val LEFT = 0x0100
    const val RIGHT = 0x0200
    const val DOWN = 0x0400
    const val UP = 0x0800
    const val PLUS = 0x1000

    fun buttons(mask: Int): ByteArray = byteArrayOf((mask ushr 8).toByte(), mask.toByte())

    // Placeholder accelerometer center values; NOT real sensor calibration.
    fun buttonsAndNeutralAccel(mask: Int): ByteArray =
        buttons(mask) + byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte())

    fun status(mask: Int, leds: Int = 0x10, battery: Int = 0xC0): ByteArray =
        buttons(mask) + byteArrayOf(leds.toByte(), 0, 0, battery.toByte())

    fun ack(mask: Int, outputReportId: Int, error: Int = 0): ByteArray =
        buttons(mask) + byteArrayOf(outputReportId.toByte(), error.toByte())
}
