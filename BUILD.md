# Building Verdetto from source

The engine is compiled from the `core/` submodule inside the Gradle build, on every build, for every flavor (the
operator's decision of 2026-09-06, engineer-foss-distribution.md sections 8 and 10). No prebuilt library is in the
repository. This file names every input that decides the bytes of the engine library, and records the two-build
comparison that proves the same inputs give the same bytes on this box. RELEASE.md is the release recipe on top of it.

## 1. The inputs of a reproducible engine build

| Input | Where it is pinned | Value today |
|---|---|---|
| The engine sources | the `core/` submodule commit (`git ls-tree HEAD core`) | `d77578d` |
| The crate lock | `core/Cargo.lock` at that commit, honoured as is (`--locked`) | at the commit |
| The Rust toolchain | `toolchain/rust-toolchain.toml`, channel a version, never a moving name; selected by `RUSTUP_TOOLCHAIN` | `1.98.0` (rustc 1.98.0 88d9e12ae 2026-08-18) |
| The targets | the same file; one cargo run per ABI | `aarch64-linux-android` (arm64-v8a), `x86_64-linux-android` (x86_64) |
| The Android NDK | `verdetto.ndkVersion` in `gradle.properties`; the app's `android.ndkVersion` too, so the plugin's own strip runs the same NDK's `llvm-strip` | `29.0.14206865` (r29, clang 21.0.0) |
| The API level of the linker | `minSdk` in `app/build.gradle.kts`; the NDK wrapper is `<triple><api>-clang` | 26 |
| The cargo profile | `core/Cargo.toml` `[profile.release]` at the commit | `opt-level = 3`, `lto = "fat"`, `codegen-units = 1`, `panic = "abort"`, `debug = false`, `strip = true` |
| The cargo line | `buildSrc/src/main/java/verdetto/RustToolchain.java` | `cargo build -p verdetto-android --release --locked --target <triple>` |
| The flags | `RustToolchain.remappings`, `CargoBuild` | see section 2 |
| The ABIs per build type | `engineAbis` in `app/build.gradle.kts` | release: arm64-v8a; debug: arm64-v8a, x86_64; no armeabi-v7a |

The task that runs cargo is `CargoBuild` (buildSrc), one per ABI: `cargoBuildArm64V8a`, `cargoBuildX86_64`. It runs
before the native libraries are merged: `CollectJniLibs`, one per variant, gathers the libraries of the ABIs the
variant packs into the directory the Android plugin hands the variant as a generated jniLibs source
(`variant.sources.jniLibs.addGeneratedSourceDirectory`), so `merge<Variant>JniLibFolders` sees them like any source
set and the play and foss APKs carry the same engine bytes (`tools/release/flavor_diff.py` checks the two release
APKs; a debug pair works the same way).

## 2. What the task does

- Checks the NDK: `verdetto.ndkDir` (local.properties or `-P`), else `ANDROID_NDK_HOME`, else the SDK's
  `ndk/<verdetto.ndkVersion>`; its `source.properties` must carry the pinned revision. The linker is that NDK's
  `toolchains/llvm/prebuilt/<host>/bin/<triple><api>-clang` (`.cmd` on Windows); `llvm-ar` and `llvm-ranlib` beside it.
  No path of any user is in the tree; the core's own `.cargo/config.toml` names a personal path for the linkers, which
  the environment set here overrides (section 6 names the change the core repository owes).
- Selects the toolchain: `RUSTUP_TOOLCHAIN=<channel>` (or `verdetto.rustupToolchain`) with `RUSTUP_AUTO_INSTALL=0`;
  `rustc --version` must report the channel, and `<sysroot>/lib/rustlib/<triple>/lib` must exist, else the build stops
  with the `rustup toolchain install` or `rustup target add` line to run. cargo is `verdetto.cargo`, else
  `$CARGO_HOME/bin/cargo` (`~/.cargo/bin`), else the first on PATH; none is a plain failure.
- Sets, for the cargo run in `core/`:
  `CARGO_TARGET_DIR` (inside `app/build/rust/target` unless `verdetto.cargoTargetDir`), `CARGO_INCREMENTAL=0`,
  `RUSTC_WRAPPER=` and `RUSTC_WORKSPACE_WRAPPER=` empty, `CARGO_TARGET_<TRIPLE>_LINKER`, `CC_<triple>`, `CXX_<triple>`,
  `AR_<triple>`, `RANLIB_<triple>`, and
  `CARGO_ENCODED_RUSTFLAGS` = `--remap-path-prefix=<checkout>=/verdetto`, `--remap-path-prefix=<cargo home>=/cargo`,
  `--remap-path-prefix=<cargo target dir>=/target` (one flag per unit separator; the target directory last, since the last
  matching mapping wins), and `CFLAGS_<triple>` / `CXXFLAGS_<triple>` = the same three as `-ffile-prefix-map=` for the
  vendored zxing-cpp sources the `cc` crate compiles. `--locked` refuses a lock file that would change.
- Copies `<target>/<triple>/release/libverdetto_android.so` to `app/build/generated/verdettoJniLibs/<abi>/` and writes
  `app/build/rust/<abi>/record.txt`: the key (`<core commit>[-dirty]/rust-<channel>/ndk-<revision>/<abi>`), the
  library's size and SHA-256, the rustc, cargo and clang versions, the flags, cargo's output.
- Is up to date when the core commit, the core's build inputs (`Cargo.toml`, `Cargo.lock`, `.cargo`, the two crates,
  `third_party`, by `git status`), the toolchain file, the NDK pin, the API level and the ABI are unchanged: a second
  build runs no cargo. A modified core source always rebuilds.

## 3. Building

```text
git clone --recurse-submodules https://gitlab.com/verdettoqr/verdetto-android.git
sdkmanager --install "ndk;$(sed -n 's/^verdetto\.ndkVersion=//p' gradle.properties)"
rustup toolchain install 1.98.0 --profile minimal --target aarch64-linux-android --target x86_64-linux-android
./gradlew :app:assembleFossDebug        # or :app:assembleFossRelease on a tag vX.Y.Z (RELEASE.md)
```

Rust with rustup and the NDK are the only additions to an Android build. The checkout path must hold no spaces (the
`cc` crate's flag variables split on whitespace; the build refuses such a path). Per-checkout settings go in
`local.properties` or `-P`, never in the repository: `verdetto.cargoTargetDir`, `verdetto.cargoJobs` (cargo's `-j`;
unset leaves cargo its default, which never changes the bytes), `verdetto.cargo`, `verdetto.rustupToolchain`,
`verdetto.ndkDir`. On the shared box the chains that rebuilt the libraries by hand and copied them into the tree
(`/c/dev/sc/app_repin_*.sh`, the jniLibs record) retire with this: a new core pin is a submodule bump alone, and the
next build compiles it.

## 4. The two-build proof

The same inputs give the same library bytes. The proof is two clean builds from two checkouts under different paths,
each with a fresh cargo target directory, and the SHA-256 of the two libraries per ABI:

```text
./gradlew clean :app:assembleFossDebug --no-build-cache      # in checkout A, then in checkout B
sha256sum app/build/generated/verdettoJniLibs/*/libverdetto_android.so
grep -c "<the checkout path>" app/build/generated/verdettoJniLibs/*/libverdetto_android.so   # 0: no path of the box
```

The library in the APK (`lib/<abi>/libverdetto_android.so`) is the same file after the plugin's `llvm-strip` from the
pinned NDK, so it hashes the same across builds too; `python tools/release/apk_compare.py <a.apk> <b.apk>` compares
two whole APKs and names every differing entry with its cause.

## 5. Record

- 2026-09-10, on this box (Windows 11; the Studio JBR 25.0.3; rustc 1.98.0 (88d9e12ae, LLVM 22.1.8), cargo 1.98.0;
  NDK 29.0.14206865 (clang 21.0.0); AGP 9.4.0 on Gradle 9.6.0; core d77578d, the pin of main's PR 45; the branch
  build/core-from-source at a9603cd). Two clean builds from two checkouts under different paths gave the same
  libraries: checkout A (`C:/dev/ports/app-core-source`, `:app:assembleFossDebug`, 3 min 11 s for both ABIs with cargo
  at its default parallelism) and checkout B (`C:/dev/ports/app-core-source-b`, the two cargo tasks alone from an
  empty target directory, 6 min 10 s):

  | ABI | Bytes | SHA-256, checkout A | SHA-256, checkout B |
  |---|---|---|---|
  | arm64-v8a | 4,222,120 | `622f4836991c752e440061de9694303472bae12ee6c5c28221d00c57cb519673` | the same |
  | x86_64 | 5,002,192 | `001bc8acd77c2fa533e3b698dd4da5edaea08c7cfa7e845d8e386dadf811330e` | the same |

  Neither library holds the checkout path, cargo's home or the account (`grep -c` for `app-core-source` and `Users`:
  0 in each; the mapped names `/verdetto` and `/cargo` are inside). A rebuild with nothing changed took 3 s with
  `cargoBuildArm64V8a`, `cargoBuildX86_64` and `collectFossDebugEngineLibraries` up to date. At the previous pin
  3b243c2 the same build had given arm64-v8a `69c01c81…` and x86_64 `687f4fa6…` (5 min 52 s for the whole foss debug
  build from an empty target directory); the pin then moved by the submodule bump alone and the next build compiled
  it. In the APKs the entry `lib/arm64-v8a/libverdetto_android.so` is the same file in the play and the foss release
  built under a temporary local tag (SHA-256 `aae29618a99e529783e954b691bf49bf7a9310553286d36acdfa1ade1214e550`
  after the plugin's strip; the foss release unsigned, 13,517,235 bytes), and `tools/release/flavor_diff.py` on that
  pair passed (1,589 identical entries, the listed differences alone); the debug pair's `lib/` entries are identical
  too (the script stops on that pair over `res/raw/com_android_billingclient_*`, the billing client's raw resources
  the release shrinker removes: a debug-pair gap of the script's list, not an engine difference). A pin no SDK holds
  (`-Pverdetto.ndkVersion=28.9.99999999`) stops the engine task with `no NDK at <sdk>/ndk/28.9.99999999 (no
  source.properties): install ndk;28.9.99999999 with sdkmanager, or point verdetto.ndkDir or ANDROID_NDK_HOME at it`,
  while a unit-test task still configures: the unit suite never needs the NDK, and its task graph (63 tasks for both
  flavors) carries no engine or jni task. The JVM suite on both flavors: 1,295 tests each, 0 failures, 58 s. On the
  Pixel 7a the foss debug APK loaded `lib/arm64-v8a/libverdetto_android.so` from the package (logcat's nativeloader
  line, no UnsatisfiedLinkError) and read a poster QR through "Scan a photo instead"; the evidence is in the archive
  under its core-from-source record of 2026-09-10.
- 2026-09-11, on this box (the same toolchain: the Studio JBR 25.0.3; rustc 1.98.0 (88d9e12ae, LLVM 22.1.8), cargo
  1.98.0; NDK 29.0.14206865 (clang 21.0.0); AGP 9.4.0 on Gradle 9.6.0; core 7ad9136, the core's main after its PR 12
  (block-88, the PDF417 clipped-edge rescue from the stop side) and PR 11 (the NDK tools by name in `.cargo/config.toml`,
  the core's own `rust-toolchain.toml` at 1.98.0, which the task checked against `toolchain/rust-toolchain.toml`); the
  branch core/pin-88 at f8fde9f, the submodule bump alone). Two clean builds from two checkouts under different paths,
  `:app:assemblePlayDebug --no-build-cache --max-workers=2`, each from an empty target directory, gave the same
  libraries: checkout A (`C:/dev/ports/app-pin88`, 6 min 27 s for the whole play debug build with both ABIs) and
  checkout B (`C:/dev/ports/app-pin88-b`, 6 min 42 s):

  | ABI | Bytes | SHA-256, checkout A | SHA-256, checkout B |
  |---|---|---|---|
  | arm64-v8a | 4,239,848 | `a18c6d25121a872cd7acd0949211b3fb33a8495da15a03baf6351fee5b43ad47` | the same |
  | x86_64 | 5,025,232 | `b0dd2a508d38cb78692624e397b9b0c57723c6f402ea96ec6667cda0de73edd5` | the same |

  Neither library holds the checkout path or the account (`grep -c` for `app-pin88` and `Users`: 0 in each). The play
  debug APK's entries `lib/<abi>/libverdetto_android.so` keep the size after the plugin's strip (arm64-v8a SHA-256
  `4eefd1b9…`, x86_64 `7462d591…`, the same in checkout B's APK). The previous pin d77578d had given arm64-v8a `622f4836…` and x86_64
  `001bc8ac…` (the row above). The core's config change of PR 11 could not move the app's bytes: the task names every
  tool absolutely through the environment, which cargo reads before any config file, and the two-build proof above is
  the check.

## 6. What the core repository owes (a separate small pull request there)

Delivered: the core's PR 11 (c34e696, merged 2026-09-11), pinned here from 7ad9136, carries both items below; the list
stays as the record of what was asked.

- `core/.cargo/config.toml` names the NDK linkers and the `cc` compilers under one user's absolute path
  (`<the user's profile directory>/AppData/Local/Android/Sdk/ndk/29.0.14206865/...`). The app overrides every one of them through
  the environment, so the app's build is unaffected, but a plain `cargo build --target aarch64-linux-android` in the
  core alone works on that one box only. The change: the `[target.*].linker` and `[env]` entries expressed from an
  environment variable (`ANDROID_NDK_HOME`, which cargo's config can read as `{ value = "...", relative = false }` only
  for `[env]`, so the linkers go through a small wrapper or the documented `CARGO_TARGET_<TRIPLE>_LINKER` environment),
  or dropped in favour of the environment the app sets, with the README naming the variables.
- A `rust-toolchain.toml` of the core's own, equal to `toolchain/rust-toolchain.toml` here (channel 1.98.0, the two
  Android targets, profile minimal), so the core's own `cargo test` and CI use the pinned rustc; the app's task checks
  the two agree.
