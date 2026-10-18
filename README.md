# Golf CAN Diagnostic v0.2

For Volkswagen Golf 5 / Android 11 D02022HA16 / Raise CAN Pro 9.0.6.

## Changes
- Scans installed CAN packages and their declared components (where visible to Android).
- Listens for nine *hypothetical* broadcasts (not verified Raise message names).
- Runs a temporary read-only HTTP server on port 8080 with a random 96-bit URL token.
- Shows the LAN URL on screen; Safari can download `/report.txt`.
- Server stops when the app closes or STOP is pressed. No CAN commands, no root, no external cloud.

## GitHub build
Upload the **contents** of this directory to the repository root. Keep `.github/workflows/build-apk.yml`. If your repo already has `.github/workflows/build.yml`, remove the old workflow or update it to avoid duplicate builds.

GitHub → Actions → Build CAN Diagnostic APK → Run workflow. Download the artifact and extract `app-debug.apk`.

## On the head unit
1. Install the debug APK (same application ID as v0.1; normal update should work if signed with the same debug key; GitHub runners may use a different debug key, so uninstall v0.1 first if signature mismatch occurs).
2. Park the vehicle. Press SCAN, then LISTEN. Try door/lights changes without driving.
3. Connect head unit and iPhone to a network that permits local device-to-device traffic (e.g. same Wi-Fi; some iPhone hotspot configurations may isolate clients).
4. Press Wi-Fi START. Enter the exact displayed URL including the long token into Safari. Download the TXT report and upload it to ChatGPT.
5. Press STOP. Do not share the URL publicly.

If Safari cannot connect, check that the phone and radio are on the same LAN and that client isolation is off. If the address does not appear, the head unit may not have a usable LAN IP. The report is transmitted over local **unencrypted HTTP**, so use a trusted private network only.

## Important limitations
Package components do not imply access to CAN data. Android's app sandbox may block private services, and no root or privileged permissions are requested. Broadcast names are unverified. This is a read-only diagnostic experiment; do not use it as a safety-critical instrument cluster.


## v0.2.1 stability patch
- Package scan runs on a background thread to avoid UI freezes.
- Log UI refresh is throttled to four times per second.
- Fatal scan exceptions are recorded in the report instead of terminating the app where possible.
- Package scan avoids GET_DISABLED_COMPONENTS to reduce OEM PackageManager workload.
- Wi-Fi server requires an actual local IP address; connect Wi-Fi first, then press STOP and Wi-Fi START.
- No CAN bus writes or MCU commands.
