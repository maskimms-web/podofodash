# Golf CAN Diagnostic v0.9 — Binder Callback

Android SDK 27, Podofo / 8227L, CAN Pro Raise-PQ. GitHub Actions builds APK.

## Screen 1024×600
Two compact rows of three buttons, large scrollable log area.

## How to test (vehicle parked)
1. Build via Actions → Build Golf CAN Diagnostic v0.9 → Run workflow; download artifact `GolfCanDiagnostic-v0.9-APK`.
2. Install `app-debug.apk` on the head unit.
3. Press `1 ПОВНА ПЕРЕВІРКА` (metadata only) and wait for `КІНЕЦЬ`.
4. With vehicle stationary, press `2 CAN CALLBACK`, read warning, confirm. Observe 30 seconds. The app attempts registration of `ICanbusInterface.onResult(byte[],int)` and automatically attempts unregister after 30 seconds.
5. Press `СТОП` if anything unusual occurs. If CAN functions are disrupted, stop test and restart the head unit.
6. Press `USB: ВИБРАТИ`, select flash drive, then `USB: ЗБЕРЕГТИ`; send `GolfCanDiagnostic-v0.9-report.txt`.

## Caution
`setCanbusInterface` is a system Binder registration operation. It may replace or interfere with a stock callback; this is **not** proven safe on this firmware. Do not run while driving. Registration may fail due to permissions. No `setValue`, `setBCS`, CAN transmission or UART access is attempted. Registration and removal are Binder transact calls. No root.

## Implementation
Uses the actual `TRANSACTION_setCanbusInterface`, `TRANSACTION_removeCanbusInterface` and `TRANSACTION_onResult` constants reflected from device framework rather than hardcoded transaction numbers. Logs callback payloads in HEX (up to 96 bytes, first 150 messages).
