# Golf CAN Diagnostic v0.3

Read-only diagnostic application for the D02022HA16 / Raise CAN Pro head unit. Does not transmit CAN commands or access MCU device nodes.

## Build on GitHub

1. Create a new **public or private GitHub repository**.
2. Upload **the contents of this ZIP** to the repository root (including `.github/workflows/build-apk.yml`).
3. Open **Actions → Build CAN Diagnostic APK → Run workflow**, or push to `main`.
4. Download artifact **GolfCanDiagnostic-debug-apk** and install `app-debug.apk` on the Android head unit.

**Note:** if Android refuses to update the previous installation because the signing key differs, uninstall the older diagnostic app first (old diagnostic app data will be lost). Do not uninstall the factory CAN app.

## Test door events safely (vehicle stationary)

1. Connect the head unit to the iPhone hotspot.
2. Open v0.3; tap **СКАНУВАТИ**, then **СЛУХАТИ**.
3. Open driver door; tap **ДВЕРІ ВІДКРИТІ**. Close it; tap **ДВЕРІ ЗАКРИТІ**. Repeat 2–3 times.
4. Tap **Wi-Fi СТАРТ**. Use the exact displayed `http://IP:PORT/TOKEN` address in iPhone Safari (HTTP, not HTTPS). Download TXT report. Stop server afterward.

The door buttons write **manual timestamps**, not detected CAN events. Only `BROADCAST action=...` entries demonstrate that a registered Android broadcast was received, and such a broadcast would still need validation against door changes.

Wi-Fi server selects first free port 8080–8090 and uses a random access token. Do not share the URL publicly. Some iPhone hotspot configurations may prevent client-to-host connections; if so, transfer the TXT report via USB or other means. No QR code is included in this build.

The factory overlay is evidence that the OEM system receives a door state; it does **not** establish third-party CAN API access. Package and process discovery are best-effort and may be limited by Android permissions. No services are started or bound.
