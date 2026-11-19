# Verdetto: QR & Barcode Scanner (Android)

Verdetto is a free scanner for Android with no ads and no telemetry: it
reads the codes a phone meets (retail, logistics, tickets, documents),
explains what a code will do before anything opens, and runs its checks
on the phone; the online lookups (on by default) turn off with one switch
in Settings, and the built-in safety list still works with them off.

## Layout

| Path | What it is |
|---|---|
| `app/` | The Android app: Kotlin, Jetpack Compose, Material 3. `minSdk` 26. |
| `toolchain/` | The Rust toolchain pin (`rust-toolchain.toml`) the engine build copies into `core/`; the NDK pin is `verdetto.ndkVersion` in `gradle.properties`. |
| `RELEASE.md` | The release recipe: the tag, the CI build of the unsigned foss APK, the local signing, the public release, the F-Droid metadata, the reproducibility record. |
| `app/src/main/assets/safety/` | The signed safety bundle snapshot (link lists, brands, confusables, public suffixes, shorteners, aviation codes). Built by the `link-safety-list` repository; refreshed from its release assets before a store build. |
| `core/` | The scanning engine, a separate work in its own repository (verdettoqr/verdetto-core, Apache-2.0), mounted as a git submodule pinned to one commit: the Rust decode core, the JNI shim, the vendored zxing-cpp reader core, the fixtures and the port log. `git submodule update --init` after a clone. |
| `tools/screens/` | Emulator walks and the scanning validation matrix (`matrix_cases.py`, `matrix.py`, `matrix_report.py`, `matrix_compare.py`). |
| `tools/l10n/` | The localization gates (`inventory.py --check`, `check.py <lang>`, `leak_check.py`). |
| `tools/hygiene/` | The publish gates: `check.py` (no module name or format word of the page-format system), `log_check.py` (no scan text, payload, address, link or secret in a release build's log, and every number a log line formats in `Locale.ROOT`; its allow-list is `log_allow.txt`) and `apk_size.py` (an APK by content class, each class and the total held to a band file: `apk_size_band.json` for the arm64-v8a release APK, `apk_size_band_debug.json` for the play debug APK every landing builds; `apk_size_check.py` pins it on a synthetic zip). |
| `baselineprofile/` | The baseline profile module. |
| `SCAN-VALIDATION.md` | The validation matrix: what is validated, the procedure, the dated results. |
| `SECURITY.md` | How to report a security problem. |

## Build

The app: Android Studio or `./gradlew :app:assembleDebug :app:assembleRelease`
(JDK 17 or later; the Android SDK path comes from `local.properties`,
which is not carried). Two product flavors on a `store` dimension:
`play` (Google Play, with its billing client) and `foss` (F-Droid and the
GitLab releases; no Google library of any kind). The APKs land under
`app/build/outputs/apk/<flavor>/<debug|release>/app-<flavor>-<debug|release>.apk`
(the foss release, which carries no signing configuration, as
`app-foss-release-unsigned.apk`);
the walks and the matrix take the play flavor's paths by default, and
`tools/screens/everything_walk.py --flavor foss` walks the other build.
The version comes from the tag the checkout sits on (`vX.Y.Z`: versionName
X.Y.Z, versionCode X*10000+Y*100+Z, the same for every flavor); off a tag
the name ends in `-dev` and a release build refuses (`buildSrc`, with
`./gradlew :buildSrc:test` for the arithmetic). `./gradlew auditFossRuntimeClasspath`
fails on any Google artifact in the foss flavor, and
`python tools/release/flavor_diff.py <play apk> <foss apk>` holds the two
release APKs to the listed differences (the manifest's billing entries and
the store meta-data value, the dex).

The build resolves only the artifacts it resolved on the box (SECURITY-MODEL.md,
S1 and S2): the lock files (`gradle.lockfile` in `app`, `baselineprofile` and
`buildSrc`, `buildscript-gradle.lockfile` and `settings-gradle.lockfile` at the
root, strict mode),
`gradle/verification-metadata.xml` (a SHA-256 for every artifact and every
POM or module file, plugins included, verification strict) and the wrapper's
`distributionSha256Sum` are in the tree, so a version outside the lock state,
an artifact whose bytes differ from the recorded checksum, or a changed Gradle
distribution stops the build before it is used, on the box and in CI alike.
A version change refreshes them by hand, in the same commit as the change,
never in CI:

```bash
./gradlew --write-locks --write-verification-metadata sha256 --no-configuration-cache \
  help :dependencies :app:dependencies :baselineprofile:dependencies :buildSrc:dependencies \
  :buildSrc:test :app:assemblePlayDebug :app:testPlayDebugUnitTest :app:testFossDebugUnitTest \
  :app:auditFossRuntimeClasspath :app:lintAnalyzePlayDebug
```

Read the diff before committing: a lock line or a checksum changes only when
a version did. The `dependencies` reports lock every configuration, the
release ones included (a release build refuses off a tag, so it cannot write
its own). The metadata merges, so an entry stays until its version moves; a
leg that resolves something new (a device leg, a new Gradle task) is refreshed
with the same flags on that task. `aapt2` ships one jar per platform: the
Linux and macOS checksums come from Google's Maven `.sha256` sidecars beside
the Windows one the box resolves. A Gradle version change takes the new
distribution's SHA-256 from `services.gradle.org/distributions/<file>.sha256`
into `gradle-wrapper.properties`, and the wrapper jar is validated in CI
against Gradle's published list.

The engine is built from `core/` inside the Gradle build (the operator's
decision of 2026-09-06; engineer-foss-distribution.md, sections 8 and 10):
`cargoBuildArm64V8a` and `cargoBuildX86_64` run
`cargo build -p verdetto-android --release --locked --target <triple>`
over the pinned NDK (`verdetto.ndkVersion` in `gradle.properties`, installed
with `sdkmanager --install "ndk;<version>"`) and the pinned Rust toolchain
(`toolchain/rust-toolchain.toml`, selected through `RUSTUP_TOOLCHAIN`; a rustc
of another version, or one without the target, refuses), with the checkout,
cargo's home and cargo's target directory mapped to fixed names so no path of
the box enters the library. The libraries land under
`app/build/generated/verdettoJniLibs/<abi>/` with a record under
`app/build/rust/<abi>/record.txt` (the core commit, the rustc, cargo and
clang versions, the flags, the SHA-256), and every variant packages the ABIs
its build type carries (`engineAbis` in `app/build.gradle.kts`: the release
arm64-v8a alone, the debug build x86_64 too) as a generated jniLibs source, so
both flavors carry the same engine bytes. The build is keyed on the core
commit, the toolchain pin, the NDK pin and the ABI: an unchanged pin never
rebuilds, a modified `core/` source always does, and a missing cargo, target
or NDK stops the build with a plain line rather than reaching for a stale
library. Rust with rustup and the NDK must be installed; nothing else.
Per-checkout settings go in `local.properties` (or `-P`):
`verdetto.cargoTargetDir` (cargo's target directory, inside `app/build` by
default), `verdetto.cargoJobs`, `verdetto.cargo`, `verdetto.rustupToolchain`,
`verdetto.ndkDir`. The native tests stay in `core/`
(`cargo test --release --tests`; its own README has the details).
`BUILD.md` carries the inputs of a reproducible build, the two-build proof and
its record; `RELEASE.md` the release recipe;
`python tools/release/apk_compare.py <a.apk> <b.apk>` proves two builds
identical byte for byte and names every differing entry with its cause when
they are not.
## What is not here

The reference decoder this core is ported from lives in another
repository, together with a page-format system that is not part of this
release. Nothing from that system is in this tree: no page geometry, no
container, no generate path, no claim or evidence surface. A hygiene
check for the excluded module names and the format words runs before
every publish: `python tools/hygiene/check.py` must print zero hits
(its lists are in the script, the allowed ordinary uses in
`tools/hygiene/allow.txt`); it is the pre-push step. The engine repository
carries the same gate for its own tree.

A second gate in the same step, `python tools/hygiene/log_check.py`, holds
the release build's log to no scan text, payload, address, link or secret:
every `Log` call in `app/src/main` outside a `BuildConfig.DEBUG` guard is
listed in `tools/hygiene/log_allow.txt` by file and call with the reason it
carries none, a guarded line that does carry content (the outline and engine
frame lines the walks read) is pinned to its guard, and a stale entry fails.
The same gate holds every floating-point format that reaches a log line (the
same statement, or an expression-body function whose result is only logged)
to `Locale.ROOT` or `Locale.US`, so a phone set to a comma-decimal language
writes the 57.7 the walks' readers expect and never 57,7.
The JVM test `LogHygieneTest` runs it, and `ReleaseConfigurationTest` reads
the release configuration from the files (not debuggable, cleartext off, the
one extra trust anchor limited to its domain, minification with the JNI keep,
no test seam outside a guard).

## Performance diagnostics

About, Performance shows the app's own measurements, kept on the phone:
every frame's cost per screen against the display's deadline (missed,
long, stall bands), the slowest frames with the phases that made them,
main-thread stalls with the code the watchdog caught running, the camera's
delivery rate and the engine's wall time, and the device's memory, GC and
thermal state; the previous run's page is kept when the app stops. Copy or
share the page from its top bar; the switch on the page turns the
recording off. A debug build writes the same page to the app's files on
request, which is how the walks read it:

```bash
adb shell am start -n com.verdettoqr.scanner/app.scanner.MainActivity --ez fake_perf_dump true
adb shell run-as com.verdettoqr.scanner cat files/perf-dump.txt
```

`--ez fake_perf_reset true` empties it. The walk's phase R proves the page
and its buttons; the frame-stat steps (P01, P02, P04) record the in-app
totals beside gfxinfo's.

## Contributing

The APK stays small: a change that adds a dependency or an asset states
its stored size (the bytes it adds to the release APK) in its commit
message, and moves the band it grows in `tools/hygiene/apk_size_band.json`
and `apk_size_band_debug.json` in the same change; `python
tools/hygiene/apk_size.py <apk> --band <band file>` is the gate the landing
runs on the debug APK, and the release APK carries `arm64-v8a` only (the
debug build keeps `x86_64` for the emulator).

## Licences

Third-party code and data carried in the app are listed in the app's
About and Terms screens: zxing-cpp (Apache-2.0), the `jni` crate
(MIT or Apache-2.0), the bundled Roboto and Droid Sans Mono fonts
(Apache-2.0), the safety bundle sources named in the Terms, and the
Italian Medicines Agency table (CC BY 4.0). The scanning engine in the
`core/` submodule is a separate work under the Apache License, Version 2.0
(its `LICENSE` and `NOTICE`; listed in `THIRD-PARTY-NOTICES.md`), and the app
conveys it combined under the GPL with that notice preserved. The code of
this repository is free software under the GNU General Public
License, version 3 or later (`LICENSE`), with one additional permission
under GPL section 7 for conveying it combined with the Google Play Billing Library
and none for anything else. The name, the icon, the splash mark, the
wordmark, the screenshots and the store material are not licensed with
the code (`BRAND.md`). No APK or app bundle is ever committed here;
releases go to Google Play, F-Droid and the GitLab releases (`RELEASE.md`).

## Status

- Engine switch to the Rust core: done (2026-09-04), timed on the API 26
  low-end emulator.
- Every user path automated (`tools/screens/everything_walk.py`, 2026-09-04
  evening; `TEST-REPORT.md`): 185 merged steps, 178 pass, 7 informational,
  0 fail; eleven defects fixed on the way (the release-reachable debug
  extras, the thank-you note that never stayed up, the identity row copy,
  the editors' re-encode per keystroke, the Settings scroll); release cold
  start 288 ms and the Settings scroll 1.9 percent janky on the low-end AVD.
- Scanning validation matrix (`SCAN-VALIDATION.md`): 316 cases in four
  groups (every symbology at nominal, small, rotated 30, 45 or 60 and 90
  degrees, tilted or inverted, and under a perspective tilt; 30 content
  kinds; the modes, batch with several codes in view included). On the
  final build, on the API 26 low-end emulator: symbology 189 of 217 read,
  all correct, no wrong read on any case; content 81 of 86, 80 first reads correct and one garbled
  frame held off by the gate (the sheet showed the true payload);
  all thirteen mode cases; first-read time median 0.30 s (symbology) and 0.19 s
  (content). What misses: the PDF417 family oblique and inverted, MaxiCode,
  the two-row stacked DataBar under tilt, DX film edge past 45 degrees, and
  symbols under 1.1 px per module. Each is filed with a next step.
- Core, 2026-09-04: a region second pass (localized 1D regions uprighted by
  their bar angle and upscaled, 2D regions cropped and upscaled, read by the
  engine on a no-read frame; a read frame costs the engine alone, 7-9 ms);
  the app confirms a read seen in this frame and one of the two before,
  follows a different code with an open sheet, and refuses ITF reads under
  ten digits.
- Core since then (the pin `272e40a`): the own symbology plugins, the
  variant ladder (`ladder`), and the precision gate with the frame verifier
  (`precision`) of the reference reader are in the core; its crate
  documentation (`core/verdetto-core/src/lib.rs`) lists every module.
