# HR Beep

Android app that connects to a Polar H10 heart rate monitor over Bluetooth LE, displays live heart rate, and beeps when the heart rate crosses user-defined upper or lower thresholds.

## Features

- **Live HR display** — connects to a Polar H10 via BLE and shows current heart rate in real time
- **Upper limit alarm** — beeps (600 Hz tone) when HR exceeds the configured upper threshold
- **Lower limit alarm** — beeps (400 Hz tone) when HR drops below an optional lower threshold
- **HR-matched beep cadence** — beep interval matches your current heart rate (clamped to 333 ms – 2 s)
- **Alert intensity control** — slider to set relative alarm volume (0–100%)
- **GPS distance tracking** — optional distance tracking during sessions with per-kilometre TTS announcements
- **Session history** — completed sessions are persisted (Room) and browsable in a swipeable history tab; shows start time, duration, average HR, and distance
- **Battery level** — shows connected device battery percentage
- **Foreground monitoring service** — monitoring continues while the screen is off
- **Settings persistence** — thresholds and intensity are saved with DataStore

## Screens

| Tab | Contents |
|-----|----------|
| Monitor | Permission/BT status, device scan, upper/lower limit inputs, intensity slider, large live HR readout, session stats |
| History | Paginated list of past sessions with delete, empty state when none exist |

## Tech stack

- Kotlin + Jetpack Compose (Material 3, no XML layouts)
- Single-Activity MVVM — `ViewModel` + Kotlin `Flow` for UI state
- Room (session history), DataStore (user preferences)
- `AudioTrack` with sine-wave synthesis and amplitude envelopes for alarm tones
- Android BLE GATT for Heart Rate and Battery Service profiles
- Foreground service with `FOREGROUND_SERVICE_CONNECTED_DEVICE` and optional `FOREGROUND_SERVICE_LOCATION`
- Min SDK 31 (Android 12), Target SDK 35

## Building

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-17"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"

./gradlew testDebugUnitTest      # run unit tests
./gradlew assembleDebug          # build debug APK
```

APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Device testing

BLE passthrough on the Android emulator is unreliable for the Polar H10, so test on a real Android 12+ device. Install via ADB:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Runtime permissions required: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, and optionally `ACCESS_FINE_LOCATION` for GPS tracking.
