# HR Beep

Android app MVP for connecting to a Polar H10 over Bluetooth LE, showing live heart rate,
and beeping when the current HR exceeds a user-defined limit.

## Current status

- Kotlin + Jetpack Compose Android app scaffold is in place
- BLE scan and Heart Rate Measurement subscription are implemented
- foreground monitoring service and repeating alarm logic are implemented
- threshold persistence uses DataStore
- unit tests pass with `./gradlew testDebugUnitTest`
- debug APK builds with `./gradlew assembleDebug`

## Local toolchain

This machine was configured with:

- JDK 17 in `~/.local/opt/jdk-17`
- Android SDK in `~/Android/Sdk`
- Gradle wrapper in this repo

For shell builds, use:

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-17"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
./gradlew testDebugUnitTest assembleDebug
```

## Device testing

The app itself needs to run on Android hardware. Android emulator BLE passthrough is not a
reliable test path for a Polar H10, so real-device testing should happen on an Android phone.
