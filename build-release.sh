#!/usr/bin/env bash
set -euo pipefail

# PrismQR release-only signed APK builder
# - Builds :app:assemble<Flavor>Release using system `gradle` (or `./gradlew` if present)
# - Signs with JKS keystore (default: my-release-key.jks, alias: my-key)
# - Outputs a single verified signed release APK in the repository root

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ "$ROOT_DIR" =~ [[:space:]] ]]; then
  echo "ERROR: Repository path must not contain spaces: $ROOT_DIR" >&2
  exit 1
fi

FLAVOR="${FLAVOR:-foss}"
case "$FLAVOR" in
  foss) FLAVOR_CAP="Foss" ;;
  play) FLAVOR_CAP="Play" ;;
  *)
    echo "ERROR: Unsupported FLAVOR='$FLAVOR' (expected 'foss' or 'play')." >&2
    exit 1
    ;;
esac

KEY_ALIAS="${KEY_ALIAS:-my-key}"
KEYSTORE_PASS="${KEYSTORE_PASS:-Sh@090609}"
KEY_PASS="${KEY_PASS:-Sh@090609}"
export KEYSTORE_PASS KEY_PASS

log() {
  printf '[build-release] %s\n' "$*"
}

die() {
  printf '[build-release] ERROR: %s\n' "$*" >&2
  exit 1
}

# 1. Locate & verify JKS keystore
JKS_FILE="${1:-${KEYSTORE_PATH:-}}"
if [[ -z "$JKS_FILE" ]]; then
  mapfile -t FOUND_JKS < <(
    find "$ROOT_DIR" -maxdepth 2 -type f \
      \( -iname '*.jks' -o -iname '*.keystore' -o -iname '*.p12' \) \
      ! -name 'debug.keystore' \
      ! -path '*/.git/*' \
      ! -path '*/.gradle/*' \
      ! -path '*/build/*' \
      | sort
  )
  if [[ ${#FOUND_JKS[@]} -eq 0 ]]; then
    die "No release keystore (.jks) found in $ROOT_DIR."
  elif [[ ${#FOUND_JKS[@]} -eq 1 ]]; then
    JKS_FILE="${FOUND_JKS[0]}"
  else
    for candidate in "${FOUND_JKS[@]}"; do
      if keytool -list -keystore "$candidate" -alias "$KEY_ALIAS" -storepass:env KEYSTORE_PASS >/dev/null 2>&1; then
        JKS_FILE="$candidate"
        break
      fi
    done
    if [[ -z "$JKS_FILE" ]]; then
      JKS_FILE="${FOUND_JKS[0]}"
    fi
  fi
fi

[[ -f "$JKS_FILE" ]] || die "Keystore file does not exist: $JKS_FILE"
log "Using keystore: $JKS_FILE (alias: $KEY_ALIAS)"

command -v keytool >/dev/null 2>&1 || die "keytool is required (JDK 17+)."
keytool -list -keystore "$JKS_FILE" -alias "$KEY_ALIAS" -storepass:env KEYSTORE_PASS >/dev/null 2>&1 \
  || die "Failed to verify alias '$KEY_ALIAS' in '$JKS_FILE' with the configured password."

# 2. Read version metadata from app/version.properties or app/build.gradle.kts
if [[ -f "$ROOT_DIR/app/version.properties" ]]; then
  VERSION_NAME="$(sed -n 's/^versionName=//p' "$ROOT_DIR/app/version.properties" | tr -d '[:space:]')"
  VERSION_CODE="$(sed -n 's/^versionCode=//p' "$ROOT_DIR/app/version.properties" | tr -d '[:space:]')"
else
  VERSION_NAME="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n1)"
  VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$ROOT_DIR/app/build.gradle.kts" | head -n1)"
fi
VERSION_NAME="${VERSION_NAME:-1.2.4}"
VERSION_CODE="${VERSION_CODE:-10204}"
log "Release metadata: versionName=$VERSION_NAME, versionCode=$VERSION_CODE"

# 3. Verify Android SDK & build-tools
if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    ANDROID_SDK_ROOT="$ANDROID_HOME"
  elif [[ -d "/opt/android/sdk" ]]; then
    ANDROID_SDK_ROOT="/opt/android/sdk"
  elif [[ -d "$HOME/Android/Sdk" ]]; then
    ANDROID_SDK_ROOT="$HOME/Android/Sdk"
  else
    die "ANDROID_SDK_ROOT is not set and no Android SDK found."
  fi
fi
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

# 4. Run release build using system gradle (preferred) or gradlew fallback
GRADLE_CMD=""
if command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
elif [[ -x "$ROOT_DIR/gradlew" ]]; then
  GRADLE_CMD="$ROOT_DIR/gradlew"
else
  die "Neither 'gradle' nor './gradlew' was found."
fi

GRADLE_TASK=":app:assemble${FLAVOR_CAP}Release"
log "Running release build: $GRADLE_CMD $GRADLE_TASK --no-daemon"
"$GRADLE_CMD" "$GRADLE_TASK" \
  --no-daemon \
  -x "lintVital${FLAVOR_CAP}Release" \
  -x "lintVitalAnalyze${FLAVOR_CAP}Release" \
  -x "lintVitalReport${FLAVOR_CAP}Release"

UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/${FLAVOR}/release/app-${FLAVOR}-release-unsigned.apk"
if [[ ! -f "$UNSIGNED_APK" ]]; then
  UNSIGNED_APK="$(find "$ROOT_DIR/app/build/outputs/apk/${FLAVOR}/release" -maxdepth 1 -name '*.apk' | head -n1 || true)"
fi
[[ -f "$UNSIGNED_APK" ]] || die "Release APK not produced under app/build/outputs/apk/${FLAVOR}/release/"

# 5. Locate apksigner and sign release APK
APKSIGNER=""
for bt_ver in 36.0.0 36.1.0 35.0.0 34.0.0 37.0.0; do
  if [[ -x "$ANDROID_SDK_ROOT/build-tools/$bt_ver/apksigner" ]]; then
    APKSIGNER="$ANDROID_SDK_ROOT/build-tools/$bt_ver/apksigner"
    break
  fi
done
if [[ -z "$APKSIGNER" ]]; then
  APKSIGNER="$(find "$ANDROID_SDK_ROOT/build-tools" -maxdepth 2 -name 'apksigner' -type f | sort -V | tail -n1 || true)"
fi
[[ -x "$APKSIGNER" ]] || die "apksigner not found under $ANDROID_SDK_ROOT/build-tools/"

OUT_APK="$ROOT_DIR/prismqr-${VERSION_NAME}-${FLAVOR}-release-signed.apk"
rm -f "$ROOT_DIR"/*-release-signed.apk "$ROOT_DIR"/app-*-release*.apk

log "Signing release APK using $APKSIGNER..."
"$APKSIGNER" sign \
  --ks "$JKS_FILE" \
  --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "env:KEYSTORE_PASS" \
  --key-pass "env:KEY_PASS" \
  --v1-signing-enabled false \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --out "$OUT_APK" \
  "$UNSIGNED_APK"

chmod 0644 "$OUT_APK"
chown --reference="$ROOT_DIR" "$OUT_APK" 2>/dev/null || true

log "Verifying signed APK..."
"$APKSIGNER" verify --verbose --print-certs "$OUT_APK"

APK_SIZE="$(wc -c < "$OUT_APK" | tr -d '[:space:]')"
APK_SHA256="$(sha256sum "$OUT_APK" | awk '{print $1}')"
log "SUCCESS: Signed release APK placed at root:"
log "  File:   $OUT_APK"
log "  Size:   $APK_SIZE bytes"
log "  SHA256: $APK_SHA256"
