# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Format code
ktfmt .

# Build
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease

# Install to connected device
./gradlew :app:installDebug

# Run unit tests
./gradlew :app:test

# Run a single test class
./gradlew :app:test --tests "com.wboelens.polarrecorder.LogViewModelTest"

# Run instrumented tests (requires device/emulator)
./gradlew :app:connectedAndroidTest

# Code analysis with Detekt
./gradlew detekt

# Clean
./gradlew clean
```

## After Making Changes

Run these commands in order:
1. **Format:** `ktfmt .`
2. **Lint:** `./gradlew detekt`
3. **Test:** `./gradlew :app:test`

## Architecture

**Pattern:** MVVM + Repository with manual dependency injection

**Layers:**
- **UI Layer** (`ui/`): Jetpack Compose screens, components, and dialogs
- **ViewModel Layer** (`viewModels/`): Expose state as LiveData, delegate to repositories
- **Repository Layer** (`repository/`): Manage data with StateFlow, extend BaseRepository for coroutine scope
- **Manager Layer** (`managers/`): Business logic (PolarManager for BLE, RecordingManager for streams, PreferencesManager for settings)
- **Data Saver Layer** (`dataSavers/`): Abstract DataSaver with FileSystem and MQTT implementations

**Dependency Injection:**
Repositories are instantiated in `PolarRecorderApplication.onCreate()` and accessed via the Application singleton.

**Reactive Stack:**
- RxJava3 for Polar BLE SDK data streams
- Kotlin Coroutines + StateFlow for UI state
- Bridge via `kotlinx-coroutines-rx3`

## Navigation Flow

```
deviceSelection → deviceConnection → deviceSettings → recordingSettings → dataSaverInitialization → recording
```

## Key Components

- `PolarManager`: Wraps Polar BLE SDK for device scanning, connection, and streaming
- `RecordingManager`: Orchestrates recording sessions with RxJava disposables for multiple data streams
- `DataSaver` (abstract): Base class for data persistence with `saveData()` and `createJSONPayload()`
- `RecordingService`: Foreground service for background recording
