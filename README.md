# HR Beep

Android app that connects to a Polar H10 heart rate monitor over Bluetooth LE, displays live heart rate, and beeps when the heart rate crosses user-defined upper or lower thresholds.

## Features

- **Live HR display** — connects to a Polar H10 via BLE and shows current heart rate in real time
- **Live HR graph** — rolling 60-sample sparkline behind the HR readout; green when in bounds, red when out of bounds
- **Upper limit alarm** — beeps (600 Hz tone) when HR exceeds the configured upper threshold
- **Lower limit alarm** — beeps (400 Hz tone) when HR drops below an optional lower threshold
- **HR-matched beep cadence** — beep interval matches your current heart rate (clamped to 333 ms – 2 s)
- **TTS audio alerts** — spoken announcement when the sensor first connects or disconnects mid-session
- **GPS distance tracking** — optional distance tracking during sessions with per-kilometre TTS announcements
- **Pace tracking** — live pace (min/km) shown in session stats and stored with each session
- **Session history** — completed sessions are persisted (Room) and browsable in a swipeable history tab; each card shows start time, duration, average HR, distance, pace, and a miniature HR graph with min/max labels
- **Battery level** — shows connected device battery percentage
- **Foreground monitoring service** — monitoring continues while the screen is off
- **Settings persistence** — thresholds are saved with DataStore; last-connected device address is remembered for auto-reconnect

## Screens

| Tab | Contents |
|-----|----------|
| Monitor | Permission/BT status, device scan, min/max BPM stepper inputs, large live HR readout with sparkline graph, session stats (average HR, duration, distance) |
| History | Scrollable list of past sessions with delete (undo supported), empty state when none exist; each card shows a 2×2 stats grid and miniature HR graph |

## Tech stack

- Kotlin + Jetpack Compose (Material 3, no XML layouts)
- Single-Activity MVVM — `ViewModel` + Kotlin `Flow` for UI state
- Room (session history), DataStore (user preferences)
- `AudioTrack` with sine-wave synthesis and amplitude envelopes for alarm tones; `TextToSpeech` for sensor connection and distance announcements
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
