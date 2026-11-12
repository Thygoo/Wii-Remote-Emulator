# Wii Remote HID Lab v0.3 — Android-only Bluetooth HID diagnostics

**Status:** Experimental. This source is NOT a completed Nintendo Wii Remote emulator. Windows HID connection is untested and connection to an unmodified Wii U is not established.

## Changes from 0.2

- Added **Refresh paired devices** selector, with separate **Connect HID to selected paired PC** and disconnect actions.
- Added Bluetooth registration, connected HID host, scan/discoverability mode, and Bluetooth adapter name diagnostics.
- Added bond-state and Bluetooth ACL event logging (ACL != HID).
- Added `BluetoothHidDevice.connect(device)` for hosts already paired using the Android Bluetooth settings.
- Fixed the stuck `HID setup already requested; registered=false` state by allowing `registerApp()` again with an existing profile proxy.
- Added a single guarded foreground re-registration attempt after unexpected HID unregister. This is not a background service.
- Logs distinguish request accepted from actual `Host connection state: 2` confirmation.
- Kept the existing experimental Nintendo-style 0x30/0x31 report payloads and generic HID descriptor for comparison with v0.2.

## Limitations and warnings

- `connect()` works only with *already paired* Bluetooth hosts and is only a request; `onConnectionStateChanged(... STATE_CONNECTED)` is the actual success signal.
- Windows can pair with Android for other phone services without connecting to this app's HID Device service.
- Current HID descriptor is **vendor-defined**, not a standard Windows gamepad descriptor. It might not appear in `joy.cpl` even when the HID connection succeeds.
- Android may unregister HID when the app leaves the foreground. Keep the app visible. The one-off recovery is opportunistic and never defeats Android OS restrictions.
- This app does **not** implement full Wii Remote Bluetooth identity, legacy pairing PIN, genuine Nintendo SDP record, MotionPlus, IR, Nunchuk, or other protocols necessary for stock Wii U compatibility.
- No root required. Android API 28+ and Bluetooth Classic HID Device support required. Android 12+ also needs Nearby Devices permissions (Connect, Advertise, Scan).

## Test on Android / Windows

1. Build with GitHub Actions and install the debug APK.
2. On Android, make sure Windows appears in **Settings → Connected devices → Previously connected devices**. Pair in system settings if necessary.
3. Open v0.3 and grant **Nearby Devices** permissions.
4. Press **Register / recover HID device**. Look for `HID registration: true`.
5. Press **Refresh paired devices**, select your Windows PC, then **CONNECT HID TO SELECTED PAIRED PC**.
6. Read the event log: `connect() request accepted: true` is *not* a confirmed connection. `Host connection state: 2 (CONNECTED)` is success.
7. If Windows only shows a connected phone and no HID connection callback, the problem is HID service negotiation or host-side permissions, not plain Bluetooth discovery.
8. If Android loses HID registration, bring the app foreground and press **Register / recover HID device** again.

## Build configuration

- Gradle 8.9, Java 17, AGP 8.7.3, Kotlin 2.0.21, Android SDK 35.
- `app/build.gradle.kts`: versionCode 3, versionName 0.3; same applicationId as v0.2.
- `.github/workflows/build-apk.yml`: tested SDK setup structure with `android-actions/setup-android@v4`, `sdkmanager`, and Gradle `:app:assembleDebug`.
- The debug APK is only created by the GitHub Actions workflow. The source was not compiled in the offline packaging environment, so a compile error may need correction.
- Debug APKs from different GitHub runners may use different signing keys; upgrading over an older debug APK may be rejected. Uninstall the old version first if necessary (this clears the app data).

## Code map

- `MainActivity.kt` – Android touchscreen, paired device picker, sync and diagnostics UI, broadcast event logs.
- `WiiHidSession.kt` – HID Device registration, recovery, manual host connect, callbacks, report sending.
- `WiiHidDescriptor.kt` – generic vendor-defined descriptor (unchanged).
- `WiiReportCodec.kt` – basic Wii-style input report payloads (unchanged).

## Android API references

- https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice
- https://developer.android.com/reference/android/bluetooth/BluetoothAdapter
- https://developer.android.com/reference/android/bluetooth/BluetoothDevice
