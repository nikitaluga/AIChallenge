# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin Multiplatform (KMP) project targeting Android, iOS, and Server. Uses Compose Multiplatform for shared UI and Ktor for the backend. Package: `ru.nikitaluga.aichallenge`.

## Build Commands

```shell
# Android
./gradlew :composeApp:assembleDebug

# Server (Ktor, runs on 0.0.0.0:8080)
./gradlew :server:run

# iOS — open iosApp/ in Xcode and run from there

# Run all tests
./gradlew test

# Run tests per module
./gradlew :shared:allTests
./gradlew :composeApp:allTests
./gradlew :server:test
```

## Architecture: MVI + Clean Architecture

### Module Responsibilities

- **shared** — Domain + Data layers. Platform-agnostic. Uses expect/actual for platform abstractions.
- **composeApp** — Presentation layer (MVI). Compose Multiplatform UI for Android + iOS.
- **server** — Ktor server (JVM). Depends on `shared`.
- **iosApp** — SwiftUI wrapper hosting the Compose UI via `MainViewController`.

### Layers inside `shared`

```
shared/src/commonMain/.../
├── domain/
│   ├── model/        # Pure Kotlin data classes — no platform/framework deps
│   ├── repository/   # Repository interfaces (contracts for data access)
│   └── usecase/      # Use cases: orchestrate repository, return Result<T>
├── data/
│   └── repository/   # Repository implementations (wrap API services)
└── api/              # Data sources: Ktor HTTP client + serializable models
```

### Layers inside `composeApp`

Each screen lives in its own package with exactly 3 files:

```
composeApp/src/commonMain/.../
├── App.kt                     # Root composable — tab navigation only
├── chat/
│   ├── ChatContract.kt        # MVI: State, Event, Effect
│   ├── ChatViewModel.kt       # Holds StateFlow<State>, processes Events
│   └── ChatScreen.kt          # Composable: renders State, dispatches Events
├── comparison/
│   ├── ComparisonContract.kt
│   ├── ComparisonViewModel.kt
│   └── ComparisonScreen.kt
├── reasoning/
│   ├── ReasoningContract.kt
│   ├── ReasoningViewModel.kt
│   └── ReasoningScreen.kt
└── temperature/
    ├── TemperatureContract.kt
    ├── TemperatureViewModel.kt
    └── TemperatureScreen.kt
```

### MVI Rules

1. **State** — Immutable `data class`. The only source of truth. Never mutate directly.
2. **Event** — `sealed interface` of all user actions. The only way to change state.
3. **Effect** — One-time side effects (navigation, scroll, toasts). Delivered via `Channel<Effect>`.
4. **ViewModel** — Holds `StateFlow<State>`. Processes Events via `onEvent()`. Emits Effects.
5. **Composable** — Observes state with `collectAsStateWithLifecycle()`. Calls `viewModel.onEvent(...)` in response to UI interactions. Never holds business logic.

### Clean Architecture Rules

- **Domain layer** (`shared/domain/`) — Zero platform dependencies. No Compose, no Ktor, no Android SDK.
- **Data layer** (`shared/data/`) — Implements domain interfaces. May depend on Ktor/API.
- **Presentation layer** (`composeApp/`) — Depends on domain (`shared`). ViewModels reference use cases and domain models only. Do **not** import from `shared/data/` directly in composables — only ViewModels may do so for wiring.
- **Use cases** return `Result<T>` via `runCatching`. ViewModels handle success/failure.

### KMP-specific Rules

- All ViewModel and domain code is in `commonMain` — identical behavior on Android and iOS.
- Use `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` for `ViewModel` and `viewModel<T>()`.
- Use `collectAsStateWithLifecycle()` from `lifecycle-runtime-compose` for state collection.
- `viewModelScope` is available in `commonMain` via the lifecycle library.
- Platform-specific code only in `androidMain` / `iosMain` using expect/actual.
- iOS builds use a static framework (`isStatic = true`), so all shared code is compiled in.

### Adding a New Screen

1. Create a package `composeApp/.../featurename/`
2. Add `FeatureContract.kt` — define `State`, `Event`, optionally `Effect`
3. Add `FeatureViewModel.kt` — extend `ViewModel`, expose `StateFlow<State>`, implement `onEvent()`
4. Add `FeatureScreen.kt` — `@Composable` fun, call `viewModel<FeatureViewModel>()`, collect state
5. Wire it into `App.kt`
6. If new domain operations are needed, add a use case in `shared/domain/usecase/`

## Key Tech Versions

Kotlin 2.3.10 · Compose Multiplatform 1.10.1 · Ktor 3.4.3 · AGP 9.0.1 · Gradle 9.3.1 · Android compileSdk/targetSdk 36 · minSdk 24 · Java 11 target compatibility.

Version catalog is in `gradle/libs.versions.toml`.

## Source Set Convention

Platform-specific code goes in the corresponding source set folder (`androidMain`, `iosMain`, `jvmMain`). Common/shared code goes in `commonMain`. Tests follow the same pattern (`commonTest`, etc.).

## Agent Efficiency Guidelines

- **Known file paths → use `Read` in parallel, never spawn a Task agent.** Task agents are only justified for open-ended search (unknown paths) or protecting context from very large outputs.
- **Read each file once, in full.** Avoid partial reads (`offset`/`limit`) unless the file is genuinely huge. Never read the same file twice.
- **Always read a file before editing it.** Skipping this causes a tool error and wastes a round-trip.
- **Batch all edits into one message.** When modifying multiple files, send all `Edit` calls in a single parallel message — not sequentially.
- **Parallelize independent tool calls.** `Read`, `Grep`, `Glob`, and `Bash` calls that don't depend on each other must be issued in the same message.
