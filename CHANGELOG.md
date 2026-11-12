# Changelog

## v0.2 — Experimental on-screen SYNC workflow

- Added large **SYNC — attempt pairing with Wii U** button.
- One press starts an Android standard Bluetooth pairing *attempt*: requests nearby-device permissions, prompts to enable Bluetooth if disabled, registers the HID Device profile, then asks Android to become discoverable for 120 seconds.
- Added explicit status text and session logs. The app does not claim paired status when only discoverability succeeds.
- Added automatic permission-result handling, so tapping SYNC once resumes after granting permissions.
- Retained separate HID registration/discoverability diagnostic controls and touch buttons.

**Limitation:** This workflow is not equivalent to the hardware Wii Remote SYNC button. Android public HID Device APIs cannot guarantee real Wii Remote Bluetooth identity or pairing with an unmodified Wii U.
