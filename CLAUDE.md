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

## Architecture

Four modules with a shared-code-first approach:

- **shared** — Platform-agnostic Kotlin library consumed by all other modules. Uses the expect/actual pattern for platform abstractions (`Platform.kt` interface with per-platform implementations in `androidMain`, `iosMain`, `jvmMain`). Also holds global constants (`Constants.kt`).
- **composeApp** — Compose Multiplatform UI (targets Android + iOS). Source sets: `commonMain` for shared composables (`App.kt`), `androidMain` for `MainActivity`, `iosMain` for `MainViewController`. Compose resources live in `commonMain/composeResources/`.
- **server** — Ktor server (JVM only). Entry point is `Application.kt` with embedded Netty. Depends on `shared` for business logic.
- **iosApp** — Native SwiftUI wrapper that hosts the Compose UI via `MainViewController`.

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