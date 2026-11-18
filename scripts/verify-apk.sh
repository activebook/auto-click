#!/usr/bin/env bash
#
# Verifies invariants of a built APK. Used by CI and by hand.
#
#   ./scripts/verify-apk.sh <apk-path> <expected-versionName> <expected-versionCode> [--release]
#
# Fails (exit 1) when:
#   * the artifact is missing or empty
#   * versionName / versionCode do not match the source of truth
#   * minSdk / targetSdk drift away from 26 / 36
#   * the APK requests android.permission.INTERNET   <- the app's privacy promise
#   * the APK requests an android.permission.* outside the designed six
#   * --release is passed and the APK is still debuggable
#
# Permissions declared by the app itself (same package prefix, e.g. AndroidX's
# DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION) are allowed: they are signature-level,
# scoped to this app, and cannot reach anything outside it.
#
set -euo pipefail

APK="${1:?usage: verify-apk.sh <apk> <versionName> <versionCode> [--release]}"
EXPECTED_NAME="${2:?expected versionName}"
EXPECTED_CODE="${3:?expected versionCode}"
MODE="${4:-}"

PACKAGE_ID="com.activebook.autoclick.app"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
AAPT2="$(find "$SDK_ROOT/build-tools" -maxdepth 2 -name aapt2 -type f 2>/dev/null | sort | tail -1)"
if [ ! -x "$AAPT2" ]; then
  echo "FAIL: aapt2 not found under $SDK_ROOT/build-tools"; exit 1
fi

if [ ! -s "$APK" ]; then
  echo "FAIL: $APK missing or empty"; exit 1
fi

BADGING="$("$AAPT2" dump badging "$APK")"

# Anchored patterns on purpose: the package line also carries
# platformBuildVersionCodename, so a greedy match would return the wrong value.
field() { printf '%s\n' "$BADGING" | grep -m1 "$1" | sed -E "$2"; }

PACKAGE="$(field "^package: name=" "s/^package: name='([^']*)'.*/\1/")"
VERSION_CODE="$(field "^package: name=" "s/.*versionCode='([^']*)'.*/\1/")"
VERSION_NAME="$(field "^package: name=" "s/.*versionName='([^']*)'.*/\1/")"

# aapt2 emits `minSdkVersion:` on modern build-tools and `sdkVersion:` on older ones.
MIN_SDK="$(field "^minSdkVersion:" "s/^minSdkVersion:'([^']*)'.*/\1/" || true)"
if [ -z "$MIN_SDK" ]; then
  MIN_SDK="$(field "^sdkVersion:" "s/^sdkVersion:'([^']*)'.*/\1/" || true)"
fi
TARGET_SDK="$(field "^targetSdkVersion:" "s/^targetSdkVersion:'([^']*)'.*/\1/")"

FAIL=0
fail() { echo "FAIL: $*"; FAIL=1; }

[ "$PACKAGE" = "$PACKAGE_ID" ] || fail "package is '$PACKAGE', expected '$PACKAGE_ID'"
[ "$VERSION_NAME" = "$EXPECTED_NAME" ] || fail "versionName is '$VERSION_NAME', expected '$EXPECTED_NAME'"
[ "$VERSION_CODE" = "$EXPECTED_CODE" ] || fail "versionCode is '$VERSION_CODE', expected '$EXPECTED_CODE'"
[ "$MIN_SDK" = "26" ] || fail "minSdk is '$MIN_SDK', expected 26"
[ "$TARGET_SDK" = "36" ] || fail "targetSdk is '$TARGET_SDK', expected 36"

# ---- permissions ---------------------------------------------------------
DESIGNED_ANDROID_PERMISSIONS="android.permission.SYSTEM_ALERT_WINDOW
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_SPECIAL_USE
android.permission.POST_NOTIFICATIONS
android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
android.permission.VIBRATE"

REQUESTED="$(printf '%s\n' "$BADGING" \
  | grep -E "^uses-permission[^:]*: name=" \
  | sed -E "s/.*name='([^']*)'.*/\1/" \
  | sort -u || true)"

if printf '%s\n' "$REQUESTED" | grep -qx "android.permission.INTERNET"; then
  fail "the APK requests android.permission.INTERNET — this app must not have network access"
fi

# Anything in the android.* namespace that is not one of the designed six is a
# design change and must fail the build.
UNEXPECTED="$(printf '%s\n' "$REQUESTED" \
  | grep -E "^android\.permission\." \
  | comm -23 - <(printf '%s\n' "$DESIGNED_ANDROID_PERMISSIONS" | sort -u) || true)"
if [ -n "$UNEXPECTED" ]; then
  fail "unexpected permission(s): $(printf '%s' "$UNEXPECTED" | tr '\n' ' ')"
fi

MISSING="$(comm -13 <(printf '%s\n' "$REQUESTED" | sort -u) \
                    <(printf '%s\n' "$DESIGNED_ANDROID_PERMISSIONS" | sort -u) || true)"
if [ -n "$MISSING" ]; then
  echo "NOTE: designed permission(s) not in this artifact: $(printf '%s' "$MISSING" | tr '\n' ' ')"
fi

# ---- debug flag ----------------------------------------------------------
if [ "$MODE" = "--release" ] && printf '%s\n' "$BADGING" | grep -q "^application-debuggable"; then
  fail "release artifact is marked debuggable"
fi

SHA="$( (shasum -a 256 "$APK" 2>/dev/null || sha256sum "$APK") | awk '{print $1}')"

if [ "$FAIL" -ne 0 ]; then
  echo "APK: $APK"
  printf '%s\n' "$BADGING" \
    | grep -E "^package:|^minSdkVersion:|^sdkVersion:|^targetSdkVersion:|^uses-permission|^application-debuggable" \
    | sed 's/^/  /'
  exit 1
fi

echo "OK  $APK"
echo "    package=$PACKAGE versionName=$VERSION_NAME versionCode=$VERSION_CODE"
echo "    minSdk=$MIN_SDK targetSdk=$TARGET_SDK"
echo "    permissions:"
printf '%s\n' "$REQUESTED" | sed '/^$/d' | sed 's/^/      /'
echo "    sha256=$SHA"
