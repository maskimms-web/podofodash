# Golf CAN Signal Analyzer v1.1 — VW Golf 5 / Podofo 8227L

Source ZIP for GitHub Actions, not a prebuilt APK. Android 8.1 (SDK 27), 1024x600. No root, no CAN TX.

## Build
Upload all ZIP contents into a GitHub repository. Actions > Build Golf CAN Signal Analyzer v1.1 > Run workflow. Download artifact `GolfCanSignalAnalyzer-v1.1-APK`, install `app-debug.apk`.

## Test — vehicle parked
1. USB ПАПКА: select USB folder.
2. Start `▶ ПРИЙОМ 90 с`, confirm. Callback registration is experimental and could affect stock CAN software.
3. Each phase button starts a **12-second window**: first 3 seconds are transition, next 9 seconds sampled. Press marker immediately before the physical action. Do not press another phase button until previous phase finishes.
4. Suggested phases: ignition OFF baseline (already off), ignition ON (turn key), ignition OFF (turn off); doors closed, doors open, doors closed; idle and higher RPM only if safe while stationary. To sample the same phase twice, wait for the phase-end message before pressing again.
5. Press `ПІДСУМОК` to log command+payload frequencies for stable windows. Auto-stop at 90 s, or `■ СТОП` sooner. Check `UNREGISTER result=true`.
6. USB ЗВІТ, share `GolfCanSignalAnalyzer-v1.1-report.txt`.

## Data handling
Logs all callback events and valid 2E frames, and collects stable-phase statistics only after 3-second settling. Groups by phase label, command and raw payload. A marker is not proof of a specific signal; other vehicle activity can overlap. No automatic RPM, speed, ignition or door value is asserted. `type` is the usable byte count. Checksum: sum of frame bytes mod 256 == 0x2D. Frames may span callbacks and partial frames are noted; v1.1 is not a fully streaming reassembler.

Safety: use only while parked; if stock CAN behavior changes, stop and reboot head unit if needed. APK build and runtime are not verified locally.
