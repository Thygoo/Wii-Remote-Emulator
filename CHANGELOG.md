# Changelog

## v0.3
- Added paired Bluetooth device selector and an explicit HID connect attempt to a selected, previously bonded Windows PC.
- Added HID host state, Bluetooth adapter name, actual scan mode, ACL events and bond-state logs.
- Fixed v0.2's stale `HID setup already requested; registered=false` path.
- Added foreground-only limited HID re-registration after unexpected unregister.
- Separated pairing/discoverability, HID registration, connect request and confirmed `STATE_CONNECTED` in UI/logs.
- Increased versionCode to 3 and declared Android 12+ Bluetooth Scan permission for scan mode diagnostics.

## v0.2
- Experimental SYNC registration and discoverability workflow.
