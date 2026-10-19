# Golf CAN Analyzer v1.4 — VW Golf 5 / Raise-PQ / 8227L

Android SDK 27 target, min SDK 23; GitHub Actions builds `app-debug.apk` (Actions → Artifacts).

## Live features
- CANBOX Binder callback, explicit confirmation, 90-second auto-stop, no CAN transmission.
- Validated `0x24` door-state response **only when testing the driver's door**. Other doors not decoded.
- Community Raise-PQ **candidate** telemetry decoder: `2E 41 0D 02 ... CHECKSUM`.
- Dashboard shows candidate RPM, speed, voltage, mileage and raw fuel byte. Diagnostics shows candidate external temperature. Values are **NOT VERIFIED** against vehicle; do not use for driving decisions.
- No coolant-temperature mapping. Fuel raw byte is **not a percentage**. Ignition is not validated.
- Command catalog: per-command counts, changes, latest hex; diagnostics retains full raw log, markers, USB export.
- When no fresh telemetry frame in 12 seconds, display dashes; no invented values.

## Safe testing
Park the car, secure parking brake, start CAN 90s, compare with vehicle instrument cluster. Test engine off, idle, 1500/2000 RPM with stationary car; avoid interacting with the screen while driving. Stop callback after the test; reboot head unit if the stock CAN app behaves differently.

## Build
GitHub → Actions → `Build Golf CAN Analyzer v1.4` → Run workflow → download APK artifact.
