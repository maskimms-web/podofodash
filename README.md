# Golf CAN Door Monitor v1.2 — Podofo 8227L / Golf 5

Source project ZIP for GitHub Actions; APK is not included. Android 8.1, 1024×600.

## Build
Upload extracted files into GitHub repository. Actions → Build Golf CAN Door Monitor v1.2 → Run workflow. Download `GolfCanDoorMonitor-v1.2-APK` and install `app-debug.apk`.

## Parked test
1. Select USB folder, start 90s callback with confirmation.
2. With ignition ON (engine not needed), close all doors; wait for indicator, then open driver's door; wait; close it; wait. Repeat twice. Leave 5-10s between actions.
3. If desired, test ignition OFF, but door indicator may show UNKNOWN when ignition is off.
4. Stop, export USB report `GolfCanDoorMonitor-v1.2-report.txt`.

## Experimental rules
DOOR OPEN only if `41 01 A0` and `24 02 00` agree within 12s; DOOR CLOSED only if `41 01 A1` and `24 02 01` agree. Other packets or missing/stale signals => UNKNOWN. This is not proven vehicle-wide door state. These codes are correlations from prior logs and may represent another status. Ignition ON/OFF also uses two tentative matching signals. No CAN TX; callback registration can affect OEM service, so do not run while driving. Automatic unregister after 90 seconds, manual STOP. Includes streaming frame reassembly, 12-second marker lockout. Runtime untested.
