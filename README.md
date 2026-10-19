# Golf CAN Live Decoder v1.0 — Podofo 8227L / VW Golf 5

Build with GitHub Actions: Actions → Build Golf CAN Live Decoder v1.0 → Run workflow → artifact GolfCanLiveDecoder-v1.0-APK → app-debug.apk.

1024×600 layout: 3 rows of 4 compact buttons; log scrolls below. Android minSdk 23, targetSdk 27.

## Test (parked vehicle only)
1. Install APK. Press USB ПАПКА and select USB stick.
2. Press ▶ ПРИЙОМ 60 с and confirm. Start with all doors closed, ignition off.
3. Press ДВЕРІ ВІДКР. immediately before opening driver's door, then ДВЕРІ ЗАКР. before closing it.
4. Press ЗАПАЛ. ON before turning ignition on; ЗАПАЛ. OFF before turning it off.
5. Optionally, with the vehicle stationary and in neutral, press МАРКЕР: ХОЛОСТІ and then МАРКЕР: ГАЗ for a brief change of RPM. Do not do this if unsafe.
6. Wait for auto-stop and UNREGISTER result=true, or press ■ СТОП.
7. Press USB ЗВІТ and share GolfCanLiveDecoder-v1.0-report.txt.

The app sends NO CAN commands; it registers and removes a Binder callback. Registration may still interfere with the stock CAN service. Stop immediately if stock functionality changes, and reboot the head unit if necessary. No testing while driving.

## Parsing
onResult(byte[],int): int is interpreted as the count of meaningful bytes (v0.9 observed 64-byte padded buffers). 2E CMD LEN PAYLOAD CHECKSUM frames; observed checksum makes the sum of all frame bytes mod 256 equal 0x2D. The decoder records raw callback bytes, frames, phase markers and changes; it does NOT assert which byte is RPM or door state.

This ZIP is source only; APK is built by GitHub Actions. The build has not been run locally.
