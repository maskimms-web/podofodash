# Golf R CAN Dashboard v1.5 — 8227L / Raise-PQ / Golf 5

**First visual integration**, based on working v1.4 CAN Analyzer, with a custom 1024×510 Canvas dashboard below a 43px control bar (overall 1024×600 nominal). The image reference is a visual direction; the dashboard is drawn in Java (no fake readings, no bitmap overlay).

- Circular RPM/speed dials, vector Golf silhouette, driver-door tested status, telemetry tiles, bottom clickable diagnostic navigation.
- **Candidate only:** 0x41/02 RPM, speed, voltage, outside temperature, mileage, fuel raw byte. No fuel percentage, no coolant temperature, no invented readings.
- 0x24 door response observed with driver door only. Other doors **not** separately identified. 12s stale-data timeout.
- Binder callback opt-in, 90-second automatic stop, unregister, no CAN TX. Do not use while driving.
- Toolbar: start 90s, stop, diagnostic screen. Dashboard bottom bar: diagnostic screen, command catalog, 0x41 hex, caveats.
- Diagnostic screen retains logs, phase markers, USB folder selection and export.

## Build
Push ZIP contents to a GitHub repository → Actions → Build → download APK artifact. Android minSdk 23, targetSdk 27, Java 17 / Gradle 8.7.

## Testing
Park the vehicle and secure the parking brake. Start 90s and compare RPM, voltage, odometer with stock gauges; send exported report for verification. No on-road interaction with the UI.


## v1.5.1: CAN lifecycle and black/red theme
- Automatic CAN Binder callback registration in Activity.onResume; unregister in onStop/onDestroy. No 90-second limit. No background tracking.
- Long running decoder no longer stops parsing after 2500 callback events. Logging is throttled, decoding is not.
- Red/black dashboard. Candidate 0x41/02 telemetry mapping retained; unknown values shown as dashes.
- Do not use while driving; check that stock CAN apps remain operational. Some firmware may support only one callback.
- Source build not locally verified.
