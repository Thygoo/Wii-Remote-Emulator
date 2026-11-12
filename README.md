# Wii Remote HID Lab v0.2 (Android-only feasibility prototype)

**Status:** Experimental / NOT verified to pair with an unmodified Nintendo Wii U. The demo is not a finished Wii Remote emulator. No root or external adapter is needed to build/run the Android side.

## Purpose

1. Register an Android Bluetooth Classic HID Device using public `BluetoothHidDevice` API (Android 9 / API 28+).
2. Provide an experimental **SYNC** button that requests Bluetooth permissions, enables Bluetooth (with user consent), registers Android HID Device, then requests 120 seconds of discoverability.
3. Observe app registration and incoming HID-host connection callbacks.
4. Generate correctly encoded Wii Remote basic button reports (input report 0x30, two data bytes).
5. Log any HID output requests from a host; minimally respond to LED, report-mode, and status commands.
6. Determine whether an **unmodded Wii U will discover, pair, and connect** to the phone. This is the critical unknown.

## Major compatibility limits

- The virtual **SYNC** button is **not** a real Nintendo Wii Remote SYNC event. It can only start Android HID registration and standard Bluetooth discoverability. It cannot override the Wii U's pairing requirements.
- The genuine Wii Remote uses special legacy Bluetooth pairing, SDP/identity details, and complex proprietary reports. The Android public HID Device APIs do not expose control of every parameter required to impersonate one.
- The prototype's vendor-defined HID report descriptor is intentionally **not a byte-for-byte Wii Remote descriptor**. It may pair with normal HID hosts but **may never be detected by the Wii U**.
- Motion, IR pointing, Nunchuk, Bluetooth legacy PIN handling, memory/EEPROM, real rumble, extension authentication, and power operations are not implemented.
- The provisional 0x31 report contains placeholder accelerometer bytes only; these are not calibrated or real sensor data.
- Registering HID disables Android's Bluetooth HID Host role until the app unregisters it; stay foreground while testing.
- Some devices/ROMs may not expose the HID Device profile at all.

## Build

- Open this folder as a Gradle project in Android Studio (JDK 17, Android SDK 35).
- AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.9 are intended; there is **no Gradle wrapper in this archive**. Use Android Studio's Gradle installation or generate a wrapper with Gradle 8.9 (`gradle wrapper --gradle-version 8.9`) before CLI builds.
- Run on an Android device API 28+; approve Bluetooth Connect and Advertise permissions.
- This package was source-reviewed and unit-tested for its independent report codec but was **not built as an APK** in this environment (Android SDK/Gradle unavailable).

## Test order

1. Turn on the Wii U, press the **physical SYNC button** on the front of the console until the **Wii Remote pairing** screen appears (not the separate Wii U GamePad pairing screen).
2. Launch the app and tap **SYNC — attempt pairing with Wii U**. Grant Android permissions and allow Bluetooth visibility when prompted. This is a one-button flow but may need system confirmations.
3. Watch the in-app status and log: `HID Device profile available`, `HID registration: true`, then `Android discoverable for 120 seconds`.
4. If a Bluetooth HID host connects, the status shows `Host connection state: 2` (connected). This alone does **not** prove Wii Remote emulation.
5. First test with a standard Bluetooth HID host (Linux/Windows) if the Wii U does not connect. That checks Android HID support independently.
6. If the Wii U never opens a connection, the problem is likely low-level Bluetooth identity/legacy pairing and will not be solved simply by adding more touch buttons.

## What to look for in Logcat

- `registerApp()` queued vs `onAppStatusChanged(..., true)` actual registration
- `onConnectionStateChanged(..., STATE_CONNECTED)`
- `OUTPUT 0x12` requests report mode
- `OUTPUT 0x15` status request

## Code map

- `MainActivity.kt` = touchscreen, one-button experimental SYNC workflow, user permissions, Bluetooth enable and discoverable requests, status/log
- `WiiHidSession.kt` = Bluetooth profile, HID callbacks, report commands
- `WiiHidDescriptor.kt` = minimal generic vendor-defined HID descriptor
- `WiiReportCodec.kt` = Wii button bitfield and report payload encoder

## References

- https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- https://developer.android.com/reference/android/bluetooth/BluetoothHidDeviceAppSdpSettings
- https://www.wiibrew.org/wiki/Wii_Remote
- https://en-americas-support.nintendo.com/app/answers/detail/a_id/1133/p/603
