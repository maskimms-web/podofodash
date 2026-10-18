# Golf CAN Diagnostic v0.5 — USB export

Android SDK 27 compatible. Read-only inspection and copying of `com.xygala.canbus` and `com.xygala.pvcanset`. No CAN commands, root, Wi-Fi server, or firmware changes.

## GitHub Actions
Upload contents of `golfv04/` to the repository root (including `.github/workflows/build.yml`). Run workflow, download `app-debug.apk` from artifact.

## On head unit
1. Insert USB flash drive.
2. Open app and tap **1. ВИБРАТИ ПАПКУ USB**. In Android system picker, choose USB drive and confirm **Use this folder / Allow**. Do not select internal storage.
3. Tap **ВИТЯГНУТИ CANBUS APK**. It attempts to read installed APKs; inaccessible packages are logged as errors. If USB folder was chosen, available APK copies and report are exported automatically.
4. Tap **2. ЗБЕРЕГТИ ЗВІТ І APK НА USB** to retry or refresh report.
5. Eject USB safely before removal.

Android's Storage Access Framework is used, so no fixed USB filesystem path or storage permission is needed. Depending on device firmware, the system folder picker may not expose the USB drive. Existing files with the same name may be duplicated by some document providers.
