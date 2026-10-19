# Golf CAN Diagnostic v0.8 — comprehensive read-only inspection

Target: Android 8.1 / SDK 27, MTK 8227L, Raise-PQ VW CANBOX.

## Build
Upload all files to a GitHub repository. Actions → Build Golf CAN Diagnostic v0.8 → Run workflow. Download artifact GolfCanDiagnostic-v0.8-APK; install `app-debug.apk`.

## One-pass check
1. Park the car; turn on the head unit. No need to start engine or drive.
2. Launch the app and tap **ОДНА ПОВНА ПЕРЕВІРКА (БЕЗПЕЧНА)**. Wait for **КІНЕЦЬ** in log (typically seconds).
3. Insert USB drive. Tap **1. ВИБРАТИ ПАПКУ USB** and select USB root.
4. Tap **2. ЗБЕРЕГТИ ЗВІТ І APK НА USB**. Wait for save confirmation.
5. Send `GolfCanDiagnostic-v0.8-report.txt` back for analysis.

Do not press **ВИТЯГНУТИ CANBUS APK** unless you need a new copy (large 59 MB file). Broadcast listening is optional; v0.6 already found none. The full check does not read live CAN frames; it detects system Binder names/descriptors (if permitted), installed AIDL Java classes, package services/providers/receivers/permissions, SocketCAN kernel exposure, and UART device node metadata. It does not open serial devices, send CAN commands, bind to vendor services, call Binder transact, or require root.

The `VwRaiseCanbox` project documents a VW Raise 38400 8N1 UART protocol; that is an external reference, not proof that the UART can be opened by an unprivileged APK. The AC8227L-Root-Features repository mentions `canbus_service`/`android.os.ICanbusService` on a different unit and firmware version. This app tests metadata rather than assuming equivalence.

Caution: on Android 8.1, hidden `ServiceManager` APIs can be restricted. A failed reflection call means the inspection path is blocked, not that the CANBUS service does not exist.
