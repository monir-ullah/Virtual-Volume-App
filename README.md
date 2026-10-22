# Virtual Volume App & System Volume Lock

A modern Android application built with Jetpack Compose that provides full custom volume control, system volume locking capabilities, and an accessible virtual floating overlay button.

## Features

- **In-App Volume Controller**: Real-time adjustment and fine-grained control over Media, Ringtone, Notification, Alarm, and System volume streams with dynamic visual feedback.
- **Volume Lock Engine**: Foreground service-backed engine that locks desired volume levels and automatically enforces/restores volume states against accidental hardware key presses or app adjustments.
- **Virtual Floating Overlay Button**: Customizable draggable floating action button overlay accessible system-wide with state indicators including a red circle lock icon badge when active.
- **Material 3 Expressive UI**: Full Edge-to-Edge Jetpack Compose interface with dynamic light & dark theme support.

## Tech Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM / Unidirectional Data Flow with Kotlin Coroutines & Flow
- **Background Operations**: Android Foreground Service (`VolumeLockService`) & Accessibility Service support
- **Min SDK**: API 24 (Android 7.0)
- **Target SDK**: API 35 (Android 15)

## Getting Started & Building

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or newer
- JDK 17
- Android SDK 35

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/monir-ullah/Virtual-Volume-App.git
   cd Virtual-Volume-App
   ```

2. Open the project in Android Studio.

3. Build the debug APK via Gradle:
   ```bash
   ./gradlew :app:assembleDebug
   ```

4. Install on a connected Android device or emulator:
   ```bash
   ./gradlew :app:installDebug
   ```

## Required Permissions

- `SYSTEM_ALERT_WINDOW`: Display virtual floating overlay over other apps.
- `POST_NOTIFICATIONS`: Show foreground service notification for Volume Lock Engine (Android 13+).
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Maintain volume lock monitoring service reliability.

## License

This project is open-source and available under the [MIT License](LICENSE).
