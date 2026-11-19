# Releasing Verdetto

The release pipeline of engineer-foss-distribution.md (sections 4, 8 and 10, the operator's decisions of 2026-09-06):
one tag, one Linux build of the unsigned foss APK from the public tag that anyone can reproduce, a signature made
locally through the YubiKey, a GitLab release carrying the APK and its SHA-256, and F-Droid rebuilding from the tag
and shipping our signed APK once its build matches. No key ever reaches a build host or a repository. The play build
goes to Google Play through the console as before, signed by the operator's own app signing key (Play App Signing, the
decision of 2026-09-06), the same key that signs the F-Droid and GitLab builds, so the three sources interchange and
one fingerprint serves all. The project is hosted on GitLab (gitlab.com/verdettoqr); the releases from 1.0.1 on took
the route told here.

## 1. The tag

- The version comes from the tag alone (`buildSrc/TagVersion`): `vX.Y.Z` gives versionName `X.Y.Z` and versionCode
  `X*10000 + Y*100 + Z` to every flavor; minor and patch are two digits at most; a release build off a tag refuses.
- Before tagging: the security pass landed (engineer-security-pass.md), the hygiene gate empty
  (`python tools/hygiene/check.py`), the localization gates green, the safety bundle refreshed, the core pin the one
  the release was measured on, the lockfiles current (`./gradlew :app:dependencies :baselineprofile:dependencies dependencies --write-locks`
  and `./gradlew -p buildSrc dependencies --write-locks`, only after a deliberate bump), the flavor-diff test green on the release pair (`tools/release/flavor_diff.py`).
- `app/version.properties` carries the tag's two values as plain text for F-Droid's update check, which reads the file
  at the tag without running Gradle: set both to the release before tagging (a release build refuses when the file and
  the tag differ).
- `git tag -a vX.Y.Z -m "Verdetto X.Y.Z"` on the release commit; the tag goes to the GitLab repository with the
  release's push (`git push origin vX.Y.Z`).

## 2. The build, on Linux, from the public tag

No CI builds the release. The workflow files under `.github/workflows/` tell the same steps for a fork hosted on
GitHub; nothing runs them on GitLab. The unsigned foss APK is built by hand on Linux (Ubuntu 24.04 under WSL on the
release box) from a fresh recursive clone of the public history at the tag, never from a development checkout: the
build writes the core submodule's commit id into `BuildConfig.CORE_COMMIT`, so only a build of the commits F-Droid
sees can match F-Droid's rebuild. OpenJDK 21 (the major on F-Droid's build server), the pinned NDK from
`gradle.properties` (`verdetto.ndkVersion`), the pinned Rust toolchain from `toolchain/rust-toolchain.toml`, then one
clean build in the fresh clone, `./gradlew --no-daemon --no-build-cache :app:assembleFossRelease`. The foss release
variant has no signing configuration, so the output is
`app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk`, kept as `verdetto-X.Y.Z-foss-unsigned.apk` with its
SHA-256, the build log, the tool versions and the engine record (`app/build/rust/<abi>/record.txt`: the core commit,
the rustc, cargo and clang versions, the flags, the library's SHA-256). Two clean Linux builds of one tag were compared
and found identical on 1.0.1 (`tools/release/apk_compare.py`, section 7); a release on a new core pin or a new
toolchain repeats that comparison before it is signed. Nothing is published by this step.

What makes the build the same everywhere:

- the version from the tag; no build number, no date;
- the Android Gradle plugin's fixed zip timestamps (1981-01-01) and no VCS record (`vcsInfo.include = false` on the
  release build type: in a linked worktree it wrote `NO_VALID_GIT_FOUND`, in a clone it would write the revision);
- the engine from the pinned core commit with the pinned rustc and NDK (a mismatch refuses), `--locked` against
  `core/Cargo.lock`, `CARGO_INCREMENTAL=0`, and every box path mapped away (`--remap-path-prefix` for rustc,
  `-ffile-prefix-map` for the NDK clang: the checkout to `/verdetto`, cargo's home to `/cargo`, cargo's target
  directory to `/target`);
- the Gradle dependencies locked (`buildscript-gradle.lockfile` for the plugins, `gradle.lockfile` in `app`,
  `baselineprofile` and `buildSrc`);
- no signing on the variant, so no key and no signature block.

Known limits: two builds on the same operating system reproduce; a Windows build does not reproduce a Linux one,
because rustc embeds source paths with the host's separators (panic locations) and the NDK clang embeds `__FILE__`
of the C++ sources the same way (zxing-cpp's assertions and error macros), and a Windows checkout with
`core.autocrlf` on gives text assets CRLF endings (on 1.0.1, 18 of 1,610 entries differed between the Windows and the
Linux build: the engine and seventeen text assets). So the release build is the Linux one above, and F-Droid's build
server (Debian, JDK 21) has reproduced it: its build of the public tag v1.0.1 and a WSL build of the same tag gave the
same file. The checkout path must hold no spaces (the cc crate splits its flag variables on whitespace; the build
refuses such a path).

## 3. Reproducing the build

```text
git clone --recurse-submodules https://gitlab.com/verdettoqr/verdetto-android.git
cd verdetto-android && git checkout vX.Y.Z
sdkmanager --install "ndk;$(sed -n 's/^verdetto\.ndkVersion=//p' gradle.properties)"
rustup toolchain install <the channel of toolchain/rust-toolchain.toml> --profile minimal --target aarch64-linux-android --target x86_64-linux-android
./gradlew :app:assembleFossRelease
python tools/release/apk_compare.py app/build/outputs/apk/foss/release/app-foss-release-unsigned.apk verdetto-X.Y.Z-foss-unsigned.apk
```

`apk_compare.py` compares the two files byte for byte and, when they differ, names every differing entry with its
likely cause (a zip timestamp alone, the dex, a native library, a resource, the VCS record, a signature entry).
`--ignore-signing` sets the APK signing block and the v1 signature entries aside, which is how a signed release is
checked against an unsigned rebuild. Exit 0 is identity. cargo's target directory sits inside `app/build` unless
`local.properties` names `verdetto.cargoTargetDir`; a rustup toolchain that carries the pinned rustc under another
name is named with `verdetto.rustupToolchain`; two clean runs for a proof use `./gradlew clean` between them and
`--no-build-cache`, so nothing is reused, and BUILD.md's two-checkout comparison proves the path mapping (section 7).

## 4. Signing, locally, through the YubiKey

apksigner from build-tools 34.0.0 (the order's rule: F-Droid's verifier does not accept signatures made with 35.0.0
and later; re-check the F-Droid reproducible-builds page at release time). It is not installed on the box today
(36.0.0, 36.1.0 and 37.0.0 are): `sdkmanager --install "build-tools;34.0.0"`, then
`$ANDROID_HOME/build-tools/34.0.0/apksigner --version`.

The key lives in the YubiKey's PIV digital-signature slot (9c), imported from the key generated offline for Play App
Signing (section 10 of the order); apksigner reaches it through the JDK's PKCS#11 provider and Yubico's `libykcs11`
(the Yubico PIV Tool, 64-bit like the JDK). A `pkcs11.cfg`:

```text
name = YubiKey
library = C:\Program Files\Yubico\Yubico PIV Tool\bin\libykcs11.dll
slotListIndex = 0
```

(Linux: `library = /usr/lib/x86_64-linux-gnu/libykcs11.so`.) The alias apksigner needs is the key's label in that
store; list it once, with the PIN:

```text
keytool -list -keystore NONE -storetype PKCS11 -providerClass sun.security.pkcs11.SunPKCS11 -providerArg pkcs11.cfg
```

Then sign the unsigned APK of section 2 (the PIN is asked for; the key's touch policy asks for a touch), verify, and
check it against the unsigned build with the signing set aside:

```text
apksigner sign --ks NONE --ks-type PKCS11 --provider-class sun.security.pkcs11.SunPKCS11 --provider-arg pkcs11.cfg --ks-key-alias "<the alias listed above>" --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --out verdetto-X.Y.Z-foss.apk verdetto-X.Y.Z-foss-unsigned.apk
apksigner verify --verbose --print-certs verdetto-X.Y.Z-foss.apk
python tools/release/apk_compare.py --ignore-signing verdetto-X.Y.Z-foss.apk verdetto-X.Y.Z-foss-unsigned.apk
```

The route is exercised once on a test key before the first release (the decision of 2026-09-06). The certificate's
SHA-256 (`apksigner verify --print-certs`, "Signer #1 certificate SHA-256 digest") is the fingerprint the site
publishes once for all three sources and the `AllowedAPKSigningKeys` value below. v1 (JAR) signing stays off: minSdk
26 does not need it, and it would add entries to the zip. No v4 flag is passed (the ceremony's call, as Security read it
in the tool on 2026-09-20): the v4 signature is apksigner's default output, a file beside the APK
(`verdetto-X.Y.Z-foss.apk.idsig`), which rides the release as an asset; the APK's own bytes carry v2 and v3.

## 4b. The Play bundle and the two facts of the key ceremony (Security, 2026-09-12)

- The app signing key is generated offline in software and imported into the YubiKeys, never generated on a token:
  Play App Signing takes the key material once at enrolment (the PEPK tool at the first upload), so the key goes into
  slot 9c with `yubico-piv-tool -a import-key -s 9c` from the offline PEM (the self-signed certificate after it) on two
  YubiKeys, one encrypted offline copy kept. `-a generate` is for the upload key only, in another slot, since Play can
  reset an upload key. A key generated on the token cannot be exported; Play would then hold a Google-generated app
  signing key and Play installs would carry a different fingerprint from the F-Droid and GitLab builds, which breaks
  "one fingerprint serves all" and the cross-source updates.
- The Play upload is an app bundle, and apksigner does not sign bundles (Android's command-line build page: "You
  cannot use apksigner to sign your app bundle"). The bundle comes from `./gradlew :app:bundlePlayRelease`
  (`app/build/outputs/bundle/playRelease/app-play-release.aab`, unsigned: neither flavor's release carries a signing
  configuration since the security pass's A1) and is signed with jarsigner through the same SunPKCS11 provider, with
  the upload key:

```text
jarsigner -J-Djava.security.properties=<extra.security> -keystore NONE -storetype PKCS11 -sigalg SHA256withRSA -digestalg SHA-256 app-play-release.aab "<the upload key's alias>"
jarsigner -verify -verbose -certs app-play-release.aab
```

  The PIN is never an argument (Security, 2026-09-20): `-storepass <PIN>` on the command line is readable in the box's
  process list for as long as the call runs. jarsigner asks for it at its prompt; `-storepass:env <NAME>` is the other
  way in, the variable set for that call alone.

  One dry run on a scratch bundle with a scratch key before the release ceremony, as the APK route had on 09-11. The
  foss APK stays on apksigner as section 4 says.
- A walk on the phones never installs the bytes that ship: it installs a copy of the unsigned APK signed with the SDK's
  debug keystore by hand (`apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android
  --out walk.apk app-foss-release-unsigned.apk`), so the release stays unsigned until the ceremony.

## 5. The GitLab release

The signed APK and its records go up as a release on the tag, through GitLab's API with a project token (the token by
pipe, never in a command line, a chat or a log): each file is put into the project's generic package registry, then
the release is created on the tag with one asset link per file, each link's `direct_asset_path` the file's own name.

```text
sha256sum verdetto-X.Y.Z-foss.apk > SHA256SUMS-signed
PUT  https://gitlab.com/api/v4/projects/<id>/packages/generic/verdetto-android/X.Y.Z/<file>      (once per file)
POST https://gitlab.com/api/v4/projects/<id>/releases      (tag_name vX.Y.Z, name "Verdetto X.Y.Z", the notes, assets.links)
```

Four assets: `verdetto-X.Y.Z-foss.apk`, its `.idsig`, `SHA256SUMS-signed` and `signing-record.txt` (the tag and its
commit, the build's host, times and tool versions, the unsigned and the signed file's SHA-256, the certificate's
SHA-256, the verification's output). The APK's name is fixed, since F-Droid's `Binaries` pattern
points at the address the direct path gives it,
`https://gitlab.com/verdettoqr/verdetto-android/-/releases/vX.Y.Z/downloads/verdetto-X.Y.Z-foss.apk`. After the
release that address is read back: it answers, and the downloaded file's SHA-256 is the signed file's. The notes are
the tag's fastlane changelog (`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`), filled in by the
Security lane at the release, as 1.0.2's and 1.0.3's were; the launch comms plan in the site repository is the
Marketer's. Nothing else is attached: the play build goes to Google Play through the console.

Before the upload every text asset and the notes are read for the signing machine's own paths (Security,
2026-09-20): the record's composer names a tool without its folder, and a record that names a personal folder is
refused. Each file is put once, after that read: a name put twice leaves both files in the package registry, the older
still reachable by its id for a time even after it is deleted.

## 6. F-Droid (fdroiddata)

The merge request (fdroiddata 49438) adds `metadata/com.verdettoqr.scanner.yml`; the provisional filing precedes it
(section 2 of the order). The recipe's shape as F-Droid's pipeline built it green for 1.0.3, after the reviewer's
notes; the file in fdroiddata is the authority, this copy a reading aid:

```yaml
Categories:
  - Multimedia
  - Security
License: GPL-3.0-or-later
AuthorName: Verdetto
AuthorEmail: support@verdettoqr.com
AuthorWebSite: https://verdettoqr.com
WebSite: https://verdettoqr.com
SourceCode: https://gitlab.com/verdettoqr/verdetto-android
IssueTracker: https://gitlab.com/verdettoqr/verdetto-android/-/issues
Changelog: https://gitlab.com/verdettoqr/verdetto-android/-/releases

AutoName: Verdetto

RepoType: git
Repo: https://gitlab.com/verdettoqr/verdetto-android.git
Binaries: https://gitlab.com/verdettoqr/verdetto-android/-/releases/v%v/downloads/verdetto-%v-foss.apk

Builds:
  - versionName: X.Y.Z
    versionCode: N
    commit: <the 40-hex commit of the public tag vX.Y.Z>
    subdir: app
    submodules: true
    sudo:
      - apt-get update
      - apt-get install -y make gcc libc-dev rustup
    gradle:
      - foss
    prebuild:
      - rustup default <the channel of toolchain/rust-toolchain.toml>
      - rustup target add aarch64-linux-android
    scanignore:
      - app/src/main/assets/safety
    scandelete:
      - core/fixtures
      - tools/screens/fixtures/hot-fixture
    ndk: 29.0.14206865

AllowedAPKSigningKeys: <the signing certificate's SHA-256, lowercase hex>

MaintainerNotes: |-
  <why each scanignore and scandelete path is there; never a retelling of the fields>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
UpdateCheckData: app/version.properties|versionCode=(\d+)|.|versionName=(.+)
CurrentVersion: X.Y.Z
CurrentVersionCode: N
```

`commit` is the full 40-hex commit of the public tag, never the tag's name and never a branch. Until the merge request
merges, `Builds` holds one entry, the latest release: a newer tag replaces it, never joins it. After the merge
F-Droid's bot adds versions from our tags by itself (`AutoUpdateMode: Version`, `UpdateCheckMode: Tags`, and
`app/version.properties` read at the tag), so a hand change touches the recipe only when the pins or the build change,
as a new merge request. `sudo` names the minimal host packages one by one and takes rustup from Debian; `prebuild`
sets the pinned toolchain as rustup's default and adds the one Android target the release ships (arm64-v8a); the build
server's image carries the JDK (21 on 2026-09-20), so the recipe installs none. The Gradle task finds cargo by itself
(`$CARGO_HOME/bin`, then `~/.cargo/bin`, then PATH, where Debian's rustup puts it), so the recipe needs no PATH line.

`Binaries` with `AllowedAPKSigningKeys` is F-Droid's reproducible-build route: it builds from the tag, and when its
APK matches ours, it publishes ours, so F-Droid and GitLab installs update each other. `app/src/main/assets/safety` is
data the app parses (the bundled link-list snapshot, built from the public list repository by its own tooling), so the
scanner is told to ignore it; `core/fixtures` and `tools/screens/fixtures/hot-fixture` are test and rig data the
release build never reads, so they are deleted before the scan; `MaintainerNotes` says so for each path. The vendored
zxing-cpp sources are sources for F-Droid's scanner, not binaries; no `.so`, `.jar` or `.aar` is tracked. The recipe
carries no `Donate` field today; one added later takes https URLs only, no crypto fields (Counsel, section 9 of the
order). The safety-list download may draw an anti-feature label from the reviewers; it is answered honestly (section 9
of the order), never engineered around.

## 7. Record

- 2026-09-06, the first proof, on this box (Windows 11; the Studio JBR 25.0.3; rustc 1.98.0 (88d9e12ae, LLVM 22.1.8);
  NDK 29.0.14206865 (clang 21.0.0); AGP 9.4.0 on Gradle 9.6.0; core 5931614; the branch's commit 2489266 under a
  temporary local tag v0.0.1, deleted afterwards): two clean builds of `:app:assembleFossRelease`
  (`./gradlew clean :app:assembleFossRelease --no-build-cache`, each with its own fresh cargo target directory; 6 min
  14 s and 6 min 15 s with cargo at `-j 1`, of which the two engine builds are about two and a half minutes each),
  compared with `tools/release/apk_compare.py`: identical byte for byte, 14,644,789 bytes, SHA-256
  `e1fa37e133476775cbcd67eee853936b2468cb1999b2d5338f0ff769c43b720d`, 1,611 entries, no signing block. The two
  records differ only in the mapped name of the target directory in the flags, and the engine libraries hashed the
  same in every build of the day, fresh target directory or not (arm64-v8a `d906dad3…`, x86_64 `1f12c207…` as cargo
  wrote them), carrying no path of the box (the checkout, cargo's home and the target directory appear only under their
  mapped names; what remains are zxing-cpp's `__FILE__` strings and the registry crates' panic locations under
  `/verdetto` and `/cargo`). The play and foss debug APKs of the same tree carry the same engine entries (arm64-v8a
  `f4863a6c…`, x86_64 `d149daac…` after the plugin's strip), and the flavor-diff test on the release pair passes with
  the engine entries identical (tools/release/flavor_diff.py, the same day).

- 2026-09-10, the landing of the build-from-source route on main (build/core-from-source; core d77578d; BUILD.md,
  section 5, has the whole record): two checkouts under different paths gave the same libraries (arm64-v8a
  `622f4836…`, x86_64 `001bc8ac…`, no path of the box inside); the play and the foss release built under a temporary
  local tag carry the same `lib/arm64-v8a/libverdetto_android.so` (`aae29618…` after the plugin's strip) and pass the
  flavor-diff test; the foss debug build read a poster on the Pixel 7a through its built library.

- 2026-09-19, the Linux proof and the first GitLab release (1.0.1; WSL2 Ubuntu 24.04.3; OpenJDK 21.0.12; rustc 1.98.0;
  NDK 29.0.14206865): two clean builds of the tag in two checkouts, identical byte for byte, 14,379,614 bytes, 1,610
  entries, SHA-256 `e3c4fc0270dddb06adfa907aebdb5a1ff82c1ce8e16f6570349d0ff12466fa33`; against the Windows build of the
  same tag 18 entries differed (the engine, and seventeen text assets by their line endings). Those two came from a
  development checkout, and F-Droid's build of the public tag then differed from them in `classes.dex` and
  `baseline.prof` alone: `BuildConfig.CORE_COMMIT`, the core's commit id as each history names it. A WSL build of the
  public tag gave F-Droid's file (SHA-256 `e2f5c3d54f9f16f78272b2a7da83583e41563be0f73ba89727f87e8e0ee3c97f`), and
  that is the file the ceremony signed for the release. Since then every release APK is built from the public tag
  (section 2): 1.0.2 and 1.0.3 went that way.

## 8. What is not automatic

- No bot updates dependencies on GitLab: `.github/dependabot.yml` serves a fork hosted on GitHub, where it updates
  the version catalog and the workflow actions, never the lockfiles. A dependency bump is a deliberate change that
  lands with the lockfiles rewritten in the same change (section 1's commands), or the locked build refuses.
- No job scans the dependencies for advisories. Before a release chain the Security lane reads every coordinate
  the five Gradle lockfiles hold, and every registry crate of `core/Cargo.lock` at the core's pin, against the OSV
  database (api.osv.dev: names, ecosystems and versions alone are sent; both repositories read through git, nothing
  written), and says for each advisory which configurations lock the coordinate, since only a release runtime
  classpath ships. The read is dated in SECURITY-MODEL.md (S1, S3) and recorded in the security review; an advisory
  on what ships is reported to the Mechanic Leader before the chain starts.
- `toolchain/rust-toolchain.toml` is the app's pin, selected through `RUSTUP_TOOLCHAIN`; nothing is written into
  the submodule. When the core repository adopts a `rust-toolchain.toml` of its own, the two must name the same
  channel, or the build refuses.
- A new core pin is a commit of the submodule pointer alone: the next build compiles it (minutes per ABI on the
  constrained box, cached by commit afterwards), and a release on it repeats section 7's two-run comparison.
