# Golf CAN Door Monitor v1.2.2 — Golf 5 / Podofo 8227L

GitHub Actions source project (not a prebuilt APK). Android 8.1, 1024×600.

## Build
Upload all extracted project files to your GitHub repository, then Actions → Build Golf CAN Door Monitor v1.2.2 → Run workflow. Download `GolfCanDoorMonitor-v1.2.2-APK` and install `app-debug.apk`.

## What changed
- Corrected inverted OPEN/CLOSED interpretation reported in the vehicle test: `41 01 A0`/`24 xx 00` now mean CLOSED, `41 01 A1`/`24 xx 01` mean OPEN.
- Door decoder accepts 41 values `80/81/A0/A1`, and 24 values `00/02/04/06` in the first payload byte; requires both channels to agree and be recent (12 seconds).
- Main door indicator and OPEN/CLOSED evidence now derive from the exact same state. UNKNOWN when data disagree or expire.
- Version and report file updated to v1.2.2.

## Test (parked only)
1. Select USB folder, start 90-second receive with confirmation.
2. Ignition ON, all doors closed: wait for GREEN CLOSED. Open driver's door: wait for RED OPEN. Close: GREEN CLOSED. Repeat twice, wait up to 12 seconds between changes.
3. Check whether both small labels `41` and `24` agree. If the large tile stays UNKNOWN, record what the small labels show.
4. Export `GolfCanDoorMonitor-v1.2.2-report.txt` to USB and send the report.

This is an experimental CAN decoder, not yet verified across all doors and ignition states. No CAN TX. Callback registration may interfere with the stock CAN service. Do not use while driving; automatic stop at 90 seconds, manual STOP available.
