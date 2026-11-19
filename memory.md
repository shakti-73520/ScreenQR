# Project Memory & Agent Instructions

## Mandatory Post-Task Workflow
- **ALWAYS run `build-release.sh` after completing any task** (`bash build-release.sh` via `run_command` in `.`).
- Never report a task complete until `build-release.sh` succeeds and produces a verified, signed release APK in the repository root.

## Build & Signing Environment
- **Gradle binary**: Always use `gradle` (never `./gradlew`, which is not supported in this cloud build environment).
- **Release keystore**: `/my-release-key.jks` (alias `my-key`, default credentials configured in `build-release.sh`).
- **Product flavor**: `foss` (`:app:assembleFossRelease`), `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- **Identity**: `app_name` = `PrismQR` (kept in sync between `app/src/main/res/values/strings.xml`, `settings.gradle.kts`, and `metadata.json`), `applicationId` = `com.verdettoqr.scanner.wywkmb`.
- **Architecture**: Pure Kotlin + Jetpack Compose (Obsidian Prism / Alabaster Prism design), Room + DataStore local storage, CameraX + ZXing multi-code decoding, and the Notification Center **Screen QR** `TileService` + `MediaProjection` overlay scanner (`ScreenQrTileService` -> `ScreenScanActivity` + `ScreenCaptureService`).
